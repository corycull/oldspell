package com.oldspell.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared sleep-timer state. The playback service counts it down (only while audio is playing). */
object SleepTimer {
    sealed interface Mode {
        data object Off : Mode
        data class Countdown(val remainingMs: Long, val totalMs: Long) : Mode
        data class EndOfChapter(val bookId: String, val targetMs: Long) : Mode
    }

    private val _mode = MutableStateFlow<Mode>(Mode.Off)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun setMinutes(minutes: Int) {
        val ms = minutes * 60_000L
        _mode.value = Mode.Countdown(ms, ms)
    }

    fun setEndOfChapter(bookId: String, chapterEndMs: Long) {
        _mode.value = Mode.EndOfChapter(bookId, chapterEndMs)
    }

    fun cancel() {
        _mode.value = Mode.Off
    }

    /** Shake-to-extend: restart the countdown from its original length. */
    fun restart() {
        val m = _mode.value
        if (m is Mode.Countdown) _mode.value = m.copy(remainingMs = m.totalMs)
    }

    internal fun tick(remainingMs: Long) {
        val m = _mode.value
        if (m is Mode.Countdown) _mode.value = m.copy(remainingMs = remainingMs)
    }
}
