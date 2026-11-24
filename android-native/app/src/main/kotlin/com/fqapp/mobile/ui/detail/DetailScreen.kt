package com.fqapp.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fqapp.mobile.data.Book
import com.fqapp.mobile.data.CatalogRepository
import com.fqapp.mobile.library.SqliteLibraryStore
import com.fqapp.mobile.ui.store.ErrorState
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private sealed interface DetailState {
    data object Loading : DetailState
    data class Loaded(val book: Book) : DetailState
    data class Failed(val message: String) : DetailState
}

@Composable
fun DetailScreen(bookId: String, onBack: () -> Unit, onOpenChapters: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CatalogRepository(context) }
    val store = remember { SqliteLibraryStore(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reloadKey = remember { mutableIntStateOf(0) }
    val state by produceState<DetailState>(DetailState.Loading, bookId, reloadKey.intValue) {
        value = DetailState.Loading
        value = try {
            val book = repository.detail(bookId)
            store.touchBook(book)
            DetailState.Loaded(book)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DetailState.Failed(failure.message ?: "加载失败")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val book = (state as? DetailState.Loaded)?.book
            if (book != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenChapters,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("目录")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    snackbar.showSnackbar("阅读器在 P3 提供，这里先只做详情展示")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("开始阅读")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "书籍详情",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            when (val current = state) {
                DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DetailState.Failed -> ErrorState(current.message, onRetry = { reloadKey.intValue++ })
                is DetailState.Loaded -> DetailBody(current.book)
            }
        }
    }
}

@Composable
private fun DetailBody(book: Book) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            book.title.ifBlank { "（无书名）" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (book.author.isNotBlank()) {
            Text(
                book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val meta = listOf(book.category, book.words, book.chapters)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (book.lastChapter.isNotBlank()) {
            Text(
                "最新：${book.lastChapter}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            book.abstract.ifBlank { "这本书还没有简介。" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}