package com.fqapp.mobile.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginatorTest {

    @Test
    fun `按页高切行，页间不重不漏`() {
        // 四行，每行高 20，页高 45 → 每页两行。
        val starts = intArrayOf(0, 10, 20, 30)
        val tops = floatArrayOf(0f, 20f, 40f, 60f)
        val bottoms = floatArrayOf(20f, 40f, 60f, 80f)

        val pages = ReaderPaginator.pageRanges(starts, tops, bottoms, 40, 45f)

        assertEquals(listOf(0 until 20, 20..39), pages)
    }

    @Test
    fun `内容不足一页时只有一页`() {
        val pages = ReaderPaginator.pageRanges(
            intArrayOf(0, 5),
            floatArrayOf(0f, 20f),
            floatArrayOf(20f, 40f),
            9,
            500f,
        )

        assertEquals(listOf(0..8), pages)
    }

    @Test
    fun `单行高于页高也至少前进一行`() {
        val pages = ReaderPaginator.pageRanges(
            intArrayOf(0, 3, 6),
            floatArrayOf(0f, 100f, 200f),
            floatArrayOf(100f, 200f, 300f),
            9,
            50f,
        )

        assertEquals(listOf(0..2, 3..5, 6..8), pages)
    }

    @Test
    fun `空正文没有任何页`() {
        assertTrue(ReaderPaginator.pageRanges(intArrayOf(), floatArrayOf(), floatArrayOf(), 0, 500f).isEmpty())
    }
}