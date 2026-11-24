package com.fqapp.mobile.library

import android.content.Context
import com.fqapp.mobile.data.CatalogRepository
import com.fqapp.mobile.data.ChapterRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 章节正文的唯一入口：先看缓存，缺了才联网，拿到就落盘。
 * 阅读器与整本下载都走这里，所以读过的章天然离线可读。
 */
class ChapterLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = SqliteChapterCache(appContext)
    private val repository = CatalogRepository(appContext)

    suspend fun load(bookId: String, chapter: ChapterRef): String = withContext(Dispatchers.IO) {
        cache.read(bookId, chapter.itemId) ?: fetch(bookId, chapter)
    }

    suspend fun fetch(bookId: String, chapter: ChapterRef): String {
        val text = repository.chapterText(chapter.itemId)
        cache.write(bookId, chapter, text)
        return text
    }

    fun cached(bookId: String, itemId: String): String? = cache.read(bookId, itemId)

    fun downloaded(bookId: String): Set<String> = cache.downloadedIds(bookId)

    /**
     * 整本下载：同时抓 [concurrency] 章，已有缓存直接跳过。单章失败只记数不中断，
     * 用户重试时补抓剩下的。
     */
    suspend fun downloadAll(
        bookId: String,
        chapters: List<ChapterRef>,
        concurrency: Int = 3,
        onProgress: (completed: Int, failed: Int) -> Unit = { _, _ -> },
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val cached = cache.downloadedIds(bookId)
        val pending = chapters.filter { it.itemId !in cached }
        var completed = chapters.size - pending.size
        var failed = 0
        onProgress(completed, failed)
        pending.chunked(concurrency).forEach { window ->
            val results = coroutineScope {
                window.map { chapter ->
                    async {
                        try {
                            fetch(bookId, chapter)
                            true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                    }
                }.awaitAll()
            }
            val ok = results.count { it }
            completed += ok
            failed += results.size - ok
            onProgress(completed, failed)
        }
        completed to failed
    }
}