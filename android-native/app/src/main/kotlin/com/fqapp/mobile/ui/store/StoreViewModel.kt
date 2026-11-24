package com.fqapp.mobile.ui.store

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fqapp.mobile.data.Book
import com.fqapp.mobile.data.CatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CatalogRepository(app)
    private var searchJob: Job? = null

    var keyword by mutableStateOf("斗罗大陆")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<Book>>(emptyList())
        private set

    init {
        search(keyword)
    }

    fun updateKeyword(text: String) {
        keyword = text
    }

    fun search(text: String = keyword) {
        val query = text.trim()
        if (query.isEmpty()) {
            results = emptyList()
            error = "请输入书名或作者"
            return
        }
        keyword = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val found = repository.search(query)
                results = found
                if (found.isEmpty()) error = "没有找到「$query」"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                results = emptyList()
                error = failure.message ?: "搜索失败"
            } finally {
                loading = false
            }
        }
    }
}