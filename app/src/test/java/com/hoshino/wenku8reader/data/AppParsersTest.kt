package com.hoshino.wenku8reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppParsersTest {

    @Test
    fun appChapter_rejectsLandingPageWithTitleOnly() {
        assertNull(AppParsers.parseAppChapter("Welcome"))
    }

    @Test
    fun appChapter_acceptsTitleAndBody() {
        val chapter = AppParsers.parseAppChapter("第一章\n正文第一段\n正文第二段")

        assertEquals("第一章", chapter?.title)
        assertEquals("正文第一段\n正文第二段", chapter?.text)
    }
}
