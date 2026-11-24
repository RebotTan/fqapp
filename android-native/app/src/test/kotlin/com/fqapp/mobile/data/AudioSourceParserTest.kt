package com.fqapp.mobile.data

import com.fqapp.mobile.core.ApiException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioSourceParserTest {

    @Test
    fun `playinfo 的 video_model 里取播放地址与时长`() {
        val model = """{"video_list":[{"main_url":"https://cdn.example.com/a.m4a"}],"video_duration":664.74}"""
        val payload = JSONObject(
            """{"data":[{"item_id":"c1","video_model":${JSONObject.quote(model)}}]}""",
        )

        val source = AudioSourceParser.parse(payload, itemId = "c1")

        assertEquals("https://cdn.example.com/a.m4a", source.url)
        assertEquals(664.74, source.durationSeconds!!, 0.001)
    }

    @Test
    fun `老接口直接给 main_url`() {
        val payload = JSONObject("""{"data":[{"item_id":"c1","main_url":"https://cdn.example.com/b.mp3"}]}""")

        val source = AudioSourceParser.parse(payload, itemId = "c1")

        assertEquals("https://cdn.example.com/b.mp3", source.url)
        assertNull(source.durationSeconds)
    }

    @Test
    fun `上游业务错误带出原文提示`() {
        val payload = JSONObject("""{"data":[{"item_id":"c1","code":-1,"message":"该章暂无音频"}]}""")

        val error = runCatching { AudioSourceParser.parse(payload, itemId = "c1") }.exceptionOrNull()

        assertEquals("该章暂无音频", (error as ApiException).message)
    }

    @Test
    fun `没有可用地址时给出明确失败`() {
        val error = runCatching {
            AudioSourceParser.parse(JSONObject("""{"data":[{"item_id":"c1"}]}"""), itemId = "c1")
        }.exceptionOrNull()

        assertEquals("未获取到音频地址", (error as ApiException).message)
    }
}