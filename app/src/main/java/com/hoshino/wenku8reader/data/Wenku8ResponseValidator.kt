package com.hoshino.wenku8reader.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cloudflare inserts `challenge-platform` script references into ordinary protected pages too,
 * so only title/DOM markers are reliable. Keep the probe bounded so a large chapter is cheap.
 */
internal fun isWenku8ChallengePage(html: String): Boolean {
    val probe = html.take(32 * 1024)
    val lower = probe.lowercase()
    val challengeTitle = Regex(
        """<title[^>]*>\s*(just a moment|checking your browser|稍等片刻|security checkpoint|""" +
            """we're verifying your browser|enable javascript and cookies)""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(probe)
    val challengeMarkup = listOf(
        "challenge-form",
        "cf-challenge-running",
        "cf-turnstile",
        "cf-chl-",
    ).any { marker ->
        lower.contains("id=\"$marker") ||
            lower.contains("id='$marker") ||
            lower.contains("class=\"$marker") ||
            lower.contains("class='$marker")
    }
    return challengeTitle || challengeMarkup
}

/** A successful HTTP response can still carry Cloudflare's HTML error page. */
internal fun isWenku8BlockedPage(html: String): Boolean {
    if (isWenku8ChallengePage(html)) return true
    val probe = html.take(32 * 1024)
    return Regex(
        """<title[^>]*>\s*(403 forbidden|access denied|welcome)\s*</title>""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(probe)
}

/** Only a 2xx response with usable HTML may enter a business parser. */
internal fun isUsableWenku8Response(statusCode: Int, html: String): Boolean =
    statusCode in 200..299 && html.isNotBlank() && !isWenku8BlockedPage(html)

/**
 * A bare host such as `https://wenku8.net` redirects to its site root even for a chapter URL.
 * Treat that root landing page as an invalid payload instead of parsing "Welcome" as a chapter.
 */
internal fun isUnexpectedRootRedirect(requestedUrl: String, finalUrl: String): Boolean {
    val requested = requestedUrl.toHttpUrlOrNull() ?: return false
    val final = finalUrl.toHttpUrlOrNull() ?: return false
    return requested.encodedPath != "/" && final.encodedPath == "/"
}
