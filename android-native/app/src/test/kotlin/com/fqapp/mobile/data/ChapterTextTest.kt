package com.fqapp.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTextTest {

    @Test
    fun `顶层 content 直接取用`() {
        val payload = JSONObject("""{"code":0,"data":{"content":"<p>第一段</p><p>第二段</p>"}}""")

        assertEquals("第一段\n第二段", ChapterText.extract(payload))
    }

    @Test
    fun `正文藏在更深层也能找到`() {
        val payload = JSONObject("""{"data":{"chapter":{"text":"<div>深处正文</div>"}}}""")

        assertEquals("深处正文", ChapterText.extract(payload))
    }

    @Test
    fun `空正文继续往下找`() {
        val payload = JSONObject("""{"data":{"content":"   ","article_content":"真实正文"}}""")

        assertEquals("真实正文", ChapterText.extract(payload))
    }

    @Test
    fun `标签换行与实体还原`() {
        val raw = "<p>甲&nbsp;乙</p><br><br><p>丙 &amp; 丁</p>"

        val text = ChapterText.normalize(raw)

        assertEquals("甲 乙\n\n丙 & 丁", text)
    }

    @Test
    fun `完全找不到正文时返回空串`() {
        val payload = JSONObject("""{"data":{"item_id":"123"}}""")

        assertTrue(ChapterText.extract(payload).isEmpty())
    }
}