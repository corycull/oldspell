package com.oldspell.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oldspell.app.OldspellApp
import com.oldspell.app.data.Book
import com.oldspell.app.data.Bookmark
import com.oldspell.app.playback.PlaybackState
import com.oldspell.app.playback.PlayerConnection
import com.oldspell.app.playback.SleepTimer
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Sheet { None, Speed, Sleep, Chapters }

/**
 * The player is drawn on top of the app's Scaffold, so it doesn't inherit the Scaffold's
 * content color; set white text/icons explicitly or they default to black.
 */
@Composable
fun PlayerScreen(book: Book, state: PlaybackState, conn: PlayerConnection, onClose: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Palette.Text) {
        PlayerContent(book, state, conn, onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(book: Book, state: PlaybackState, conn: PlayerConnection, onClose: () -> Unit) {
    val app = OldspellApp.instance
    val context = LocalContext.current
    val skipBack by app.settings.skipBackSec.collectAsStateWithLifecycle()
    val skipFwd by app.settings.skipForwardSec.collectAsStateWithLifecycle()
    val savedSpeed by app.settings.speed.collectAsStateWithLifecycle()
    val sleep by SleepTimer.mode.collectAsStateWithLifecycle()

    val active = state.bookId == book.id
    val pos = if (active) state.positionMs else book.positionMs
    val isPlaying = active && state.isPlaying
    val speed = if (active) state.speed else savedSpeed

    val chIndex = book.chapterIndexAt(pos)
    val chapter = book.chapters.getOrNull(chIndex)
    val chStart = chapter?.startMs ?: 0L
    val chLen = (chapter?.durationMs ?: book.durationMs).coerceAtLeast(1L)

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: ((pos - chStart).toFloat() / chLen).coerceIn(0f, 1f)
    val shownPos = chStart + (fraction * chLen).toLong()
    var sheet by remember { mutableStateOf(Sheet.None) }

    val bg = Brush.verticalGradient(listOf(Color(0xFF2A2218), Palette.Bg, Palette.Bg))

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // block touches behind
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, "Close player", modifier = Modifier.size(32.dp))
            }
            Text(
                "Now playing", style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            BookMenu(book, onRemoved = onClose)
        }

        Spacer(Modifier.weight(0.4f))
        Cover(
            book,
            Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 420.dp)
                .aspectRatio(1f)
                .shadow(24.dp, RoundedCornerShape(8.dp)),
            corner = 8.dp,
        )
        Spacer(Modifier.weight(0.4f))

        Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                book.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(book.author, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
        }

        Spacer(Modifier.height(18.dp))

        // Chapter + scrubber (scrubs within the current chapter, like Audible)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                chapter?.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { sheet = Sheet.Chapters }
                    .padding(vertical = 4.dp),
            )
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let { conn.seekTo(book, chStart + (it * chLen).toLong()) }
                    dragFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Text,
                    activeTrackColor = Palette.Accent,
                    inactiveTrackColor = Palette.Line,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(shownPos - chStart), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Text(
                    timeLeft(((book.durationMs - shownPos) / speed.coerceAtLeast(0.1f)).toLong()),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Text,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Text(
                    "-" + clock(chStart + chLen - shownPos),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Transport
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { conn.previousChapter(book) }) {
                Icon(Icons.Filled.SkipPrevious, "Previous chapter", modifier = Modifier.size(30.dp))
            }
            SkipButton(replayIcon(skipBack), skipBack, "Back $skipBack seconds") {
                conn.skip(book, -skipBack * 1000L)
            }
            Box(contentAlignment = Alignment.Center) {
                PlayCircle(isPlaying, 78.dp, onClick = { conn.togglePlay(book) })
                if (active && state.buffering) {
                    CircularProgressIndicator(
                        Modifier.size(84.dp), color = Palette.Accent.copy(alpha = 0.5f), strokeWidth = 2.dp,
                    )
                }
            }
            SkipButton(forwardIcon(skipFwd), skipFwd, "Forward $skipFwd seconds") {
                conn.skip(book, skipFwd * 1000L)
            }
            IconButton(onClick = { conn.nextChapter(book) }) {
                Icon(Icons.Filled.SkipNext, "Next chapter", modifier = Modifier.size(30.dp))
            }
        }

        Spacer(Modifier.weight(0.3f))

        // Bottom actions
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionButton(Icons.Filled.Equalizer, speedLabel(speed), highlighted = abs(speed - 1f) > 0.001f) {
                sheet = Sheet.Speed
            }
            val sleepLabel = when (val m = sleep) {
                is SleepTimer.Mode.Countdown -> clock(m.remainingMs)
                is SleepTimer.Mode.EndOfChapter -> "End of ch."
                SleepTimer.Mode.Off -> "Sleep"
            }
            ActionButton(Icons.Filled.Bedtime, sleepLabel, highlighted = sleep != SleepTimer.Mode.Off) {
                sheet = Sheet.Sleep
            }
            ActionButton(Icons.Filled.List, "Chapters") { sheet = Sheet.Chapters }
            ActionButton(Icons.Filled.BookmarkAdd, "Bookmark") {
                val at = conn.positionOf(book)
                app.store.addBookmark(book.id, at, "")
                Toast.makeText(context, "Bookmark added at ${clock(at)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (sheet != Sheet.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.None },
            sheetState = sheetState,
            containerColor = Palette.Surface,
        ) {
            when (sheet) {
                Sheet.Speed -> SpeedSheet(speed) { conn.setSpeed(it) }
                Sheet.Sleep -> SleepSheet(sleep, chapter != null) { minutes ->
                    if (minutes == null) SleepTimer.cancel()
                    else if (minutes < 0) chapter?.let { SleepTimer.setEndOfChapter(book.id, it.endMs) }
                    else SleepTimer.setMinutes(minutes)
                    if (minutes != null && !isPlaying) conn.play(book)
                    sheet = Sheet.None
                }
                Sheet.Chapters -> ChaptersSheet(book, pos, chIndex, conn) { sheet = Sheet.None }
                Sheet.None -> {}
            }
        }
    }
}

@Composable
private fun SkipButton(icon: ImageVector, seconds: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, modifier = Modifier.size(38.dp))
        if (seconds !in setOf(5, 10, 30)) {
            Text("$seconds", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (highlighted) Palette.Accent else Palette.Text
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Sheets

@Composable
private fun SpeedSheet(speed: Float, onChange: (Float) -> Unit) {
    fun snap(v: Float) = ((v * 20).roundToInt() / 20f).coerceIn(0.5f, 3.5f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Narration speed", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Filled.Remove, "Slower") { onChange(snap(speed - 0.05f)) }
            Text(
                speedLabel(speed), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundIcon(Icons.Filled.Add, "Faster") { onChange(snap(speed + 0.05f)) }
        }
        Slider(
            value = speed,
            onValueChange = { onChange(snap(it)) },
            valueRange = 0.5f..3.5f,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Text, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Line,
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                val sel = abs(s - speed) < 0.001f
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Palette.Text else Palette.SurfaceHi)
                        .clickable { onChange(s) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(speedLabel(s), color = if (sel) Palette.Bg else Palette.Text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Palette.SurfaceHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description) }
}

/** onPick(null) = off, onPick(-1) = end of chapter, otherwise minutes. */
@Composable
private fun SleepSheet(mode: SleepTimer.Mode, hasChapter: Boolean, onPick: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            "Sleep timer", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            "Shake your phone while the timer runs to restart it.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        val countdownTotal = (mode as? SleepTimer.Mode.Countdown)?.totalMs
        SheetOption("Off", mode == SleepTimer.Mode.Off) { onPick(null) }
        listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
            SheetOption("$m minutes", countdownTotal == m * 60_000L) { onPick(m) }
        }
        if (hasChapter) SheetOption("End of chapter", mode is SleepTimer.Mode.EndOfChapter) { onPick(-1) }
    }
}

@Composable
private fun SheetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (selected) Palette.Accent else Palette.Text)
        if (selected) Icon(Icons.Filled.Check, null, tint = Palette.Accent)
    }
}

@Composable
private fun ChaptersSheet(book: Book, position: Long, current: Int, conn: PlayerConnection, onDone: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Palette.Surface,
            contentColor = Palette.Text,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[tab]), color = Palette.Accent,
                )
            },
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Chapters") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks (${book.bookmarks.size})") })
        }
        if (tab == 0) ChapterList(book, position, current, conn, onDone)
        else BookmarkList(book, conn, onDone)
    }
}

@Composable
private fun ChapterList(book: Book, position: Long, current: Int, conn: PlayerConnection, onDone: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (current > 2) listState.scrollToItem(current - 2) }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(book.chapters) { i, ch ->
            val isCurrent = i == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isCurrent) Palette.SurfaceHi else Color.Transparent)
                    .clickable { conn.play(book, ch.startMs); onDone() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ch.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) Palette.Accent else Palette.Text,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(
                            ((position - ch.startMs).toFloat() / ch.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
                            Modifier.fillMaxWidth(0.6f),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(clock(ch.durationMs), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            HorizontalDivider(color = Palette.Line, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun BookmarkList(book: Book, conn: PlayerConnection, onDone: () -> Unit) {
    val app = OldspellApp.instance
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    if (book.bookmarks.isEmpty()) {
        Text(
            "No bookmarks yet. Tap Bookmark on the player to save your place.",
            color = Palette.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(book.bookmarks, key = { it.id }) { bm ->
            val ch = book.chapters.getOrNull(book.chapterIndexAt(bm.positionMs))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { conn.play(book, bm.positionMs); onDone() }
                    .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(clock(bm.positionMs), fontWeight = FontWeight.SemiBold)
                    if (ch != null) {
                        Text(ch.title, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
                    }
                    if (bm.note.isNotBlank()) {
                        Text(bm.note, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    }
                }
                IconButton(onClick = { editing = bm }) { Icon(Icons.Filled.Edit, "Edit note", tint = Palette.Muted) }
                IconButton(onClick = { app.store.removeBookmark(book.id, bm.id) }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Palette.Muted)
                }
            }
            HorizontalDivider(color = Palette.Line, thickness = 0.5.dp)
        }
    }
    editing?.let { bm ->
        var text by remember(bm.id) { mutableStateOf(bm.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Note at ${clock(bm.positionMs)}") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2)
            },
            confirmButton = {
                TextButton(onClick = { app.store.updateBookmarkNote(book.id, bm.id, text.trim()); editing = null }) {
                    Text("Save", color = Palette.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            containerColor = Palette.SurfaceHi,
        )
    }
}
