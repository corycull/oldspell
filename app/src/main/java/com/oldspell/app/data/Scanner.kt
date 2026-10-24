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

/** A top-level folder on the phone that holds audio, e.g. "Internal storage / Download". */
data class AudioFolder(
    val key: String,
    val label: String,
    val bookCount: Int,
    /** Pre-ticked the first time: named like an audiobook folder, holds .m4b files, or is Download. */
    val looksLikeBooks: Boolean,
)

/**
 * Finds audiobooks through Android's media library (MediaStore), so the user never has to pick a
 * folder in the system picker, which refuses Download, the storage root and others.
 *
 *  - every .m4b file is its own book (chapters read from the file),
 *  - every folder holding other audio files (mp3, m4a, ogg, …) is one book, one chapter per file,
 *  - a folder whose only content is "CD 1", "Disc 2", "Part 3"… subfolders is one book.
 *
 * The user chooses which top-level folders count (so music elsewhere stays out).
 */
object Scanner {
    private const val TAG = "Scanner"
    private val DISC = Regex("""^(cd|disc|disk|part|teil|vol(ume)?)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE)
    private val BOOKISH = Regex(
        """audio\s*-?books?|h[öo]rb[üu]ch(er)?|livres?\s*audio|audiolibr|^books?$|^podcasts?$""",
        RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_CONTAINERS = setOf("m4b", "m4a", "mp4", "aac")

    class AudioFile(
        val uri: Uri,
        val name: String,
        /** "<volume>:<relative/dir>" with no trailing slash; "" relative part = top of the volume. */
        val dir: String,
        val durationMs: Long,
        val title: String?,
        val album: String?,
        val artist: String?,
    ) {
        val ext: String get() = name.substringAfterLast('.', "").lowercase()
        val base: String get() = name.substringBeforeLast('.')
    }

    private class Group(val key: String, val title: String, val topKey: String, val files: List<AudioFile>)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ MediaStore

    fun query(context: Context): List<AudioFile> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val modern = Build.VERSION.SDK_INT >= 29
        val cols = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ARTIST)
            if (modern) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()
        // Leave out ringtones, alarms, notification sounds and voice recordings.
        var selection = "${MediaStore.Audio.Media.IS_RINGTONE}=0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM}=0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION}=0"
        if (Build.VERSION.SDK_INT >= 31) selection += " AND ${MediaStore.Audio.Media.IS_RECORDING}=0"

        val out = ArrayList<AudioFile>()
        context.contentResolver.query(collection, cols, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val dir = if (modern) {
                    val rel = (c.getString(6) ?: "").trim('/')
                    val vol = c.getString(7) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    "$vol:$rel"
                } else {
                    legacyDir(c.getString(6) ?: continue)
                }
                out += AudioFile(
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    dir = dir,
                    durationMs = c.getLong(2).coerceAtLeast(0),
                    title = c.getString(3)?.takeUnless { it.isBlank() || it == "<unknown>" },
                    album = c.getString(4)?.takeUnless { it.isBlank() || it == "<unknown>" },
                    artist = c.getString(5)?.takeUnless { it.isBlank() || it == "<unknown>" },
                )
            }
        }
        return out
    }

    /** Android 8–9: turn "/storage/emulated/0/Download/x.mp3" into "external_primary:Download". */
    private fun legacyDir(path: String): String {
        val parent = File(path).parent ?: return "external_primary:"
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory().absolutePath
        if (parent == primary || parent.startsWith("$primary/")) {
            return "external_primary:" + parent.removePrefix(primary).trim('/')
        }
        // "/storage/1234-ABCD/Audiobooks" → "1234-abcd:Audiobooks"
        val rest = parent.removePrefix("/storage/").trim('/')
        val vol = rest.substringBefore('/')
        return vol.lowercase() + ":" + rest.substringAfter('/', "")
    }

    private fun volumeOf(dir: String) = dir.substringBefore(':')
    private fun relOf(dir: String) = dir.substringAfter(':')
    private fun parentOf(dir: String): String? {
        val rel = relOf(dir)
        if (rel.isEmpty()) return null
        return volumeOf(dir) + ":" + (if ('/' in rel) rel.substringBeforeLast('/') else "")
    }
    private fun nameOf(dir: String) = relOf(dir).substringAfterLast('/')
    private fun topKeyOf(dir: String) = volumeOf(dir) + ":" + relOf(dir).substringBefore('/')

    fun folderLabel(key: String): String {
        val vol = volumeOf(key)
        val top = relOf(key)
        val volLabel = if (vol == "external_primary" || vol == "external") "Internal storage" else "SD card"
        return if (top.isEmpty()) "$volLabel (top level)" else "$volLabel / $top"
    }

    // ------------------------------------------------------------------ Grouping

    private fun groups(files: List<AudioFile>): List<Group> {
        val byDir = files.groupBy { it.dir }
        // Parents whose audio lives only in "CD 1 / CD 2 …" subfolders become a single book.
        val discChildren = byDir.keys
            .filter { DISC.matches(nameOf(it)) }
            .groupBy { parentOf(it) }
        val mergedInto = HashMap<String, String>()
        for ((parent, kids) in discChildren) {
            if (parent != null && parent !in byDir && kids.size >= 2) kids.forEach { mergedInto[it] = parent }
        }
        val bookDirs = LinkedHashMap<String, MutableList<AudioFile>>()
        for ((dir, list) in byDir) bookDirs.getOrPut(mergedInto[dir] ?: dir) { ArrayList() } += list

        val out = ArrayList<Group>()
        for ((dir, list) in bookDirs) {
            val sorted = list.sortedWith(compareBy<AudioFile, String>(NaturalOrder) { it.dir }.thenBy(NaturalOrder) { it.name })
            val (singles, grouped) = sorted.partition { it.ext == "m4b" }
            for (s in singles) out += Group("file:${s.uri}", s.base, topKeyOf(dir), listOf(s))
            if (grouped.isNotEmpty()) {
                val title = nameOf(dir).ifEmpty { folderLabel(dir) }
                out += Group("dir:$dir", title, topKeyOf(dir), grouped)
            }
        }
        return out
    }

    fun folders(files: List<AudioFile>): List<AudioFolder> {
        val gs = groups(files)
        return gs.groupBy { it.topKey }.map { (key, list) ->
            val top = relOf(key)
            AudioFolder(
                key = key,
                label = folderLabel(key),
                bookCount = list.size,
                looksLikeBooks = BOOKISH.containsMatchIn(top) ||
                    top.equals("Download", true) || top.equals("Downloads", true) ||
                    list.any { g -> g.files.any { it.ext == "m4b" } },
            )
        }.sortedWith(compareBy<AudioFolder> { !it.looksLikeBooks }.thenBy(NaturalOrder) { it.label })
    }

    suspend fun buildBooks(
        context: Context,
        files: List<AudioFile>,
        included: Set<String>,
        existing: Map<String, Book>,
        progress: (String) -> Unit,
    ): List<Book> = withContext(Dispatchers.IO) {
        groups(files).filter { it.topKey in included }.mapNotNull { g ->
            ensureActive()
            buildBook(context, g, existing, progress)
        }
    }

    private fun buildBook(context: Context, g: Group, existing: Map<String, Book>, progress: (String) -> Unit): Book? {
        val id = sha1(g.key).take(20)
        val uris = g.files.map { it.uri.toString() }
        val prev = existing[id]
        if (prev != null && prev.tracks.map { it.uri } == uris && prev.folderUri == g.topKey) return prev

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
            val d = if (f.durationMs > 0) f.durationMs else probeDuration(context, f.uri)
            Track(f.uri.toString(), f.title ?: f.base, d)
        }
        val total = tracks.sumOf { it.durationMs }
        if (tracks.isEmpty()) return null

        val title = (first.album ?: if (g.files.size == 1) firstTitle ?: first.title else null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: g.title

        val chapters: List<Chapter> = run {
            if (g.files.size == 1) {
                val embedded = if (first.ext in CHAPTER_CONTAINERS) Mp4Chapters.read(context, first.uri, total) else emptyList()
                return@run if (embedded.size > 1) embedded else listOf(Chapter(title, 0, total))
            }
            // One chapter per file. If every file carries the same title tag, file names are more useful.
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
            folderUri = g.topKey,
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
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "$id.img")
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

    /** "Chapter 2" sorts before "Chapter 10". */
    val NaturalOrder: Comparator<String> = Comparator { a, b ->
        val re = Regex("""\d+|\D+""")
        val pa = re.findAll(a.lowercase()).map { it.value }.toList()
        val pb = re.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]
            val y = pb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                val xt = x.trimStart('0')
                val yt = y.trimStart('0')
                if (xt.length != yt.length) xt.length - yt.length else xt.compareTo(yt)
            } else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        pa.size - pb.size
    }
}
