package com.oldspell.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oldspell.app.data.AudioFolder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.oldspell.app.OldspellApp
import com.oldspell.app.data.Book
import java.io.File
import kotlin.math.abs

/** Square cover art, or a generated title card when the book has none. */
@Composable
fun Cover(book: Book, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val hue = (abs(book.id.hashCode()) % 360).toFloat()
    val gradient = Brush.linearGradient(
        listOf(Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.18f)),
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                book.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(3.dp).clip(CircleShape),
        color = Palette.Accent,
        trackColor = Palette.Line,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

fun replayIcon(sec: Int): ImageVector = when (sec) {
    5 -> Icons.Filled.Replay5
    10 -> Icons.Filled.Replay10
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.Replay
}

fun forwardIcon(sec: Int): ImageVector = when (sec) {
    5 -> Icons.Filled.Forward5
    10 -> Icons.Filled.Forward10
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.Redo
}

/** Status line under a book: progress bar + time left, "Finished", or total length. */
@Composable
fun BookStatus(book: Book, positionMs: Long, modifier: Modifier = Modifier) {
    when {
        book.finished -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Finished", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        positionMs > 0 -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            val p = if (book.durationMs > 0) (positionMs.toFloat() / book.durationMs).coerceIn(0f, 1f) else 0f
            ProgressLine(p, Modifier.width(72.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                timeLeft(book.durationMs - positionMs),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
        else -> Text(
            hoursMinutes(book.durationMs), modifier = modifier,
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        )
    }
}

@Composable
fun PlayCircle(isPlaying: Boolean, size: Dp, onClick: () -> Unit, filled: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Palette.Accent else Palette.SurfaceHi)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (filled) Palette.OnAccent else Palette.Text,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Overflow menu shared by library rows and the player. */
@Composable
fun BookMenu(book: Book, onRemoved: () -> Unit = {}) {
    val app = OldspellApp.instance
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, "More", tint = Palette.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (book.finished) "Mark as unfinished" else "Mark as finished") },
                onClick = { app.store.setFinished(book.id, !book.finished); open = false },
            )
            if (book.positionMs > 0 && !book.finished) {
                DropdownMenuItem(
                    text = { Text("Reset progress") },
                    onClick = { app.store.resetProgress(book.id); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = {
                    app.settings.hide(book.id)
                    app.store.remove(book.id)
                    open = false
                    onRemoved()
                },
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.Text,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * Shown when there are no books yet: asks for audio access, lets the user tick folders,
 * or explains that no audio was found.
 */
@Composable
fun EmptyLibrary(
    scanStatus: String?,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = OldspellApp.instance
    val folders by app.store.folders.collectAsStateWithLifecycle()
    val included by app.settings.includedFolders.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            !hasAccess -> {
                Text("Find your audiobooks", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Allow Oldspell to see the audio files on your phone. It looks for audiobooks " +
                        "anywhere, including Download, and never changes your files.",
                    color = Palette.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                AccentButton("Allow access to audio", onRequestAccess)
            }
            scanStatus != null && folders.isEmpty() -> {
                Text("Looking for audiobooks…", style = MaterialTheme.typography.titleLarge)
                Text(scanStatus, color = Palette.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
            folders.isEmpty() -> {
                Text("No audio found yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Copy audiobooks onto your phone, for example into a folder called Audiobooks or into " +
                        "Download. Each .m4b file, or each folder of mp3s, becomes a book.",
                    color = Palette.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                AccentButton("Look again") { app.rescan() }
            }
            else -> {
                Text("Which folders hold audiobooks?", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    "Turn on the folders with your books. Music in other folders stays out of your library. " +
                        "You can change this later in Settings.",
                    color = Palette.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
                FolderChooser(folders, included ?: emptySet()) { key ->
                    app.settings.toggleFolder(key)
                    app.rescan()
                }
                scanStatus?.let {
                    Text(it, color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One row per top-level folder with audio, each with an on/off switch. */
@Composable
fun FolderChooser(folders: List<AudioFolder>, included: Set<String>, onToggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        folders.forEach { f ->
            val on = f.key in included
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(f.key) })
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = if (on) Palette.Accent else Palette.Faint)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.label, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.Text)
                    Text(
                        "${f.bookCount} ${if (f.bookCount == 1) "book" else "books"}",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.OnAccent,
                        checkedTrackColor = Palette.Accent,
                        uncheckedThumbColor = Palette.Muted,
                        uncheckedTrackColor = Palette.SurfaceHi,
                        uncheckedBorderColor = Palette.Line,
                    ),
                )
            }
        }
    }
}

@Composable
fun AccentButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label, color = Palette.OnAccent, fontWeight = FontWeight.Bold)
    }
}
