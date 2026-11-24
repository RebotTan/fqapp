package com.fqapp.mobile.data

import org.json.JSONArray
import org.json.JSONObject

/** 书城频道（男生 / 女生 / 有声书 …）。解析失败时返回空表，页面退回「只搜索」。 */
data class Channel(val title: String, val tabType: Int)

object Channels {

    fun parse(payload: JSONObject): List<Channel> {
        val found = LinkedHashMap<Int, Channel>()
        collect(payload, found, 0)
        return found.values.toList()
    }

    private fun collect(value: Any?, out: MutableMap<Int, Channel>, depth: Int) {
        if (depth > 5 || value == null) return
        when (value) {
            is JSONArray -> for (index in 0 until value.length()) collect(value.opt(index), out, depth + 1)
            is JSONObject -> {
                val title = value.optString("title", value.optString("tab_name", ""))
                val type = value.optInt("tab_type", value.optInt("tabType", -1))
                if (title.isNotBlank() && type >= 0) out.putIfAbsent(type, Channel(title, type))
                val names = value.keys()
                while (names.hasNext()) collect(value.opt(names.next()), out, depth + 1)
            }
        }
    }
}