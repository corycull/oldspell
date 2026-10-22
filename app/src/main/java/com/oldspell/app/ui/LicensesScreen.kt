package com.oldspell.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oldspell.app.R

private data class Library(val name: String, val owner: String, val url: String)

/** Third-party software bundled in the app. All are Apache License 2.0. */
private val LIBRARIES = listOf(
    Library("AndroidX Media3 (ExoPlayer, Session)", "The Android Open Source Project", "https://github.com/androidx/media"),
    Library("Jetpack Compose (UI, Foundation, Material 3, Material Icons)", "The Android Open Source Project", "https://developer.android.com/jetpack/compose"),
    Library("AndroidX Core, Activity, Lifecycle", "The Android Open Source Project", "https://developer.android.com/jetpack/androidx"),
    Library("Kotlin Standard Library", "JetBrains s.r.o. and Kotlin Programming Language contributors", "https://kotlinlang.org"),
    Library("kotlinx.coroutines", "JetBrains s.r.o. and contributors", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("Coil", "Coil Contributors", "https://github.com/coil-kt/coil"),
    Library("Guava", "The Guava Authors", "https://github.com/google/guava"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val licenseText = remember {
        context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
    }
    var showText by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text("Open-source licenses", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Oldspell is built with the following open-source software, each licensed under the " +
                        "Apache License, Version 2.0.",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(LIBRARIES) { lib ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(lib.name, style = MaterialTheme.typography.titleSmall)
                    Text("Copyright ${lib.owner}", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    Text(lib.url, style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
                }
                HorizontalDivider(color = Palette.Line, thickness = 0.5.dp)
            }
            item {
                Text(
                    if (showText) "Hide license text" else "Show Apache License 2.0 text",
                    color = Palette.Accent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showText = !showText }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            if (showText) {
                item {
                    Text(
                        licenseText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Palette.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
