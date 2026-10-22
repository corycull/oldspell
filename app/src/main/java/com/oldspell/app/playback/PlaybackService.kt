package com.oldspell.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.oldspell.app.OldspellApp
import com.oldspell.app.MainActivity
import com.oldspell.app.data.Book
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Owns the player. Keeps playing with the screen off, shows the media notification / lock-screen
 * controls, saves progress every few seconds and runs the sleep timer.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = OldspellApp.instance
    private var lastSaveAt = 0L
    private var lastTickAt = 0L
    private var shake: ShakeDetector? = null

    override fun onCreate() {
        super.onCreate()
        // Constant-bitrate seeking keeps long mp3 files seekable even without a seek index.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveNow()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentBook()?.let { app.store.saveProgress(it.id, it.durationMs) }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) saveNow()
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, BookPlayer(exo))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        scope.launch {
            while (isActive) {
                delay(250)
                tick()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveNow()
        scope.cancel()
        shake?.stop()
        shake = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun currentBook(): Book? = exo.currentBookId()?.let { app.store.get(it) }

    private fun saveNow() {
        val book = currentBook() ?: return
        if (exo.playbackState == Player.STATE_IDLE) return
        lastSaveAt = SystemClock.elapsedRealtime()
        app.store.saveProgress(book.id, exo.globalPosition(book))
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTickAt == 0L) 0L else now - lastTickAt
        lastTickAt = now

        if (exo.isPlaying) {
            if (now - lastSaveAt > 5_000) saveNow()
            when (val m = SleepTimer.mode.value) {
                is SleepTimer.Mode.Countdown -> {
                    val remaining = m.remainingMs - dt
                    if (remaining <= 0) {
                        exo.pause()
                        exo.volume = 1f
                        SleepTimer.cancel()
                    } else {
                        SleepTimer.tick(remaining)
                        // Fade out over the last 10 seconds.
                        exo.volume = if (remaining < 10_000) remaining / 10_000f else 1f
                    }
                }
                is SleepTimer.Mode.EndOfChapter -> {
                    val book = currentBook()
                    if (book == null || book.id != m.bookId) {
                        SleepTimer.cancel()
                    } else if (exo.globalPosition(book) >= m.targetMs - 200) {
                        exo.pause()
                        SleepTimer.cancel()
                    }
                }
                SleepTimer.Mode.Off -> if (exo.volume != 1f) exo.volume = 1f
            }
        }

        val wantShake = SleepTimer.mode.value is SleepTimer.Mode.Countdown && exo.isPlaying
        if (wantShake && shake == null) {
            shake = ShakeDetector(this) { SleepTimer.restart() }.also { it.start() }
        } else if (!wantShake && shake != null) {
            shake?.stop()
            shake = null
        }
    }

    private fun skipBy(deltaMs: Long) {
        val book = currentBook()
        if (book == null || book.durationMs <= 0) {
            exo.seekTo((exo.currentPosition + deltaMs).coerceAtLeast(0L))
        } else {
            exo.seekGlobal(book, exo.globalPosition(book) + deltaMs)
        }
    }

    private fun augment(commands: Player.Commands): Player.Commands =
        if (exo.mediaItemCount == 0) commands else commands.buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK,
            )
            .build()

    /**
     * Audiobook semantics for outside controls: the notification, lock screen, Bluetooth and
     * headset "previous / next" buttons skip back / forward by the configured interval instead of
     * jumping between files.
     */
    private inner class BookPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        private val wrappers = HashMap<Player.Listener, Player.Listener>()

        override fun seekToNext() = skipBy(app.settings.skipForwardSec.value * 1000L)
        override fun seekToPrevious() = skipBy(-app.settings.skipBackSec.value * 1000L)
        override fun seekForward() = seekToNext()
        override fun seekBack() = seekToPrevious()

        override fun getAvailableCommands(): Player.Commands = augment(super.getAvailableCommands())
        override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

        // Listeners must also see the augmented command set, or the notification hides "next" on
        // the last file of a book. Java default methods aren't forwarded by Kotlin delegation,
        // so a dynamic proxy forwards every callback and patches the one that carries Commands.
        override fun addListener(listener: Player.Listener) {
            val handler = InvocationHandler { proxy, method, args ->
                if (method.declaringClass == Any::class.java) {
                    return@InvocationHandler when (method.name) {
                        "equals" -> proxy === args?.getOrNull(0)
                        "hashCode" -> System.identityHashCode(proxy)
                        else -> "BookPlayerListener"
                    }
                }
                val params = method.parameterTypes
                val callArgs: Array<Any?> = args?.copyOf() ?: arrayOf()
                if (params.size == 1 && params[0] == Player.Commands::class.java && callArgs[0] is Player.Commands) {
                    callArgs[0] = augment(callArgs[0] as Player.Commands)
                }
                try {
                    method.invoke(listener, *callArgs)
                } catch (e: InvocationTargetException) {
                    throw e.targetException ?: e
                }
            }
            val wrapped = Proxy.newProxyInstance(
                Player.Listener::class.java.classLoader,
                arrayOf(Player.Listener::class.java),
                handler,
            ) as Player.Listener
            wrappers[listener] = wrapped
            super.addListener(wrapped)
        }

        override fun removeListener(listener: Player.Listener) {
            wrappers.remove(listener)?.let { super.removeListener(it) }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Rebuild items from our library so playable URIs never depend on what the controller sent. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val bookId = item.mediaId.substringBefore('#')
                val index = item.mediaId.substringAfter('#', "").toIntOrNull()
                val book = app.store.get(bookId)
                if (book != null && index != null && index in book.tracks.indices) buildMediaItem(book, index) else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
