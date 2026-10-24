package com.oldspell.app.ui

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oldspell.app.OldspellApp
import com.oldspell.app.data.Book
import com.oldspell.app.playback.PlaybackState
import com.oldspell.app.playback.PlayerConnection

// ---------------------------------------------------------------- Mini player

@Composable
fun MiniPlayer(book: Book, state: PlaybackState, conn: PlayerConnection, onOpen: () -> Unit) {
    val active = state.bookId == book.id
    val pos = if (active) state.positionMs else book.positionMs
    val isPlaying = active && state.isPlaying
    val skipBack by OldspellApp.instance.settings.skipBackSec.collectAsStateWithLifecycle()
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
fun SettingsScreen(hasAccess: Boolean, onRequestAccess: () -> Unit) {
    val app = OldspellApp.instance
    val context = LocalContext.current
    val folders by app.store.folders.collectAsStateWithLifecycle()
    val included by app.settings.includedFolders.collectAsStateWithLifecycle()
    val hidden by app.settings.hidden.collectAsStateWithLifecycle()
    val skipBack by app.settings.skipBackSec.collectAsStateWithLifecycle()
    val skipFwd by app.settings.skipForwardSec.collectAsStateWithLifecycle()
    val scanStatus by app.store.scanStatus.collectAsStateWithLifecycle()
    val books by app.store.books.collectAsStateWithLifecycle()
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Settings", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item { SectionHeader("Audiobook folders") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                when {
                    !hasAccess -> {
                        Text(
                            "Oldspell needs access to the audio files on your phone to find your books.",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        AccentButton("Allow access to audio", onRequestAccess)
                    }
                    folders.isEmpty() -> Text(
                        if (scanStatus != null) "Looking for audio…" else "No audio files found on this phone yet.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    else -> {
                        Text(
                            "Every folder on your phone that holds audio. Turn on the ones with audiobooks.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        FolderChooser(folders, included ?: emptySet()) { key ->
                            app.settings.toggleFolder(key)
                            app.rescan()
                        }
                    }
                }
            }
        }
        if (hasAccess) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PillButton(Icons.Filled.Refresh, if (scanStatus != null) "Scanning…" else "Look for new books") {
                        app.rescan()
                    }
                }
                scanStatus?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
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
                    "• Cover art comes from the picture embedded in the audio file.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item { SectionHeader("About", Modifier.padding(top = 16.dp)) }
        item {
            val version: String = remember {
                val name: String? = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    null
                }
                name ?: ""
            }
            Text(
                "Oldspell $version",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                "Open-source licenses",
                color = Palette.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLicenses = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Text(
                "Privacy: Oldspell collects no data. Your books and listening progress stay on this phone.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
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
