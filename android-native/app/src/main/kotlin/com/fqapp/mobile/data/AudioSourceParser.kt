package com.fqapp.mobile.data

import com.fqapp.mobile.core.ApiException
import org.json.JSONArray
import org.json.JSONObject

/**
 * 听书地址解析。官方 playinfo 把播放模型塞在 `video_model` 字符串里，
 * 老接口直接给 `main_url`；两种都要认。
 */
object AudioSourceParser {

    data class Source(val url: String, val durationSeconds: Double?)

    fun parse(payload: JSONObject, itemId: String = ""): Source {
        for (row in rows(payload)) {
            if (itemId.isNotEmpty()) {
                val id = row.optString("item_id", itemId)
                if (id.isNotEmpty() && id != itemId) continue
            }
            val code = row.optInt("code", 0)
            if (code != 0 && code != 200) {
                throw ApiException(row.optString("message").ifBlank { "音频资源暂不可用" })
            }
            val model = row.optString("video_model")
            if (model.isNotBlank()) {
                fromModel(model)?.let { return it }
            }
            val direct = firstUrl(row)
            if (direct != null) return Source(direct, null)
        }
        throw ApiException("未获取到音频地址")
    }

    private fun fromModel(raw: String): Source? {
        val model = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return null
        }
        val streams = model.optJSONArray("video_list") ?: return null
        for (index in 0 until streams.length()) {
            val stream = streams.optJSONObject(index) ?: continue
            val url = firstUrl(stream) ?: continue
            val duration = model.optDouble("video_duration", 0.0)
            return Source(url, if (duration.isFinite() && duration > 0) duration else null)
        }
        return null
    }

    private fun firstUrl(row: JSONObject): String? =
        listOf("main_url", "backup_url").firstNotNullOfOrNull { key ->
            row.optString(key).takeIf { it.startsWith("http") }
        }

    /** 找出所有「媒体行」：带 video_model 或有播放地址的对象。 */
    private fun rows(payload: JSONObject): List<JSONObject> {
        val found = ArrayList<JSONObject>()
        fun visit(value: Any?, depth: Int) {
            if (depth > 6 || value == null) return
            when (value) {
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index), depth + 1)
                is JSONObject -> {
                    // 带 code 的行也可能是「该章无音频」这类业务错误，要能带出原文提示。
                    if (value.has("video_model") || firstUrl(value) != null || value.has("code")) {
                        found.add(value)
                    }
                    val names = value.keys()
                    while (names.hasNext()) visit(value.opt(names.next()), depth + 1)
                }
            }
        }
        visit(payload, 0)
        return found
    }
}