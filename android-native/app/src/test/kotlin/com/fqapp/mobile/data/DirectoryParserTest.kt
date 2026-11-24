package com.fqapp.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryParserTest {

    @Test
    fun `分卷目录取回卷名与章节，缺标题时补章序号`() {
        val payload = JSONObject(
            """
            {"data":{"data":{"chapterListWithVolume":[
              {"volume_name":"第一卷 少年","chapterList":[
                {"item_id":"1001","title":"开端"},
                {"item_id":"1002"}
              ]}
            ]}}}
            """.trimIndent(),
        )

        val volumes = DirectoryParser.parse(payload)

        assertEquals(1, volumes.size)
        assertEquals("第一卷 少年", volumes[0].name)
        assertEquals(listOf("1001", "1002"), volumes[0].chapters.map { it.itemId })
        assertEquals("开端", volumes[0].chapters[0].title)
        assertEquals("第 2 章", volumes[0].chapters[1].title)
    }

    @Test
    fun `data 直接是章节数组时当成单卷`() {
        val payload = JSONObject(
            """{"data":[{"item_id":"a","item_name":"甲"},{"itemId":"b","name":"乙"}]}""",
        )

        val volumes = DirectoryParser.parse(payload)

        assertEquals(1, volumes.size)
        assertEquals("", volumes[0].name)
        assertEquals(listOf("a", "b"), volumes[0].chapters.map { it.itemId })
        assertEquals(listOf("甲", "乙"), volumes[0].chapters.map { it.title })
    }

    @Test
    fun `版本号从原始 item_data_list 补齐`() {
        val payload = JSONObject(
            """
            {"data":{
              "chapterListWithVolume":[
                {"volume_name":"正文","chapterList":[{"item_id":"77","title":"第七章"}]}
              ],
              "item_data_list":[
                {"item_id":"77","version":"20240601","item_name":"第七章"}
              ]
            }}
            """.trimIndent(),
        )

        val chapters = DirectoryParser.parse(payload).single().chapters

        assertEquals("20240601", chapters.single().version)
    }

    @Test
    fun `没有 item_id 的条目被丢掉，空目录返回空列表`() {
        val junk = JSONObject("""{"data":{"item_data_list":[{"title":"没有 id"},{"item_id":"0"}]}}""")
        assertTrue(DirectoryParser.parse(junk).isEmpty())

        val empty = JSONObject("""{"data":{}}""")
        assertTrue(DirectoryParser.parse(empty).isEmpty())
    }
}