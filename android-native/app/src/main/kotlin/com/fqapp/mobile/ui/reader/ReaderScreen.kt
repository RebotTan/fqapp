package com.fqapp.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fqapp.mobile.data.CatalogRepository
import com.fqapp.mobile.data.ChapterRef
import com.fqapp.mobile.library.ChapterLoader
import com.fqapp.mobile.library.SqliteLibraryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val bodyStyle = TextStyle(fontSize = 17.sp, lineHeight = 30.sp)

/**
 * 阅读器：章内按屏分页，点击左右三分之一翻页，翻到头自动换章。
 * 进度只记「读到哪一章」，页内位置在重进时回到该章第一页。
 */
@Composable
fun ReaderScreen(
    bookId: String,
    startItemId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { CatalogRepository(context) }
    val loader = remember { ChapterLoader(context) }
    val store = remember { SqliteLibraryStore(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var chapters by remember { mutableStateOf<List<ChapterRef>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var chrome by remember { mutableStateOf(true) }

    LaunchedEffect(bookId) {
        try {
            val loaded = repository.directory(bookId).flatMap { volume -> volume.chapters }
            chapters = loaded
            val target = startItemId ?: store.progressOf(bookId)?.itemId
            val position = loaded.indexOfFirst { it.itemId == target }
            index = if (position >= 0) position else 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "目录加载失败"
        }
    }

    LaunchedEffect(index, chapters) {
        val chapter = chapters.getOrNull(index) ?: return@LaunchedEffect
        text = null
        error = null
        page = 0
        try {
            text = loader.load(bookId, chapter)
            store.saveProgress(bookId, chapter, index)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "正文加载失败"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val pageHeight = with(density) { (maxHeight - 96.dp).toPx() }
        val pageWidth = with(density) { maxWidth.roundToPx() }
        val pages = remember(text, pageWidth, pageHeight) {
            val body = text ?: return@remember emptyList<IntRange>()
            val layout = measurer.measure(
                text = AnnotatedString(body),
                style = bodyStyle,
                constraints = Constraints(maxWidth = pageWidth),
            )
            ReaderPaginator.pageRanges(
                lineStarts = IntArray(layout.lineCount) { layout.getLineStart(it) },
                lineTops = FloatArray(layout.lineCount) { layout.getLineTop(it) },
                lineBottoms = FloatArray(layout.lineCount) { layout.getLineBottom(it) },
                textLength = body.length,
                pageHeight = pageHeight,
            )
        }
        val current = pages.getOrNull(page)

        Column(modifier = Modifier.fillMaxSize()) {
            if (chrome) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        chapters.getOrNull(index)?.title ?: "阅读",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pages.size) {
                        detectTapGestures { offset ->
                            val ratio = offset.x / size.width
                            when {
                                ratio < 0.3f -> {
                                    if (page > 0) {
                                        page--
                                    } else if (index > 0) {
                                        index--
                                    } else {
                                        scope.launch { snackbar.showSnackbar("已是第一章") }
                                    }
                                }
                                ratio > 0.7f -> when {
                                    page + 1 < pages.size -> page++
                                    index + 1 < chapters.size -> index++
                                    else -> scope.launch { snackbar.showSnackbar("已是最后一章") }
                                }
                                else -> chrome = !chrome
                            }
                        }
                    },
            ) {
                when {
                    error != null -> Text(
                        "加载失败：$error",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    text == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    current != null -> Text(
                        text = text!!.substring(current.first, current.last + 1),
                        style = bodyStyle,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            if (chrome) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${page + 1}/${pages.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        onClick = { if (index + 1 < chapters.size) index++ },
                        enabled = index + 1 < chapters.size,
                    ) {
                        Text("下一章")
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}