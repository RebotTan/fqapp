package com.fqapp.mobile.library

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import com.fqapp.mobile.data.Book
import com.fqapp.mobile.data.ChapterRef

/** 书架上的一本书，附带「读到哪」；没读过时 progress 为 null。 */
data class ShelfBook(
    val bookId: String,
    val title: String,
    val author: String,
    val updatedAt: Long,
    val progress: ReadingProgress?,
)

data class ReadingProgress(
    val itemId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val updatedAt: Long,
)

/**
 * 本地书架与阅读进度。P2 只落这两张表；章节正文缓存另开一张表留给 P3，
 * 避免缓存体量与「我的书」这类小数据抢事务。
 */
interface LibraryStore {
    fun touchBook(book: Book, now: Long = System.currentTimeMillis())

    fun saveProgress(
        bookId: String,
        chapter: ChapterRef,
        chapterIndex: Int,
        now: Long = System.currentTimeMillis(),
    )

    fun recentBooks(limit: Int = 100): List<ShelfBook>

    fun progressOf(bookId: String): ReadingProgress?

    fun removeBook(bookId: String)
}

class SqliteLibraryStore(private val database: LibraryDatabase) : LibraryStore {

    constructor(context: Context) : this(LibraryDatabase.of(context))

    override fun touchBook(book: Book, now: Long) {
        val values = ContentValues().apply {
            put("book_id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("updated_at", now)
        }
        database.writableDatabase.insertWithOnConflict(
            "shelf",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun saveProgress(bookId: String, chapter: ChapterRef, chapterIndex: Int, now: Long) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "progress",
                null,
                ContentValues().apply {
                    put("book_id", bookId)
                    put("item_id", chapter.itemId)
                    put("chapter_title", chapter.title)
                    put("chapter_index", chapterIndex)
                    put("updated_at", now)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.execSQL("UPDATE shelf SET updated_at = ? WHERE book_id = ?", arrayOf<Any>(now, bookId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun recentBooks(limit: Int): List<ShelfBook> {
        val sql = """
            SELECT s.book_id, s.title, s.author, s.updated_at,
                   p.item_id, p.chapter_title, p.chapter_index, p.updated_at
            FROM shelf s
            LEFT JOIN progress p ON p.book_id = s.book_id
            ORDER BY s.updated_at DESC
            LIMIT ?
        """.trimIndent()
        val books = ArrayList<ShelfBook>()
        database.readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val progress = if (cursor.isNull(4)) {
                    null
                } else {
                    ReadingProgress(
                        itemId = cursor.getString(4),
                        chapterTitle = cursor.getString(5),
                        chapterIndex = cursor.getInt(6),
                        updatedAt = cursor.getLong(7),
                    )
                }
                books.add(
                    ShelfBook(
                        bookId = cursor.getString(0),
                        title = cursor.getString(1),
                        author = cursor.getString(2),
                        updatedAt = cursor.getLong(3),
                        progress = progress,
                    ),
                )
            }
        }
        return books
    }

    override fun progressOf(bookId: String): ReadingProgress? =
        database.readableDatabase.rawQuery(
            "SELECT item_id, chapter_title, chapter_index, updated_at FROM progress WHERE book_id = ?",
            arrayOf(bookId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            ReadingProgress(
                itemId = cursor.getString(0),
                chapterTitle = cursor.getString(1),
                chapterIndex = cursor.getInt(2),
                updatedAt = cursor.getLong(3),
            )
        }

    override fun removeBook(bookId: String) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("progress", "book_id = ?", arrayOf(bookId))
            db.delete("shelf", "book_id = ?", arrayOf(bookId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
