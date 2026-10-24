package com.oldspell.app.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Walks a folder the user picked and turns it into books:
 *  - every .m4b file is its own book (chapters read from the file),
 *  - every folder holding other audio files (mp3, m4a, ogg, …) is one book, one chapter per file,
 *  - a folder whose only content is "CD 1", "Disc 2", "Part 3"… subfolders is one book.
 */
object Scanner {
    private const val TAG = "Scanner"
    private val AUDIO = setOf(
        "mp3", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "webm", "amr", "3gp",
    )
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp")
    private val COVER_NAMES = setOf("cover", "folder", "front", "albumart", "poster")
    private val DISC = Regex("""^(cd|disc|disk|part|teil|vol(ume)?)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE)

    private data class Doc(val id: String, val name: String, val mime: String) {
        val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
        val ext: String get() = name.substringAfterLast('.', "").lowercase()
        val base: String get() = name.substringBeforeLast('.')
    }

    private fun children(context: Context, tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = ArrayList<Doc>()
        context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Doc(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return out
    }

    suspend fun scan(
        context: Context,
        tree: Uri,
        existing: Map<String, Book>,
        progress: (String) -> Unit,
    ): List<Book> = withContext(Dispatchers.IO) {
        val out = ArrayList<Book>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Audiobooks" }
        walk(context, tree, rootId, rootName, existing, out, progress, 0)
        out
    }

    private fun walk(
        context: Context, tree: Uri, docId: String, name: String,
        existing: Map<String, Book>, out: MutableList<Book>, progress: (String) -> Unit, depth: Int,
    ) {
        if (depth > 10) return
        val kids = children(context, tree, docId)
        val dirs = kids.filter { it.isDir && !it.name.startsWith(".") }.sortedWith(compareBy<Doc, String>(NaturalOrder) { it.name })
        val audio = kids.filter { !it.isDir && it.ext in AUDIO && !it.name.startsWith(".") }
            .sortedWith(compareBy<Doc, String>(NaturalOrder) { it.name })
        val cover = pickCover(kids)

        // "Book/CD 1, Book/CD 2" layout → one book.
        if (audio.isEmpty() && dirs.size >= 2 && dirs.all { DISC.matches(it.name) }) {
            val parts = dirs.flatMap { d ->
                children(context, tree, d.id)
                    .filter { !it.isDir && it.ext in AUDIO && !it.name.startsWith(".") }
                    .sortedWith(compareBy<Doc, String>(NaturalOrder) { it.name })
            }
            if (parts.isNotEmpty()) {
                val discCover = cover ?: dirs.firstNotNullOfOrNull { pickCover(children(context, tree, it.id)) }
                buildBook(context, tree, docId, name, parts, discCover, existing, progress)?.let { out += it }
                return
            }
        }

        val (singles, grouped) = audio.partition { it.ext == "m4b" }
        for (s in singles) {
            buildBook(context, tree, s.id, s.base, listOf(s), cover, existing, progress)?.let { out += it }
        }
        if (grouped.isNotEmpty()) {
            buildBook(context, tree, docId, name, grouped, cover, existing, progress)?.let { out += it }
        }
        for (d in dirs) walk(context, tree, d.id, d.name, existing, out, progress, depth + 1)
    }

    private fun pickCover(kids: List<Doc>): Doc? {
        val images = kids.filter { !it.isDir && it.ext in IMAGE }
        return images.firstOrNull { it.base.lowercase() in COVER_NAMES } ?: images.firstOrNull()
    }

    private fun buildBook(
        context: Context, tree: Uri, sourceDocId: String, fallbackTitle: String, files: List<Doc>,
        coverDoc: Doc?, existing: Map<String, Book>, progress: (String) -> Unit,
    ): Book? {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(tree, sourceDocId).toString()
        val id = sha1(sourceUri).take(20)
        val uris = files.map { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
        val prev = existing[id]
        if (prev != null && prev.tracks.map { it.uri } == uris.map { it.toString() }) return prev

        progress("Reading “$fallbackTitle”")
        var album: String? = null
        var bookTitle: String? = null
        var author: String? = null
        var narrator: String? = null
        var picture: ByteArray? = null

        val tracks = files.mapIndexed { i, doc ->
            var title: String? = null
            var duration = 0L
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uris[i])
                duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (i == 0) {
                    album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    bookTitle = title
                    author = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?.takeIf { it.isNotBlank() }
                        ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    narrator = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    picture = mmr.embeddedPicture
                }
            } catch (e: Exception) {
                Log.w(TAG, "Metadata failed for ${doc.name}", e)
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
            if (duration <= 0) duration = extractorDuration(context, uris[i])
            Track(uris[i].toString(), title?.trim()?.takeIf { it.isNotEmpty() } ?: doc.base, duration)
        }
        if (tracks.isEmpty()) return null

        val title = if (files.size == 1) {
            album?.takeIf { it.isNotBlank() } ?: bookTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
        } else {
            album?.takeIf { it.isNotBlank() } ?: fallbackTitle
        }.trim()

        val total = tracks.sumOf { it.durationMs }
        val chapters: List<Chapter> = run {
            if (files.size == 1) {
                val embedded = if (files[0].ext in setOf("m4b", "m4a", "mp4", "aac")) {
                    Mp4Chapters.read(context, uris[0], total)
                } else emptyList()
                if (embedded.size > 1) return@run embedded
                return@run listOf(Chapter(title, 0, total))
            }
            // One chapter per file. If every file carries the same title tag, file names are more useful.
            val tagTitlesUseful = tracks.map { it.name }.distinct().size == tracks.size
            var start = 0L
            tracks.mapIndexed { i, t ->
                val name = if (tagTitlesUseful) t.name else files[i].base
                Chapter(name, start, t.durationMs).also { start += t.durationMs }
            }
        }

        val coverPath = saveCover(context, id, picture, coverDoc?.let {
            DocumentsContract.buildDocumentUriUsingTree(tree, it.id)
        })

        return Book(
            id = id,
            title = title,
            author = author?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown author",
            narrator = narrator?.trim().orEmpty(),
            sourceUri = sourceUri,
            folderUri = tree.toString(),
            coverPath = coverPath,
            tracks = tracks,
            chapters = chapters,
            addedAt = prev?.addedAt ?: System.currentTimeMillis(),
            positionMs = prev?.positionMs?.coerceAtMost(total) ?: 0L,
            lastPlayedAt = prev?.lastPlayedAt ?: 0L,
            finished = prev?.finished ?: false,
            bookmarks = prev?.bookmarks ?: emptyList(),
        )
    }

    private fun saveCover(context: Context, id: String, embedded: ByteArray?, imageUri: Uri?): String? {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "$id.img")
        return try {
            when {
                embedded != null && embedded.isNotEmpty() -> out.writeBytes(embedded)
                imageUri != null -> context.contentResolver.openInputStream(imageUri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return null
                else -> return null
            }
            out.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Cover save failed", e)
            null
        }
    }

    private fun extractorDuration(context: Context, uri: Uri): Long {
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
