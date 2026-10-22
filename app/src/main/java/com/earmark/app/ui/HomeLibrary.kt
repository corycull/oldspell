package com.earmark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earmark.app.data.Book
import com.earmark.app.playback.PlaybackState
import java.util.Calendar

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(
    books: List<Book>,
    state: PlaybackState,
    scanStatus: String?,
    onPlay: (Book) -> Unit,
    onToggle: (Book) -> Unit,
    onAddFolder: () -> Unit,
) {
    fun pos(b: Book) = if (state.bookId == b.id) state.positionMs else b.positionMs
    val continueListening = books
        .filter { (it.inProgress || it.id == state.bookId) && !it.finished }
        .sortedByDescending { it.lastPlayedAt }
    val hero = continueListening.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
                Text("EARMARK", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
                Text(greeting, style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (books.isEmpty()) {
            item { EmptyLibrary(scanStatus, onAddFolder) }
            return@LazyColumn
        }
        if (hero != null) {
            item { ContinueCard(hero, pos(hero), state.bookId == hero.id && state.isPlaying, onPlay, onToggle) }
        }
        val rest = continueListening.drop(1)
        if (rest.isNotEmpty()) item { Shelf("Continue listening", rest, ::pos, onPlay) }
        val recent = books.sortedByDescending { it.addedAt }.take(15)
        item { Shelf("Recently added", recent, ::pos, onPlay) }
        val notStarted = books.filter { it.notStarted }.sortedWith(compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
        if (notStarted.isNotEmpty()) item { Shelf("Up next", notStarted, ::pos, onPlay) }
        val finished = books.filter { it.finished }.sortedByDescending { it.lastPlayedAt }
        if (finished.isNotEmpty()) item { Shelf("Finished", finished, ::pos, onPlay) }
    }
}

@Composable
private fun ContinueCard(
    book: Book, position: Long, isPlaying: Boolean, onPlay: (Book) -> Unit, onToggle: (Book) -> Unit,
) {
    val ch = book.chapters.getOrNull(book.chapterIndexAt(position))
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .clickable { onPlay(book) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.size(104.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("CONTINUE LISTENING", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
            Spacer(Modifier.height(4.dp))
            Text(
                book.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
            if (ch != null && book.chapters.size > 1) {
                Text(
                    ch.title, style = MaterialTheme.typography.bodySmall, color = Palette.Faint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            BookStatus(book, position)
        }
        Spacer(Modifier.width(8.dp))
        PlayCircle(isPlaying, 48.dp, onClick = { onToggle(book) })
    }
}

@Composable
private fun Shelf(title: String, books: List<Book>, pos: (Book) -> Long, onPlay: (Book) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(books, key = { it.id }) { b ->
                Column(Modifier.width(124.dp).clickable { onPlay(b) }) {
                    Cover(b, Modifier.fillMaxWidth().aspectRatio(1f))
                    val p = pos(b)
                    if (p > 0 && !b.finished && b.durationMs > 0) {
                        Spacer(Modifier.height(6.dp))
                        ProgressLine((p.toFloat() / b.durationMs).coerceIn(0f, 1f), Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        b.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(b.author, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Library

private enum class Filter(val label: String) { All("All"), NotStarted("Not started"), InProgress("In progress"), Finished("Finished") }
private enum class Sort(val label: String) { Recent("Recent"), Title("Title"), Author("Author"), Added("Date added"), Length("Length") }

@Composable
fun LibraryScreen(
    books: List<Book>,
    state: PlaybackState,
    scanStatus: String?,
    onPlay: (Book) -> Unit,
    onToggle: (Book) -> Unit,
    onAddFolder: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var sort by rememberSaveable { mutableStateOf(Sort.Recent) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }

    fun pos(b: Book) = if (state.bookId == b.id) state.positionMs else b.positionMs

    val q = query.trim().lowercase()
    val shown = books
        .filter {
            when (filter) {
                Filter.All -> true
                Filter.NotStarted -> it.notStarted && it.id != state.bookId
                Filter.InProgress -> !it.finished && (it.positionMs > 0 || it.id == state.bookId)
                Filter.Finished -> it.finished
            }
        }
        .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.author.lowercase().contains(q) || it.narrator.lowercase().contains(q) }
        .let { list ->
            when (sort) {
                Sort.Recent -> list.sortedWith(compareByDescending<Book> { it.lastPlayedAt }.thenByDescending { it.addedAt })
                Sort.Title -> list.sortedWith(compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
                Sort.Author -> list.sortedWith(compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.author }.thenBy { it.title })
                Sort.Added -> list.sortedByDescending { it.addedAt }
                Sort.Length -> list.sortedByDescending { it.durationMs }
            }
        }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search")
            }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(s.label, color = if (s == sort) Palette.Accent else Palette.Text)
                            },
                            onClick = { sort = s; sortOpen = false },
                        )
                    }
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search title, author or narrator") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Line,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Filter.entries) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Palette.Text,
                        selectedLabelColor = Palette.Bg,
                        containerColor = Palette.Bg,
                        labelColor = Palette.Text,
                    ),
                )
            }
        }
        val scanning = scanStatus
        if (scanning != null) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp),
                color = Palette.Accent, trackColor = Palette.Line,
            )
        }
        if (books.isEmpty()) {
            EmptyLibrary(scanStatus, onAddFolder)
            return@Column
        }
        Text(
            "${shown.size} ${if (shown.size == 1) "title" else "titles"}",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.id }) { b ->
                LibraryRow(b, pos(b), state.bookId == b.id && state.isPlaying, onPlay, onToggle)
            }
        }
    }
}

@Composable
private fun LibraryRow(book: Book, position: Long, isPlaying: Boolean, onPlay: (Book) -> Unit, onToggle: (Book) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay(book) }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.size(76.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "By: ${book.author}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (book.narrator.isNotBlank()) {
                Text(
                    "Narrated by: ${book.narrator}", style = MaterialTheme.typography.bodySmall,
                    color = Palette.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            BookStatus(book, position)
        }
        PlayCircle(isPlaying, 40.dp, onClick = { onToggle(book) }, filled = false)
        BookMenu(book)
    }
}
