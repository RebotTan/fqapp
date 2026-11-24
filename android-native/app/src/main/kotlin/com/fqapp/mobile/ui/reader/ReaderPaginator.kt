package com.fqapp.mobile.ui.reader

/**
 * 章内分页。行高与偏移由排版层给出（Compose 的 `TextLayoutResult`），这里只负责
 * 「一屏能装几行」的切分，以便脱离排版单独验证。
 */
object ReaderPaginator {

    /**
     * @param lineStarts 每行起始字符下标
     * @param lineTops 每行顶部 y
     * @param lineBottoms 每行底部 y
     * @param textLength 正文字符数
     * @param pageHeight 一页可用高度
     * @return 每页覆盖的字符区间（左闭右开）
     */
    fun pageRanges(
        lineStarts: IntArray,
        lineTops: FloatArray,
        lineBottoms: FloatArray,
        textLength: Int,
        pageHeight: Float,
    ): List<IntRange> {
        if (textLength <= 0 || lineStarts.isEmpty()) return emptyList()
        val pages = ArrayList<IntRange>()
        var firstLine = 0
        var pageTop = lineTops[0]
        for (line in lineStarts.indices) {
            if (lineBottoms[line] - pageTop <= pageHeight || line == firstLine) continue
            pages.add(lineStarts[firstLine] until lineStarts[line])
            firstLine = line
            pageTop = lineTops[line]
        }
        pages.add(lineStarts[firstLine] until textLength)
        return pages
    }
}