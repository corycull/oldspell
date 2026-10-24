package com.oldspell.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/** In-memory library backed by a JSON file in app storage. Shared by the UI and the playback service. */
class LibraryStore(private val context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private var scanJob: Job? = null

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    /** Non-null while a scan is running; holds a short status line. */
    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    fun get(id: String): Book? = _books.value.firstOrNull { it.id == id }

    private fun load(): List<Book> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            List(arr.length()) { bookFromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read library", e)
        emptyList()
    }

    private fun persist() {
        scope.launch {
            writeLock.withLock {
                try {
                    val json = JSONArray().apply { _books.value.forEach { put(it.toJson()) } }.toString()
                    val tmp = File(file.parentFile, "library.json.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(file)) {
                        file.writeText(json)
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save library", e)
                }
            }
        }
    }

    fun update(id: String, transform: (Book) -> Book) {
        _books.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persist()
    }

    fun saveProgress(id: String, positionMs: Long) = update(id) { b ->
        val pos = positionMs.coerceIn(0L, b.durationMs.coerceAtLeast(0L))
        val done = b.durationMs > 0 && pos >= b.durationMs - 2_000
        b.copy(positionMs = pos, lastPlayedAt = System.currentTimeMillis(), finished = done)
    }

    fun setFinished(id: String, finished: Boolean) = update(id) { b ->
        if (finished) b.copy(finished = true, positionMs = b.durationMs)
        else b.copy(finished = false, positionMs = 0)
    }

    fun resetProgress(id: String) = update(id) { it.copy(positionMs = 0, finished = false) }

    fun addBookmark(id: String, positionMs: Long, note: String) = update(id) { b ->
        val bm = Bookmark(System.currentTimeMillis(), positionMs, note, System.currentTimeMillis())
        b.copy(bookmarks = (b.bookmarks + bm).sortedBy { it.positionMs })
    }

    fun updateBookmarkNote(id: String, bookmarkId: Long, note: String) = update(id) { b ->
        b.copy(bookmarks = b.bookmarks.map { if (it.id == bookmarkId) it.copy(note = note) else it })
    }

    fun removeBookmark(id: String, bookmarkId: Long) = update(id) { b ->
        b.copy(bookmarks = b.bookmarks.filterNot { it.id == bookmarkId })
    }

    fun remove(id: String) {
        get(id)?.coverPath?.let { File(it).delete() }
        _books.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    /** Top-level folders on the phone that hold audio (empty until audio access is granted). */
    private val _folders = MutableStateFlow<List<AudioFolder>>(emptyList())
    val folders: StateFlow<List<AudioFolder>> = _folders.asStateFlow()
    private var scanGeneration = 0

    /** Re-reads the phone's audio library. A new request cancels one already running. */
    fun scan(settings: Settings) {
        scanJob?.cancel()
        val gen = ++scanGeneration
        scanJob = scope.launch {
            if (!Scanner.hasPermission(context)) {
                _folders.value = emptyList()
                return@launch
            }
            _scanStatus.value = "Looking for audiobooks…"
            try {
                val files = Scanner.query(context)
                val folders = Scanner.folders(files)
                _folders.value = folders
                val included = settings.includedFolders.value
                    ?: folders.filter { it.looksLikeBooks }.map { it.key }.toSet()
                        .also { if (folders.isNotEmpty()) settings.setIncluded(it) }

                val existing = _books.value.associateBy { it.id }
                val hidden = settings.hidden.value
                val result = Scanner.buildBooks(context, files, included, existing) { _scanStatus.value = it }
                    .filter { it.id !in hidden }
                    .distinctBy { it.id }

                // Merge with live state so progress saved during the scan isn't lost. Books found under a
                // new id (e.g. after the switch away from the folder picker) keep the progress of the old
                // entry with the same title and length.
                _books.update { current ->
                    val live = current.associateBy { it.id }
                    val unmatched = current.filter { c -> result.none { it.id == c.id } }.toMutableList()
                    result.map { b ->
                        val c = live[b.id] ?: unmatched.firstOrNull { o ->
                            o.title.equals(b.title, ignoreCase = true) && kotlin.math.abs(o.durationMs - b.durationMs) < 3_000
                        }?.also { unmatched.remove(it) }
                        if (c == null) b else b.copy(
                            positionMs = c.positionMs.coerceAtMost(b.durationMs), lastPlayedAt = c.lastPlayedAt,
                            finished = c.finished, bookmarks = c.bookmarks, addedAt = c.addedAt,
                            coverPath = b.coverPath ?: c.coverPath,
                        )
                    }
                }
                persist()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed", e)
            } finally {
                if (gen == scanGeneration) _scanStatus.value = null
            }
        }
    }

    companion object {
        private const val TAG = "LibraryStore"
    }
}
