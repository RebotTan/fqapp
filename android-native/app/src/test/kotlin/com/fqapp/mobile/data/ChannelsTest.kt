package com.fqapp.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {

    @Test
    fun `从嵌套结构里收集频道并按 tab_type 去重`() {
        val payload = JSONObject(
            """{"data":{"tabs":[{"title":"男生","tab_type":2},{"title":"女生","tab_type":3},
               {"tab_name":"有声书","tabType":8},{"title":"男生","tab_type":2}]}}""",
        )

        val channels = Channels.parse(payload)

        assertEquals(listOf("男生", "女生", "有声书"), channels.map { it.title })
        assertEquals(listOf(2, 3, 8), channels.map { it.tabType })
    }

    @Test
    fun `没有频道时返回空表而不是抛错`() {
        assertTrue(Channels.parse(JSONObject("""{"data":{}}""")).isEmpty())
    }
}