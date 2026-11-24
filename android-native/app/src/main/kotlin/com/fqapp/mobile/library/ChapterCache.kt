package com.fqapp.mobile.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fqapp.mobile.data.ChapterRef

/**
 * 已下载的正文。命中就不再联网，这也是「整本下载」的落地形态：
 * 书架上的书把每章正文写进这张表，之后完全离线可读。
 */
interface ChapterCache {
    fun read(bookId: String, itemId: String): String?

    fun write(bookId: String, chapter: ChapterRef, body: String, now: Long = System.currentTimeMillis())

    fun downloadedCount(bookId: String): Int

    fun downloadedIds(bookId: String): Set<String>

    fun removeBook(bookId: String)
}

class SqliteChapterCache(context: Context) : ChapterCache {

    private val database = LibraryDatabase.of(context)

    override fun read(bookId: String, itemId: String): String? =
        database.readableDatabase.rawQuery(
            "SELECT body FROM chapters WHERE book_id = ? AND item_id = ?",
            arrayOf(bookId, itemId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    override fun write(bookId: String, chapter: ChapterRef, body: String, now: Long) {
        if (body.isBlank()) return
        database.writableDatabase.insertWithOnConflict(
            "chapters",
            null,
            ContentValues().apply {
                put("book_id", bookId)
                put("item_id", chapter.itemId)
                put("title", chapter.title)
                put("body", body)
                put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun downloadedCount(bookId: String): Int =
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM chapters WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    override fun downloadedIds(bookId: String): Set<String> {
        val ids = HashSet<String>()
        database.readableDatabase.rawQuery(
            "SELECT item_id FROM chapters WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getString(0))
        }
        return ids
    }

    override fun removeBook(bookId: String) {
        database.writableDatabase.delete("chapters", "book_id = ?", arrayOf(bookId))
    }
}