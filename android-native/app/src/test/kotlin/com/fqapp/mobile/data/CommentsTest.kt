package com.fqapp.mobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsTest {

    @Test
    fun `解析书评正文、评分、用户与配图`() {
        val payload = JSONObject(
            """{"data":{"comment":[
                 {"id":"1","text":"很好看","score":9,"digg_count":12,"reply_count":2,
                  "user_info":{"name":"读者甲","avatar_url":"https://img/a.png"},
                  "image_url":["https://img/1.jpg",{"web_url":"https://img/2.jpg"}]},
                 {"id":"2","text":"  ","image_url":[]}
               ]}}""",
        )

        val comments = Comments.parse(payload)

        assertEquals(1, comments.size)
        val comment = comments.first()
        assertEquals("很好看", comment.text)
        assertEquals(9, comment.score)
        assertEquals("读者甲", comment.userName)
        assertEquals(listOf("https://img/1.jpg", "https://img/2.jpg"), comment.images)
    }

    @Test
    fun `只有图片没有文字的评论也要保留`() {
        val payload = JSONObject(
            """{"data":{"comment":[{"id":"3","text":"","image_url":["https://img/3.jpg"]}]}}""",
        )

        assertEquals(listOf("https://img/3.jpg"), Comments.parse(payload).first().images)
    }

    @Test
    fun `没有评论时返回空表`() {
        assertTrue(Comments.parse(JSONObject("""{"data":{}}""")).isEmpty())
    }
}