package com.earmark.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Track(val uri: String, val name: String, val durationMs: Long)

data class Chapter(val title: String, val startMs: Long, val durationMs: Long) {
    val endMs: Long get() = startMs + durationMs
}

data class Bookmark(val id: Long, val positionMs: Long, val note: String, val createdAt: Long)

/**
 * A book is one or more audio files played back-to-back. All positions exposed to the UI
 * are "global" positions measured from the start of the first file.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String,
    val sourceUri: String,
    val folderUri: String,
    val coverPath: String?,
    val tracks: List<Track>,
    val chapters: List<Chapter>,
    val addedAt: Long,
    val positionMs: Long = 0,
    val lastPlayedAt: Long = 0,
    val finished: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
) {
    val durationMs: Long = tracks.sumOf { it.durationMs }

    private val offsets: LongArray = LongArray(tracks.size).also { arr ->
        var acc = 0L
        for (i in tracks.indices) {
            arr[i] = acc
            acc += tracks[i].durationMs
        }
    }

    fun offsetOf(trackIndex: Int): Long =
        if (offsets.isEmpty()) 0L else offsets[trackIndex.coerceIn(0, offsets.size - 1)]

    /** Global position -> (track index, position inside that track). */
    fun locate(globalMs: Long): Pair<Int, Long> {
        if (offsets.isEmpty()) return 0 to 0L
        var i = offsets.size - 1
        while (i > 0 && offsets[i] > globalMs) i--
        return i to (globalMs - offsets[i]).coerceAtLeast(0L)
    }

    fun chapterIndexAt(globalMs: Long): Int {
        if (chapters.isEmpty()) return -1
        var i = chapters.size - 1
        while (i > 0 && chapters[i].startMs > globalMs) i--
        return i
    }

    val progress: Float
        get() = when {
            finished -> 1f
            durationMs <= 0 -> 0f
            else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }

    val inProgress: Boolean get() = !finished && positionMs > 0
    val notStarted: Boolean get() = !finished && positionMs <= 0
}

fun Book.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("author", author)
    put("narrator", narrator)
    put("sourceUri", sourceUri)
    put("folderUri", folderUri)
    if (coverPath != null) put("coverPath", coverPath)
    put("addedAt", addedAt)
    put("positionMs", positionMs)
    put("lastPlayedAt", lastPlayedAt)
    put("finished", finished)
    put("tracks", JSONArray().apply {
        tracks.forEach {
            put(JSONObject().put("uri", it.uri).put("name", it.name).put("durationMs", it.durationMs))
        }
    })
    put("chapters", JSONArray().apply {
        chapters.forEach {
            put(JSONObject().put("title", it.title).put("startMs", it.startMs).put("durationMs", it.durationMs))
        }
    })
    put("bookmarks", JSONArray().apply {
        bookmarks.forEach {
            put(
                JSONObject().put("id", it.id).put("positionMs", it.positionMs)
                    .put("note", it.note).put("createdAt", it.createdAt)
            )
        }
    })
}

private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) out += f(getJSONObject(i))
    return out
}

fun bookFromJson(o: JSONObject): Book = Book(
    id = o.getString("id"),
    title = o.getString("title"),
    author = o.optString("author"),
    narrator = o.optString("narrator"),
    sourceUri = o.getString("sourceUri"),
    folderUri = o.optString("folderUri"),
    coverPath = if (o.has("coverPath")) o.getString("coverPath") else null,
    addedAt = o.optLong("addedAt"),
    positionMs = o.optLong("positionMs"),
    lastPlayedAt = o.optLong("lastPlayedAt"),
    finished = o.optBoolean("finished"),
    tracks = o.optJSONArray("tracks").mapObjects {
        Track(it.getString("uri"), it.optString("name"), it.optLong("durationMs"))
    },
    chapters = o.optJSONArray("chapters").mapObjects {
        Chapter(it.optString("title"), it.optLong("startMs"), it.optLong("durationMs"))
    },
    bookmarks = o.optJSONArray("bookmarks").mapObjects {
        Bookmark(it.optLong("id"), it.optLong("positionMs"), it.optString("note"), it.optLong("createdAt"))
    },
)
