package com.oldspell.app.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log

/** Reads chapter markers embedded in .m4b / .m4a files. */
object Mp4Chapters {
    fun read(context: Context, uri: Uri, totalDurationMs: Long): List<Chapter> = try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) emptyList() else {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { ch ->
                Mp4ChapterParser.parse(ch).toChapters(totalDurationMs)
            }
        }
    } catch (e: Exception) {
        Log.w("Mp4Chapters", "Chapter read failed", e)
        emptyList()
    }

    private fun List<Pair<Long, String>>.toChapters(totalMs: Long): List<Chapter> {
        val marks = filter { it.first in 0 until totalMs.coerceAtLeast(1) }
            .sortedBy { it.first }
            .distinctBy { it.first }
        if (marks.isEmpty()) return emptyList()
        // Anything before the first marker belongs to the first chapter.
        val starts = marks.mapIndexed { i, m -> if (i == 0) 0L else m.first }
        return marks.mapIndexed { i, (_, title) ->
            val start = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else totalMs
            Chapter(title.trim().ifEmpty { "Chapter ${i + 1}" }, start, (end - start).coerceAtLeast(0))
        }.filter { it.durationMs > 0 }
    }
}
