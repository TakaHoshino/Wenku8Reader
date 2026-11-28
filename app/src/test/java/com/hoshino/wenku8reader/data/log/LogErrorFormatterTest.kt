package com.hoshino.wenku8reader.data.log

import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test

class LogErrorFormatterTest {
    @Test
    fun `保留异常链和调用帧`() {
        val formatted = LogErrorFormatter.format(
            IllegalStateException(
                "outer",
                IOException("inner"),
            )
        )

        assertTrue(formatted.contains("IllegalStateException(outer)"))
        assertTrue(formatted.contains("caused by IOException(inner)"))
        assertTrue(formatted.contains("at="))
    }
}
