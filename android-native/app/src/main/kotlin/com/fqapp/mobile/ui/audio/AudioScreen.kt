package com.fqapp.mobile.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fqapp.mobile.audio.ChapterPlayer
import com.fqapp.mobile.data.CatalogRepository
import com.fqapp.mobile.data.ChapterRef
import kotlinx.coroutines.CancellationException

private fun format(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * 听书页：一次只播一章，播完自动接下一章；上一章/下一章随时切。
 * 音色固定用默认（tone 0），多音色选择留给后续。
 */
@Composable
fun AudioScreen(
    bookId: String,
    startItemId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { CatalogRepository(context) }
    val player = remember { ChapterPlayer() }

    var chapters by remember { mutableStateOf<List<ChapterRef>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>("正在取音频地址…") }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    LaunchedEffect(bookId) {
        try {
            val loaded = repository.directory(bookId).flatMap { volume -> volume.chapters }
            chapters = loaded
            val position = loaded.indexOfFirst { it.itemId == startItemId }
            index = if (position >= 0) position else 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = failure.message ?: "目录加载失败"
        }
    }

    LaunchedEffect(index, chapters) {
        val chapter = chapters.getOrNull(index) ?: return@LaunchedEffect
        status = "正在取音频地址…"
        try {
            val source = repository.audioSource(bookId, chapter.itemId)
            status = null
            player.play(source.url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = failure.message ?: "音频加载失败"
        }
    }

    LaunchedEffect(player) {
        player.onCompleted = {
            if (index + 1 < chapters.size) index++
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("听书", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Filled.Headset,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            chapters.getOrNull(index)?.title ?: "—",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        val failed = status ?: player.error
        if (failed != null) {
            Text(
                failed,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val total = player.durationMs.coerceAtLeast(1L)
            Slider(
                value = player.positionMs.coerceIn(0L, total).toFloat(),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..total.toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(format(player.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(format(player.durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一章")
            }
            if (player.buffering) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                FilledIconButton(
                    onClick = { if (status == null) player.toggle() },
                    enabled = status == null,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player.playing) "暂停" else "播放",
                    )
                }
            }
            IconButton(
                onClick = { if (index + 1 < chapters.size) index++ },
                enabled = index + 1 < chapters.size,
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一章")
            }
        }
    }
}