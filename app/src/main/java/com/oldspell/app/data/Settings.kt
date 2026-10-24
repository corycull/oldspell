package com.oldspell.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // Folders picked with the old system picker no longer apply.
        prefs.edit().remove("folders").apply()
    }

    /** Top-level folders whose audio counts as audiobooks. null = not chosen yet (use defaults). */
    private val _included = MutableStateFlow(
        if (prefs.getBoolean("includedChosen", false)) readSet("includedFolders") else null,
    )
    val includedFolders: StateFlow<Set<String>?> = _included.asStateFlow()

    private val _hidden = MutableStateFlow(readSet("hidden"))
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _skipBackSec = MutableStateFlow(prefs.getInt("skipBack", 30))
    val skipBackSec: StateFlow<Int> = _skipBackSec.asStateFlow()

    private val _skipForwardSec = MutableStateFlow(prefs.getInt("skipForward", 30))
    val skipForwardSec: StateFlow<Int> = _skipForwardSec.asStateFlow()

    private val _speed = MutableStateFlow(prefs.getFloat("speed", 1f))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private fun readSet(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    fun setIncluded(keys: Set<String>) {
        _included.value = keys
        prefs.edit().putStringSet("includedFolders", keys).putBoolean("includedChosen", true).apply()
    }

    fun toggleFolder(key: String) {
        val cur = _included.value ?: emptySet()
        setIncluded(if (key in cur) cur - key else cur + key)
    }

    fun hide(bookId: String) {
        val next = _hidden.value + bookId
        _hidden.value = next
        prefs.edit().putStringSet("hidden", next).apply()
    }

    fun unhideAll() {
        _hidden.value = emptySet()
        prefs.edit().remove("hidden").apply()
    }

    fun setSkipBack(sec: Int) {
        _skipBackSec.value = sec
        prefs.edit().putInt("skipBack", sec).apply()
    }

    fun setSkipForward(sec: Int) {
        _skipForwardSec.value = sec
        prefs.edit().putInt("skipForward", sec).apply()
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        prefs.edit().putFloat("speed", speed).apply()
    }
}
