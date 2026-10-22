package com.earmark.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earmark.app.EarmarkApp
import com.earmark.app.data.Book
import com.earmark.app.playback.PlaybackState
import com.earmark.app.playback.PlayerConnection

// ---------------------------------------------------------------- Mini player

@Composable
fun MiniPlayer(book: Book, state: PlaybackState, conn: PlayerConnection, onOpen: () -> Unit) {
    val active = state.bookId == book.id
    val pos = if (active) state.positionMs else book.positionMs
    val isPlaying = active && state.isPlaying
    val skipBack by EarmarkApp.instance.settings.skipBackSec.collectAsStateWithLifecycle()
    val chapter = book.chapters.getOrNull(book.chapterIndexAt(pos))
    val chProgress = if (chapter != null && chapter.durationMs > 0) {
        ((pos - chapter.startMs).toFloat() / chapter.durationMs).coerceIn(0f, 1f)
    } else book.progress

    Column(Modifier.fillMaxWidth().background(Palette.SurfaceHi).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Line)) {
            Box(Modifier.fillMaxWidth(chProgress).height(2.dp).background(Palette.Accent))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(book, Modifier.size(46.dp), corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (chapter != null && book.chapters.size > 1) chapter.title else timeLeft(book.durationMs - pos),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { conn.skip(book, -skipBack * 1000L) }) {
                Icon(replayIcon(skipBack), "Back $skipBack seconds")
            }
            IconButton(onClick = { conn.togglePlay(book) }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(onAddFolder: () -> Unit) {
    val app = EarmarkApp.instance
    val context = LocalContext.current
    val folders by app.settings.folders.collectAsStateWithLifecycle()
    val hidden by app.settings.hidden.collectAsStateWithLifecycle()
    val skipBack by app.settings.skipBackSec.collectAsStateWithLifecycle()
    val skipFwd by app.settings.skipForwardSec.collectAsStateWithLifecycle()
    val scanStatus by app.store.scanStatus.collectAsStateWithLifecycle()
    val books by app.store.books.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Settings", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item { SectionHeader("Audiobook folders") }
        items(folders, key = { it }) { f ->
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, null, tint = Palette.Accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folderLabel(f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${books.count { it.folderUri == f }} books",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                }
                IconButton(onClick = {
                    app.settings.removeFolder(f)
                    app.store.removeFolder(f)
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: Exception) {
                    }
                }) { Icon(Icons.Filled.Close, "Remove folder", tint = Palette.Muted) }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PillButton(Icons.Filled.Add, "Add folder", primary = true, onClick = onAddFolder)
                if (folders.isNotEmpty()) {
                    PillButton(Icons.Filled.Refresh, if (scanStatus != null) "Scanning…" else "Rescan") {
                        app.rescan()
                    }
                }
            }
            scanStatus?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item { SectionHeader("Playback", Modifier.padding(top = 16.dp)) }
        item { IntervalPicker("Skip back", skipBack) { app.settings.setSkipBack(it) } }
        item { IntervalPicker("Skip forward", skipFwd) { app.settings.setSkipForward(it) } }
        item {
            Text(
                "Headphone, lock-screen and car controls use these intervals too.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (hidden.isNotEmpty()) {
            item { SectionHeader("Library", Modifier.padding(top = 16.dp)) }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    PillButton(Icons.Filled.Refresh, "Restore ${hidden.size} removed books") {
                        app.settings.unhideAll()
                        app.rescan()
                    }
                }
            }
        }

        item { SectionHeader("How books are found", Modifier.padding(top = 16.dp)) }
        item {
            Text(
                "• Every .m4b file is a book, with its built-in chapters.\n" +
                    "• Every folder of mp3 / m4a / ogg / flac files is a book; each file is a chapter.\n" +
                    "• Folders split into “CD 1”, “Disc 2”… are joined into one book.\n" +
                    "• Cover art comes from the file, or a cover.jpg / folder.jpg next to it.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

private fun folderLabel(uri: String): String {
    val decoded = Uri.decode(uri)
    val path = decoded.substringAfterLast("/tree/").substringBefore("/document/")
    val volume = path.substringBefore(':')
    val rest = path.substringAfter(':', "")
    val vol = if (volume == "primary") "Internal storage" else volume
    return if (rest.isEmpty()) vol else "$vol / $rest"
}

@Composable
private fun IntervalPicker(label: String, value: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(label)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 30, 45, 60).forEach { s ->
                val sel = s == value
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Palette.Text else Palette.SurfaceHi)
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${s}s", color = if (sel) Palette.Bg else Palette.Text)
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) Palette.Accent else Palette.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (primary) Palette.OnAccent else Palette.Text
        Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c, fontWeight = FontWeight.SemiBold)
    }
}
