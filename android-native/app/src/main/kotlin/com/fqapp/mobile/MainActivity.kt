package com.fqapp.mobile

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fqapp.mobile.core.CoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * P0 冒烟页：证明 JNI + loopback + capability 整条链在原生侧能通。
 * P1 起换成 Compose 的第一屏。
 */
class MainActivity : ComponentActivity() {
    private val rows = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        lifecycleScope.launch {
            status.text = "正在启动核心…"
            val handle = runCatching { CoreRuntime.ensureStarted(applicationContext) }
                .getOrElse { error ->
                    status.text = "核心启动失败：${error.message}"
                    return@launch
                }
            status.text = "核心就绪 ${handle.baseUrl}"
            val body = withContext(Dispatchers.IO) { fetchHot(handle) }
            status.text = "${handle.baseUrl}｜${body.statusLine}"
            rows.clear()
            rows.addAll(body.rows)
            adapter.notifyDataSetChanged()
        }
    }

    private fun buildLayout(): LinearLayout {
        status = TextView(this).apply {
            text = "正在启动核心…"
            setPadding(48, 48, 48, 24)
            textSize = 13f
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val list = ListView(this).apply { adapter = this@MainActivity.adapter }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun fetchHot(handle: CoreRuntime.Handle): HotResult {
        val request = Request.Builder()
            .url("${handle.baseUrl}/api/v1/search/hot")
            .header("Authorization", "Bearer ${handle.capability}")
            .build()
        return runCatching {
            CoreRuntime.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                HotResult(
                    statusLine = "GET /api/v1/search/hot → ${response.code}",
                    rows = rowsFrom(text),
                )
            }
        }.getOrElse { error ->
            HotResult(statusLine = "请求失败：${error.message}", rows = emptyList())
        }
    }

    /** 结构未知时把原文按行摊开，至少能看见拿回了什么。 */
    private fun rowsFrom(body: String): List<String> {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
            ?: runCatching { JSONArray(body) }.getOrNull()
        if (data != null) {
            val titles = ArrayList<String>(data.length())
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val label = listOf("word", "title", "name", "book_name")
                    .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
                if (label != null) titles.add(label)
            }
            if (titles.isNotEmpty()) return titles
        }
        return body.lines().filter { it.isNotBlank() }.take(200)
    }

    private data class HotResult(val statusLine: String, val rows: List<String>)
}