package com.earmark.app.ui

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
import com.earmark.app.EarmarkApp
import com.earmark.app.data.Book
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
    val app = EarmarkApp.instance
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

@Composable
fun EmptyLibrary(scanStatus: String?, onAddFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your library is empty", style = MaterialTheme.typography.titleLarge)
        Text(
            scanStatus ?: "Choose the folder on your phone where you keep audiobooks. " +
                "Each .m4b file, or each folder of mp3s, becomes a book.",
            color = Palette.Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Accent)
                .clickable(onClick = onAddFolder)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Add audiobook folder", color = Palette.OnAccent, fontWeight = FontWeight.Bold)
        }
    }
}
