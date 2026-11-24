package com.fqapp.mobile.ui.chapters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fqapp.mobile.data.CatalogRepository
import com.fqapp.mobile.data.ChapterRef
import com.fqapp.mobile.data.ChapterVolume
import com.fqapp.mobile.library.SqliteLibraryStore
import com.fqapp.mobile.ui.store.ErrorState
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private sealed interface DirectoryState {
    data object Loading : DirectoryState
    data class Loaded(val volumes: List<ChapterVolume>) : DirectoryState
    data class Failed(val message: String) : DirectoryState
}

private sealed interface Row {
    data class Volume(val name: String) : Row
    data class Chapter(val ref: ChapterRef, val index: Int) : Row
}

@Composable
fun ChapterListScreen(bookId: String, onBack: () -> Unit, onOpenReader: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { CatalogRepository(context) }
    val store = remember { SqliteLibraryStore(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reloadKey = remember { mutableIntStateOf(0) }

    val state by produceState<DirectoryState>(DirectoryState.Loading, bookId, reloadKey.intValue) {
        value = DirectoryState.Loading
        value = try {
            DirectoryState.Loaded(repository.directory(bookId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DirectoryState.Failed(failure.message ?: "目录加载失败")
        }
    }
    val reading = remember(bookId, reloadKey.intValue) { store.progressOf(bookId) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("目录", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            when (val current = state) {
                DirectoryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DirectoryState.Failed -> ErrorState(current.message, onRetry = { reloadKey.intValue++ })
                is DirectoryState.Loaded -> {
                    val rows = remember(current.volumes) { flatten(current.volumes) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, row -> keyOf(row) }) { _, row ->
                            when (row) {
                                is Row.Volume -> Text(
                                    row.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                is Row.Chapter -> {
                                    val isCurrent = reading?.itemId == row.ref.itemId
                                    Text(
                                        row.ref.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                store.saveProgress(bookId, row.ref, row.index)
                                                onOpenReader(row.ref.itemId)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flatten(volumes: List<ChapterVolume>): List<Row> {
    val rows = ArrayList<Row>()
    var index = 0
    volumes.forEach { volume ->
        if (volume.name.isNotBlank()) rows.add(Row.Volume(volume.name))
        volume.chapters.forEach { chapter ->
            rows.add(Row.Chapter(chapter, index))
            index += 1
        }
    }
    return rows
}

private fun keyOf(row: Row): String = when (row) {
    is Row.Volume -> "v:${row.name}"
    is Row.Chapter -> "c:${row.ref.itemId}"
}