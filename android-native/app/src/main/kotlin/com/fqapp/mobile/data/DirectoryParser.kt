package com.fqapp.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/** 目录里的一章。`version` 是正文版本号，P3 抓正文时要带上游那句话。 */
data class ChapterRef(
    val itemId: String,
    val title: String,
    val version: String,
)

data class ChapterVolume(
    val name: String,
    val chapters: List<ChapterRef>,
)

/**
 * 目录响应解析。形状比 Dart 版少一半分支，但覆盖上游实际会返回的三种：
 * `data.data.chapterListWithVolume`（分卷）、`data` 直接是章节数组、以及
 * 兜底的 `item_data_list` / `episodes` / `lists` / `item_list`。
 */
object DirectoryParser {
    private val idKeys = listOf(
        "item_id", "itemId", "chapter_id", "video_id", "vid", "episode_id", "id",
    )
    private val titleKeys = listOf(
        "item_name", "title", "chapter_title", "name", "chapter_name",
    )
    private val entryKeys = listOf(
        "episodes", "item_data_list", "lists", "item_list",
    )
    private val volumeChapterKeys = listOf(
        "chapterList", "chapter_list", "item_data_list", "lists", "item_list",
    )

    fun parse(payload: JSONObject): List<ChapterVolume> {
        val versions = versions(payload)
        val data = payload.optJSONObject("data")
        val inner = data?.optJSONObject("data") ?: data ?: payload

        val flat = data?.optJSONArray("data") ?: payload.optJSONArray("data")
        if (flat != null) return single("", chaptersOf(flat, versions))

        val volumes = inner.optJSONArray("chapterListWithVolume")
        if (volumes != null) {
            val parsed = ArrayList<ChapterVolume>(volumes.length())
            for (index in 0 until volumes.length()) {
                val volume = volumes.optJSONObject(index) ?: continue
                val name = volume.optString("volume_name").ifBlank { volume.optString("volumeName") }
                val chapters = volumeChapterKeys
                    .firstNotNullOfOrNull { key -> volume.optJSONArray(key)?.takeIf { it.length() > 0 } }
                    ?.let { chaptersOf(it, versions) }
                    ?: emptyList()
                if (chapters.isNotEmpty()) parsed.add(ChapterVolume(name, chapters))
            }
            if (parsed.isNotEmpty()) return parsed
        }

        val entries = findEntries(inner) ?: return emptyList()
        return single("", chaptersOf(entries, versions))
    }

    private fun single(name: String, chapters: List<ChapterRef>): List<ChapterVolume> =
        if (chapters.isEmpty()) emptyList() else listOf(ChapterVolume(name, chapters))

    private fun chaptersOf(
        array: JSONArray,
        versions: Map<String, String>,
    ): List<ChapterRef> {
        val out = ArrayList<ChapterRef>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.firstNonBlank(idKeys) ?: continue
            val title = entry.firstNonBlank(titleKeys) ?: "第 ${index + 1} 章"
            out.add(ChapterRef(id, title, entry.optString("version").ifBlank { versions[id].orEmpty() }))
        }
        return out
    }

    private fun JSONObject.firstNonBlank(keys: List<String>): String? = keys
        .firstNotNullOfOrNull { key -> optString(key).trim().takeIf { it.isNotEmpty() && it != "0" } }

    /** 正文版本只出现在原始 `item_data_list` 里，归一化后的章节条目没有。 */
    private fun versions(value: JSONObject, depth: Int = 0): Map<String, String> {
        val found = HashMap<String, String>()
        if (depth > 6) return found
        val raw = value.optJSONArray("item_data_list")
        if (raw != null) {
            for (index in 0 until raw.length()) {
                val entry = raw.optJSONObject(index) ?: continue
                val id = entry.firstNonBlank(listOf("item_id", "itemId")) ?: continue
                val version = entry.optString("version").trim()
                if (version.isNotEmpty()) found[id] = version
            }
            if (found.isNotEmpty()) return found
        }
        value.keys().forEach { key ->
            value.optJSONObject(key)?.let { found.putAll(versions(it, depth + 1)) }
        }
        return found
    }

    private fun findEntries(value: JSONObject, depth: Int = 0): JSONArray? {
        if (depth > 5) return null
        for (key in entryKeys) {
            val array = value.optJSONArray(key)
            if (array != null && array.length() > 0) return array
        }
        value.keys().forEach { key ->
            val nested = value.optJSONObject(key) ?: return@forEach
            findEntries(nested, depth + 1)?.let { return it }
        }
        return null
    }
}