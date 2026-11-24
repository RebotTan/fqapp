package com.fqapp.mobile.data

import android.content.Context
import com.fqapp.mobile.core.ApiException
import com.fqapp.mobile.core.CoreHttp
import com.fqapp.mobile.core.CoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 业务数据入口。P1 只有搜索与详情两条读路径，全部经 Rust core 的 loopback 服务；
 * 本地缓存与进度在 P2 接进来。
 */
class CatalogRepository(private val context: Context) {

    suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        try {
            Channels.parse(http().getJson("/api/v1/recommend/channels"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 频道只是导航辅助，拿不到就退回纯搜索。
            emptyList()
        }
    }

    suspend fun search(
        keyword: String,
        offset: Int = 0,
        count: Int = 20,
        tabType: Int? = null,
    ): List<Book> = withContext(Dispatchers.IO) {
        val query = buildMap {
            put("query", keyword)
            put("offset", offset.toString())
            put("count", count.toString())
            if (tabType != null) put("tab_type", tabType.toString())
        }
        Book.listFromSearch(http().getJson("/api/v1/search", query))
    }

    suspend fun detail(bookId: String): Book = withContext(Dispatchers.IO) {
        Book.fromDetailPayload(http().getJson("/api/v1/books/$bookId"))
    }

    suspend fun chapterText(itemId: String): String = withContext(Dispatchers.IO) {
        val text = ChapterText.extract(http().getJson("/api/v1/chapters/$itemId"))
        if (text.isEmpty()) throw ApiException("正文为空")
        text
    }

    suspend fun audioSource(bookId: String, itemId: String): AudioSourceParser.Source =
        withContext(Dispatchers.IO) {
            AudioSourceParser.parse(
                http().getJson(
                    "/api/v1/audio/books/$bookId/chapters/$itemId",
                    mapOf("tone_id" to "0"),
                ),
                itemId = itemId,
            )
        }

    suspend fun comments(bookId: String, count: Int = 10, offset: Int = 0): List<BookComment> =
        withContext(Dispatchers.IO) {
            Comments.parse(
                http().getJson(
                    "/api/v1/books/$bookId/comments",
                    mapOf("count" to count.toString(), "offset" to offset.toString()),
                ),
            )
        }

    suspend fun directory(bookId: String): List<ChapterVolume> = withContext(Dispatchers.IO) {
        DirectoryParser.parse(http().getJson("/api/v1/books/$bookId/directory"))
    }

    private suspend fun http(): CoreHttp =
        CoreHttp(CoreRuntime.ensureStarted(context.applicationContext))
}