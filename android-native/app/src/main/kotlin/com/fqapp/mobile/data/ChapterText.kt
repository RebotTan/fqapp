package com.fqapp.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 正文提取与清洗。上游接口把正文塞在 `content` / `text` / `article_content` / `body`
 * 之一，层级不固定，所以递归找第一个非空字符串；拿到的往往是带标签的 HTML 片段。
 */
object ChapterText {
    private val textKeys = listOf("content", "text", "article_content", "body")
    private val breakTags = Regex("<\\s*(br|/p|/div|/h[1-6])\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("<[^>]*>")
    private val entities = mapOf(
        "&nbsp;" to " ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&amp;" to "&",
    )
    private val blankLine = Regex("\n{3,}")

    fun extract(payload: JSONObject): String = visit(payload, 0)

    private fun visit(value: Any?, depth: Int): String {
        if (depth > 7 || value == null) return ""
        when (value) {
            is JSONObject -> {
                for (key in textKeys) {
                    val candidate = value.optString(key)
                    if (candidate.isNotBlank()) return normalize(candidate)
                }
                val names = value.keys()
                while (names.hasNext()) {
                    val found = visit(value.opt(names.next()), depth + 1)
                    if (found.isNotEmpty()) return found
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val found = visit(value.opt(index), depth + 1)
                    if (found.isNotEmpty()) return found
                }
            }
        }
        return ""
    }

    /** 标签换成换行、实体还原、行内空白收拢、连续空行压成一个。 */
    fun normalize(raw: String): String {
        var text = breakTags.replace(raw, "\n")
        text = anyTag.replace(text, "")
        for ((entity, plain) in entities) text = text.replace(entity, plain)
        val lines = text.split('\n').map { it.replace('\u00A0', ' ').trim() }
        return blankLine.replace(lines.joinToString("\n"), "\n\n").trim()
    }
}