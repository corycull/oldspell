package com.oldspell.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The Download folder, which Android's folder picker refuses to hand out. Read through the media
 * library (audio permission) instead:
 *  - every .m4b is its own book,
 *  - every subfolder of audio files is one book (one chapter per file); "CD 1 / CD 2" subfolders merge,
 *  - loose files directly in Download are grouped by album tag; untagged loose files are one book each.
 */
object DownloadSource {
    /** Stored in the folder list in place of a picker URI. */
    const val KEY = "oldspell:download"
    private const val TAG = "DownloadSource"
    private val DISC = Regex("""^(cd|disc|disk|part|teil|vol(ume)?)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE)
    private val CHAPTER_CONTAINERS = setOf("m4b", "m4a", "mp4", "aac")

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    private class AudioFile(
        val uri: Uri,
        val name: String,
        /** Folder path below Download, "" for files directly in Download. */
        val sub: String,
        val durationMs: Long,
        val title: String?,
        val album: String?,
        val artist: String?,
    ) {
        val ext: String get() = name.substringAfterLast('.', "").lowercase()
        val base: String get() = name.substringBeforeLast('.')
    }

    private class Group(val key: String, val title: String, val files: List<AudioFile>)

    suspend fun scan(context: Context, existing: Map<String, Book>, progress: (String) -> Unit): List<Book> =
        withContext(Dispatchers.IO) {
            groups(query(context)).mapNotNull { g ->
                ensureActive()
                buildBook(context, g, existing, progress)
            }
        }

    private fun query(context: Context): List<AudioFile> {
        val modern = Build.VERSION.SDK_INT >= 29
        val collection = if (modern) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        @Suppress("DEPRECATION")
        val pathCol = if (modern) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            pathCol,
        )
        @Suppress("DEPRECATION")
        val downloadRoot = File(
            Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS,
        ).absolutePath
        val prefix = if (modern) "${Environment.DIRECTORY_DOWNLOADS}/" else "$downloadRoot/"
        var selection = "$pathCol LIKE ? AND ${MediaStore.Audio.Media.IS_RINGTONE}=0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM}=0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION}=0"
        if (Build.VERSION.SDK_INT >= 31) selection += " AND ${MediaStore.Audio.Media.IS_RECORDING}=0"

        val out = ArrayList<AudioFile>()
        context.contentResolver.query(collection, cols, selection, arrayOf("$prefix%"), null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val path = c.getString(6) ?: continue
                val sub = if (modern) {
                    path.removePrefix(prefix).trim('/')
                } else {
                    (File(path).parent ?: downloadRoot).removePrefix(downloadRoot).trim('/')
                }
                if (sub.split('/').any { it.startsWith(".") }) continue
                out += AudioFile(
                    uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                    name = name,
                    sub = sub,
                    durationMs = c.getLong(2).coerceAtLeast(0),
                    title = c.getString(3)?.takeUnless { it.isBlank() || it == "<unknown>" },
                    album = c.getString(4)?.takeUnless { it.isBlank() || it == "<unknown>" },
                    artist = c.getString(5)?.takeUnless { it.isBlank() || it == "<unknown>" },
                )
            }
        }
        return out
    }

    private fun parentOf(sub: String) = if ('/' in sub) sub.substringBeforeLast('/') else ""
    private fun nameOf(sub: String) = sub.substringAfterLast('/')

    private fun groups(files: List<AudioFile>): List<Group> {
        val bySub = files.groupBy { it.sub }
        // A folder whose audio lives only in "CD 1 / CD 2 …" subfolders becomes one book.
        val mergedInto = HashMap<String, String>()
        bySub.keys.filter { it.isNotEmpty() && DISC.matches(nameOf(it)) }.groupBy { parentOf(it) }
            .forEach { (parent, kids) ->
                if (parent.isNotEmpty() && parent !in bySub && kids.size >= 2) kids.forEach { mergedInto[it] = parent }
            }
        val books = LinkedHashMap<String, MutableList<AudioFile>>()
        for ((sub, list) in bySub) books.getOrPut(mergedInto[sub] ?: sub) { ArrayList() } += list

        val out = ArrayList<Group>()
        for ((sub, list) in books) {
            val sorted = list.sortedWith(
                compareBy<AudioFile, String>(Scanner.NaturalOrder) { it.sub }.thenBy(Scanner.NaturalOrder) { it.name },
            )
            val (singles, rest) = sorted.partition { it.ext == "m4b" }
            singles.forEach { out += Group("file:${it.uri}", it.base, listOf(it)) }
            if (rest.isEmpty()) continue
            if (sub.isNotEmpty()) {
                out += Group("dir:$sub", nameOf(sub), rest)
            } else {
                // Loose files in Download: one book per album tag, else one per file.
                val (tagged, untagged) = rest.partition { it.album != null }
                tagged.groupBy { it.album!! }.forEach { (album, fs) -> out += Group("album:$album", album, fs) }
                untagged.forEach { out += Group("file:${it.uri}", it.base, listOf(it)) }
            }
        }
        return out
    }

    private fun buildBook(context: Context, g: Group, existing: Map<String, Book>, progress: (String) -> Unit): Book? {
        if (g.files.isEmpty()) return null
        val id = sha1("download:${g.key}").take(20)
        val uris = g.files.map { it.uri.toString() }
        val prev = existing[id]
        if (prev != null && prev.tracks.map { it.uri } == uris) return prev

        progress("Reading “${g.title}”")
        val first = g.files.first()
        var author: String? = null
        var narrator: String? = null
        var picture: ByteArray? = null
        var firstTitle: String? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, first.uri)
            author = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf { it.isNotBlank() }
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            narrator = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            firstTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            picture = mmr.embeddedPicture
        } catch (e: Exception) {
            Log.w(TAG, "Metadata failed for ${first.name}", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        val tracks = g.files.map { f ->
            Track(f.uri.toString(), f.title ?: f.base, if (f.durationMs > 0) f.durationMs else probeDuration(context, f.uri))
        }
        val total = tracks.sumOf { it.durationMs }
        val title = (first.album ?: if (g.files.size == 1) firstTitle ?: first.title else null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: g.title

        val chapters: List<Chapter> = run {
            if (g.files.size == 1) {
                val embedded = if (first.ext in CHAPTER_CONTAINERS) Mp4Chapters.read(context, first.uri, total) else emptyList()
                return@run if (embedded.size > 1) embedded else listOf(Chapter(title, 0, total))
            }
            val tagTitlesUseful = tracks.map { it.name }.distinct().size == tracks.size
            var start = 0L
            tracks.mapIndexed { i, t ->
                Chapter(if (tagTitlesUseful) t.name else g.files[i].base, start, t.durationMs).also { start += t.durationMs }
            }
        }

        return Book(
            id = id,
            title = title,
            author = author?.trim()?.takeIf { it.isNotEmpty() } ?: first.artist ?: "Unknown author",
            narrator = narrator?.trim().orEmpty(),
            sourceUri = g.key,
            folderUri = KEY,
            coverPath = saveCover(context, id, picture) ?: prev?.coverPath,
            tracks = tracks,
            chapters = chapters,
            addedAt = prev?.addedAt ?: System.currentTimeMillis(),
            positionMs = prev?.positionMs?.coerceAtMost(total) ?: 0L,
            lastPlayedAt = prev?.lastPlayedAt ?: 0L,
            finished = prev?.finished ?: false,
            bookmarks = prev?.bookmarks ?: emptyList(),
        )
    }

    private fun saveCover(context: Context, id: String, embedded: ByteArray?): String? {
        if (embedded == null || embedded.isEmpty()) return null
        val out = File(File(context.filesDir, "covers").apply { mkdirs() }, "$id.img")
        return try {
            out.writeBytes(embedded)
            out.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Cover save failed", e)
            null
        }
    }

    private fun probeDuration(context: Context, uri: Uri): Long {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { if (it > 0) return it }
        } catch (_: Exception) {
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            var best = 0L
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.containsKey(MediaFormat.KEY_DURATION)) best = maxOf(best, f.getLong(MediaFormat.KEY_DURATION) / 1000)
            }
            best
        } catch (e: Exception) {
            0L
        } finally {
            ex.release()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
