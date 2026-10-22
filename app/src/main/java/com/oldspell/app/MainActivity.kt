package com.oldspell.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oldspell.app.data.Book
import com.oldspell.app.playback.PlayerConnection
import com.oldspell.app.ui.OldspellTheme
import com.oldspell.app.ui.HomeScreen
import com.oldspell.app.ui.LibraryScreen
import com.oldspell.app.ui.MiniPlayer
import com.oldspell.app.ui.Palette
import com.oldspell.app.ui.PlayerScreen
import com.oldspell.app.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    private lateinit var conn: PlayerConnection
    private val openPlayerRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        conn = PlayerConnection(applicationContext)
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayerRequests.value++
        if (savedInstanceState == null) OldspellApp.instance.rescan()
        setContent {
            OldspellTheme { AppRoot(conn, openPlayerRequests) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequests.value++
    }

    override fun onStart() {
        super.onStart()
        conn.connect()
    }

    override fun onStop() {
        conn.disconnect()
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

@Composable
private fun AppRoot(conn: PlayerConnection, openRequests: StateFlow<Int>) {
    val app = OldspellApp.instance
    val context = LocalContext.current
    val books by app.store.books.collectAsStateWithLifecycle()
    val scanStatus by app.store.scanStatus.collectAsStateWithLifecycle()
    val ps by conn.state.collectAsStateWithLifecycle()
    val openReq by openRequests.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openReq) { if (openReq > 0) playerOpen = true }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            app.settings.addFolder(uri.toString())
            app.rescan()
        }
    }
    val addFolder: () -> Unit = { pickFolder.launch(null) }

    // Media notifications work without this, but some phones hide them unless it's granted.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val nowBook: Book? = remember(books, ps.bookId) {
        books.firstOrNull { it.id == ps.bookId }
            ?: books.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }
    }
    val playAndOpen: (Book) -> Unit = { b ->
        conn.play(b)
        playerOpen = true
    }
    val toggle: (Book) -> Unit = { b -> conn.togglePlay(b) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Scaffold(
            containerColor = Palette.Bg,
            bottomBar = {
                Column {
                    nowBook?.let { MiniPlayer(it, ps, conn, onOpen = { playerOpen = true }) }
                    NavigationBar(containerColor = Palette.Surface) {
                        val colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Accent,
                            selectedTextColor = Palette.Accent,
                            indicatorColor = Palette.SurfaceHi,
                            unselectedIconColor = Palette.Muted,
                            unselectedTextColor = Palette.Muted,
                        )
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                            icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                            icon = { Icon(Icons.Filled.LibraryBooks, null) }, label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                            icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(books, ps, scanStatus, playAndOpen, toggle, addFolder)
                    1 -> LibraryScreen(books, ps, scanStatus, playAndOpen, toggle, addFolder)
                    else -> SettingsScreen(addFolder)
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen && nowBook != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            nowBook?.let { PlayerScreen(it, ps, conn, onClose = { playerOpen = false }) }
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
}
