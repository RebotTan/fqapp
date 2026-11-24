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

    suspend fun search(keyword: String, offset: Int = 0, count: Int = 20): List<Book> =
        withContext(Dispatchers.IO) {
            val payload = http().getJson(
                "/api/v1/search",
                mapOf(
                    "query" to keyword,
                    "offset" to offset.toString(),
                    "count" to count.toString(),
                ),
            )
            Book.listFromSearch(payload)
        }

    suspend fun detail(bookId: String): Book = withContext(Dispatchers.IO) {
        Book.fromDetailPayload(http().getJson("/api/v1/books/$bookId"))
    }

    suspend fun chapterText(itemId: String): String = withContext(Dispatchers.IO) {
        val text = ChapterText.extract(http().getJson("/api/v1/chapters/$itemId"))
        if (text.isEmpty()) throw ApiException("正文为空")
        text
    }

    suspend fun directory(bookId: String): List<ChapterVolume> = withContext(Dispatchers.IO) {
        DirectoryParser.parse(http().getJson("/api/v1/books/$bookId/directory"))
    }

    private suspend fun http(): CoreHttp =
        CoreHttp(CoreRuntime.ensureStarted(context.applicationContext))
}