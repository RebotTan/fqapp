package com.fqapp.mobile.ui.shelf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.fqapp.mobile.library.LibraryStore
import com.fqapp.mobile.library.ShelfBook
import com.fqapp.mobile.library.SqliteLibraryStore

class ShelfViewModel(app: Application) : AndroidViewModel(app) {
    private val store: LibraryStore = SqliteLibraryStore(app)

    var books by mutableStateOf<List<ShelfBook>>(emptyList())
        private set

    /** 进书架、或者从详情页返回时都要重新读一次，书架内容就该是最新的。 */
    fun refresh() {
        books = store.recentBooks()
    }

    fun remove(bookId: String) {
        store.removeBook(bookId)
        refresh()
    }
}