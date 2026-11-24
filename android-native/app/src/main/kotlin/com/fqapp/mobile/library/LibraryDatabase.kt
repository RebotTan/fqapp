package com.fqapp.mobile.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * `library.db` 的唯一持有者：书架、进度、正文缓存三张表共用一个连接，
 * 否则两个 helper 会对同一个库文件各建各的表。
 */
class LibraryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE shelf (
                book_id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE progress (
                book_id TEXT PRIMARY KEY NOT NULL,
                item_id TEXT NOT NULL,
                chapter_title TEXT NOT NULL DEFAULT '',
                chapter_index INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createChapters(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 书架是用户数据，只加表不 drop。
        if (oldVersion < 2) createChapters(db)
    }

    private fun createChapters(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chapters (
                book_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (book_id, item_id)
            )
            """.trimIndent(),
        )
    }

    companion object {
        const val NAME = "library.db"
        const val VERSION = 2

        @Volatile
        private var instance: LibraryDatabase? = null

        fun of(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: LibraryDatabase(context.applicationContext).also { instance = it }
            }
    }
}