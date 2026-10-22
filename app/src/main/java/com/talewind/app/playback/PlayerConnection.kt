package com.talewind.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.talewind.app.TalewindApp
import com.talewind.app.data.Book
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val speed: Float = 1f,
)

/** UI-side handle to the playback service. All positions are global book positions. */
class PlayerConnection(private val context: Context) {
    private val app get() = TalewindApp.instance
    private val scope = MainScope()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f) return@addListener
            val c = try { f.get() } catch (e: Exception) { null } ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending?.invoke(c)
            pending = null
            ticker = scope.launch {
                while (isActive) {
                    refresh()
                    delay(250)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending = block
            connect()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val bookId = c.currentBookId()
        val book = bookId?.let { app.store.get(it) }
        _state.value = PlaybackState(
            connected = true,
            bookId = if (book != null) bookId else null,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = if (book != null) c.globalPosition(book) else 0L,
            speed = c.playbackParameters.speed,
        )
    }

    private fun currentBook(c: Player): Book? = c.currentBookId()?.let { app.store.get(it) }

    /** Start (or resume) a book. If it's already loaded this just plays. */
    fun play(book: Book, startAtMs: Long? = null) = withController { c ->
        if (c.currentBookId() == book.id && c.mediaItemCount == book.tracks.size) {
            if (startAtMs != null) c.seekGlobal(book, startAtMs)
            if (c.playbackState == Player.STATE_ENDED) c.seekGlobal(book, 0)
            c.play()
            return@withController
        }
        // Save where we were in the previous book before switching.
        currentBook(c)?.let { app.store.saveProgress(it.id, c.globalPosition(it)) }

        var start = startAtMs ?: book.positionMs
        if (startAtMs == null && (book.finished || start >= book.durationMs - 1_000)) start = 0
        val (index, offset) = book.locate(start)
        val items = book.tracks.indices.map { buildMediaItem(book, it) }
        c.setMediaItems(items, index, offset)
        c.setPlaybackSpeed(app.settings.speed.value)
        c.prepare()
        c.play()
        app.store.update(book.id) { it.copy(lastPlayedAt = System.currentTimeMillis()) }
    }

    fun togglePlay(book: Book) {
        val c = controller
        if (c != null && c.currentBookId() == book.id && c.isPlaying) c.pause() else play(book)
    }

    fun pause() = controller?.pause()

    fun seekTo(book: Book, globalMs: Long) {
        val c = controller
        if (c != null && c.currentBookId() == book.id) {
            c.seekGlobal(book, globalMs)
            refresh()
        } else {
            app.store.saveProgress(book.id, globalMs)
        }
    }

    fun skip(book: Book, deltaMs: Long) {
        val pos = positionOf(book)
        seekTo(book, (pos + deltaMs).coerceIn(0L, book.durationMs))
    }

    fun positionOf(book: Book): Long {
        val s = _state.value
        return if (s.bookId == book.id) s.positionMs else book.positionMs
    }

    fun nextChapter(book: Book) {
        val i = book.chapterIndexAt(positionOf(book))
        if (i + 1 < book.chapters.size) seekTo(book, book.chapters[i + 1].startMs)
    }

    /** Like a CD player: go to the start of this chapter, or the previous one if we're near its start. */
    fun previousChapter(book: Book) {
        val pos = positionOf(book)
        val i = book.chapterIndexAt(pos)
        if (i < 0) return
        val ch = book.chapters[i]
        val target = if (pos - ch.startMs > 3_000 || i == 0) ch.startMs else book.chapters[i - 1].startMs
        seekTo(book, target)
    }

    fun setSpeed(speed: Float) {
        app.settings.setSpeed(speed)
        controller?.setPlaybackSpeed(speed)
    }
}
