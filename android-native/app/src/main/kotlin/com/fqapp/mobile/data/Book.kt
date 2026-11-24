package com.fqapp.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一本书的展示字段。字段名对齐 `lib/models/book_detail.dart` 读的那套上游 JSON，
 * 搜索与详情共用同一份，缺字段就留空。
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val abstract: String,
    val cover: String,
    val category: String,
    val words: String,
    val chapters: String,
    val lastChapter: String,
) {
    companion object {
        fun fromJson(raw: JSONObject): Book {
            val words = raw.optInt("word_number", 0)
            val chapters = raw.optInt("serial_count", raw.optInt("content_chapter_number", 0))
            return Book(
                id = raw.optString("book_id"),
                title = raw.optString("book_name").ifEmpty { raw.optString("title") },
                author = raw.optJSONObject("author_info")?.optString("name")
                    ?: raw.optString("author"),
                abstract = raw.optString("abstract").ifEmpty { raw.optString("description") },
                cover = raw.optString("thumb_url").ifEmpty { raw.optString("detail_page_thumb_url") },
                category = raw.optString("category"),
                words = if (words > 0) formatWords(words) else "",
                chapters = if (chapters > 0) "共 $chapters 章" else "",
                lastChapter = raw.optString("last_chapter_title"),
            )
        }

        private fun formatWords(words: Int): String =
            if (words >= 10_000) "%.1f 万字".format(words / 10_000.0) else "$words 字"

        /** 搜索响应的列表藏在 `books` / `data.books` / `data` 三处之一。 */
        fun listFromSearch(payload: JSONObject): List<Book> {
            val array = payload.optJSONArray("books")
                ?: payload.optJSONObject("data")?.optJSONArray("books")
                ?: payload.optJSONArray("data")
                ?: JSONArray()
            val books = ArrayList<Book>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val book = fromJson(item)
                if (book.title.isNotEmpty()) books.add(book)
            }
            return books
        }

        /** 详情可能是 `{code,data}`、`{data:{data}}` 或裸对象，Dart 版三种都认。 */
        fun fromDetailPayload(payload: JSONObject): Book {
            val data = payload.optJSONObject("data")
            val nested = data?.optJSONObject("data")
            return fromJson(nested ?: data ?: payload)
        }
    }
}