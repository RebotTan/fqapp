package com.fqapp.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/** 一条书评。图片是上游 `image_url[]`（元素可能是字符串或带 `web_url` 的对象）。 */
data class BookComment(
    val id: String,
    val text: String,
    val score: Int,
    val diggCount: Int,
    val replyCount: Int,
    val userName: String,
    val avatar: String,
    val images: List<String>,
)

object Comments {

    fun parse(payload: JSONObject): List<BookComment> {
        val data = resolveData(payload) ?: return emptyList()
        val raw = data.optJSONArray("comment") ?: data.optJSONArray("comments") ?: return emptyList()
        val list = ArrayList<BookComment>()
        for (index in 0 until raw.length()) {
            val entry = raw.optJSONObject(index) ?: continue
            val text = entry.optString("text", entry.optString("content")).trim()
            val images = imagesOf(entry.opt("image_url"))
            if (text.isEmpty() && images.isEmpty()) continue
            val user = entry.optJSONObject("user_info") ?: entry.optJSONObject("user")
            list.add(
                BookComment(
                    id = entry.optString("id", entry.optString("comment_id")),
                    text = text,
                    score = entry.optInt("score", 0),
                    diggCount = entry.optInt("digg_count", 0),
                    replyCount = entry.optInt("reply_count", 0),
                    userName = user?.optString("name", user.optString("user_name")).orEmpty(),
                    avatar = user?.optString("avatar_url", user.optString("avatar")).orEmpty(),
                    images = images,
                ),
            )
        }
        return list
    }

    private fun resolveData(payload: JSONObject): JSONObject? {
        val data = payload.optJSONObject("data") ?: return payload
        return data.optJSONObject("data") ?: data
    }

    fun imagesOf(value: Any?): List<String> {
        val array = value as? JSONArray ?: return emptyList()
        val urls = ArrayList<String>()
        for (index in 0 until array.length()) {
            val url = when (val item = array.opt(index)) {
                is String -> item
                is JSONObject -> item.optString("web_url", item.optString("url"))
                else -> ""
            }.trim()
            if (url.startsWith("http")) urls.add(url)
        }
        return urls
    }
}