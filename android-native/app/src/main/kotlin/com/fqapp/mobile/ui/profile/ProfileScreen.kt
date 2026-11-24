package com.fqapp.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fqapp.mobile.core.FqCoreBridge
import com.fqapp.mobile.library.LibraryDatabase
import com.fqapp.mobile.library.SqliteLibraryStore
import com.fqapp.mobile.settings.ReadingPrefs

/** 我的：阅读设置、缓存清理、版本。 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val store = remember { SqliteLibraryStore(context) }
    var books by remember { mutableIntStateOf(0) }
    var chapters by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        books = store.recentBooks(limit = 1000).size
        chapters = LibraryDatabase.of(context).readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM chapters",
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("阅读字号", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = ReadingPrefs.fontScale,
                onValueChange = { ReadingPrefs.updateFontScale(it) },
                valueRange = ReadingPrefs.MIN_SCALE..ReadingPrefs.MAX_SCALE,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("A", style = MaterialTheme.typography.titleMedium)
        }

        Text("外观", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                null to "跟随系统",
                true to "深色",
                false to "浅色",
            ).forEach { (value, label) ->
                OutlinedButton(
                    onClick = { ReadingPrefs.updateDarkOverride(value) },
                    enabled = ReadingPrefs.darkOverride != value,
                ) { Text(label) }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("书架 $books 本 · 已缓存 $chapters 章", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirming = true }) { Text("清理正文缓存") }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.weight(1f))
        Text(
            FqCoreBridge.nativeVersion(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("清理正文缓存？") },
            text = { Text("已下载的 $chapters 章正文会被删除，书架和阅读进度保留。") },
            confirmButton = {
                TextButton(onClick = {
                    LibraryDatabase.of(context).writableDatabase.delete("chapters", null, null)
                    confirming = false
                    refresh()
                    message = "已清理，共 $chapters 章"
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("取消") }
            },
        )
    }
}