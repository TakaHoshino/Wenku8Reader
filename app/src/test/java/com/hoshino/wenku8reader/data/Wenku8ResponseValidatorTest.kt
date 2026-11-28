package com.hoshino.wenku8reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Wenku8ResponseValidatorTest {

    @Test
    fun rejectsForbiddenBodyEvenIfStatusWasSuccessful() {
        assertFalse(isUsableWenku8Response(200, "<html><title>403 Forbidden</title></html>"))
        assertFalse(isUsableWenku8Response(200, "<title>Access denied</title>"))
        assertFalse(isUsableWenku8Response(200, "<title>Welcome</title>"))
    }

    @Test
    fun rejectsNonSuccessfulAndChallengeResponses() {
        assertFalse(isUsableWenku8Response(403, "<html>book</html>"))
        assertFalse(isUsableWenku8Response(200, "<title>Just a moment...</title>"))
        assertFalse(isUsableWenku8Response(200, "<div id=\"challenge-form\"></div>"))
    }

    @Test
    fun acceptsNormalBookPage() {
        assertTrue(
            isUsableWenku8Response(
                200,
                "<html><title>书籍详情</title><body>轻小说</body></html>",
            ),
        )
    }

    @Test
    fun normalCloudflareProtectedPageIsNotMistakenForChallenge() {
        val html = """
            <html>
              <head>
                <title>书籍详情</title>
                <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
              </head>
              <body>${"正文".repeat(20_000)}</body>
            </html>
        """.trimIndent()

        assertTrue(isUsableWenku8Response(200, html))
    }

    @Test
    fun detectsContentUrlRedirectedToBareSiteRoot() {
        assertTrue(
            isUnexpectedRootRedirect(
                "https://wenku8.net/book/2902.htm",
                "https://www.wenku8.net/",
            ),
        )
        assertFalse(
            isUnexpectedRootRedirect(
                "https://wenku8.net/book/2902.htm",
                "https://www.wenku8.net/book/2902.htm",
            ),
        )
        assertFalse(
            isUnexpectedRootRedirect("https://wenku8.net/", "https://www.wenku8.net/"),
        )
    }
}
