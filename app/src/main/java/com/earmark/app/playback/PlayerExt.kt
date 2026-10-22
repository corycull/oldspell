package com.earmark.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.earmark.app.data.Book
import java.io.File

/** Media item IDs look like "<bookId>#<trackIndex>". */
fun Player.currentBookId(): String? =
    currentMediaItem?.mediaId?.substringBefore('#')?.takeIf { it.isNotEmpty() }

fun Player.globalPosition(book: Book): Long =
    book.offsetOf(currentMediaItemIndex) + currentPosition.coerceAtLeast(0L)

fun Player.seekGlobal(book: Book, globalMs: Long) {
    val target = globalMs.coerceIn(0L, (book.durationMs - 250).coerceAtLeast(0L))
    val (index, offset) = book.locate(target)
    seekTo(index, offset)
}

fun buildMediaItem(book: Book, index: Int): MediaItem {
    val track = book.tracks[index]
    val meta = MediaMetadata.Builder()
        .setTitle(book.title)
        .setArtist(book.author)
        .setAlbumTitle(book.title)
        .setAlbumArtist(book.author)
        .setArtworkUri(book.coverPath?.let { Uri.fromFile(File(it)) })
        .build()
    return MediaItem.Builder()
        .setMediaId("${book.id}#$index")
        .setUri(track.uri)
        .setMediaMetadata(meta)
        .build()
}
