package com.hoshino.wenku8reader.apicore

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Wenku8 v2 原始 Action 查询构造器。
 *
 * 这些字符串不是最终 HTTP 查询参数：它们必须先由 [WenkuEnvelopeSigner] 封装成
 * `WK8-RNE-1` 请求体，再统一 POST 到 [V2_API_URL]。
 */
object WenkuActionBuilder {
    const val BASE_URL: String = "https://mewx-android.wenku8.app/"
    const val V2_API_URL: String = "https://mewx-android.wenku8.app/v2/api"

    enum class Language(val queryValue: Int) {
        SC(0),
        TC(1),
    }

    enum class Sort(val wireName: String) {
        ALL_VISIT("allvisit"),
        ALL_VOTE("allvote"),
        MONTH_VISIT("monthvisit"),
        MONTH_VOTE("monthvote"),
        WEEK_VISIT("weekvisit"),
        WEEK_VOTE("weekvote"),
        DAY_VISIT("dayvisit"),
        DAY_VOTE("dayvote"),
        POST_DATE("postdate"),
        LAST_UPDATE("lastupdate"),
        GOOD_NUM("goodnum"),
        SIZE("size"),
        FULL_FLAG("fullflag"),
    }

    fun novelListWithInfo(
        sortedBy: Sort,
        page: Int,
        language: Language = Language.SC,
    ): String {
        requirePositive("page", page)
        return "action=novellist&sort=${sortedBy.wireName}&page=$page&t=${language.queryValue}"
    }

    fun searchByNovelName(
        query: String,
        language: Language = Language.SC,
    ): String = search(
        searchType = "articlename",
        query = query,
        language = language,
    )

    fun searchByAuthorName(
        query: String,
        language: Language = Language.SC,
    ): String = search(
        searchType = "author",
        query = query,
        language = language,
    )

    fun novelShortInfo(aid: Int, language: Language = Language.SC): String {
        requirePositive("aid", aid)
        return "action=book&do=bookinfo&aid=$aid&t=${language.queryValue}"
    }

    fun novelFullMeta(aid: Int, language: Language = Language.SC): String {
        requirePositive("aid", aid)
        return "action=book&do=meta&aid=$aid&t=${language.queryValue}"
    }

    fun novelFullIntro(aid: Int, language: Language = Language.SC): String {
        requirePositive("aid", aid)
        return "action=book&do=intro&aid=$aid&t=${language.queryValue}"
    }

    fun novelVolumeToc(aid: Int, language: Language = Language.SC): String {
        requirePositive("aid", aid)
        return "action=book&do=list&aid=$aid&t=${language.queryValue}"
    }

    fun chapterContent(
        aid: Int,
        cid: Int,
        language: Language = Language.SC,
    ): String {
        requirePositive("aid", aid)
        requirePositive("cid", cid)
        return "action=book&do=text&aid=$aid&cid=$cid&t=${language.queryValue}"
    }

    fun bookshelf(language: Language = Language.SC): String =
        "action=bookcase&t=${language.queryValue}"

    fun bookshelfAidList(language: Language = Language.SC): String =
        "action=bookcase&do=list&t=${language.queryValue}"

    fun addToBookshelf(aid: Int): String {
        requirePositive("aid", aid)
        return "action=bookcase&do=add&aid=$aid"
    }

    fun removeFromBookshelf(aid: Int): String {
        requirePositive("aid", aid)
        return "action=bookcase&do=del&aid=$aid"
    }

    fun reviewThreads(
        aid: Int,
        page: Int,
        language: Language = Language.SC,
    ): String {
        requirePositive("aid", aid)
        requirePositive("page", page)
        return "action=review&do=list&aid=$aid&page=$page&t=${language.queryValue}"
    }

    fun reviewReplies(rid: Int, page: Int): String {
        requirePositive("rid", rid)
        requirePositive("page", page)
        return "action=review&do=show&rid=$rid&page=$page"
    }

    fun newReviewThread(aid: Int, title: String, content: String): String {
        requirePositive("aid", aid)
        require(title.isNotBlank()) { "title must not be blank" }
        require(content.isNotBlank()) { "content must not be blank" }
        return buildString {
            append("action=review&do=post&aid=")
            append(aid)
            append("&title=")
            append(encodeQueryValue(title))
            append("&content=")
            append(encodeQueryValue(content))
        }
    }

    fun replyToReview(rid: Int, content: String): String {
        requirePositive("rid", rid)
        require(content.isNotBlank()) { "content must not be blank" }
        return "action=review&do=reply&rid=$rid&content=${encodeQueryValue(content)}"
    }

    fun userInfo(): String = "action=userinfo"

    fun dailySign(): String = "action=block&do=sign"

    fun logout(): String = "action=logout"

    internal fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun search(
        searchType: String,
        query: String,
        language: Language,
    ): String {
        require(query.isNotBlank()) { "query must not be blank" }
        return "action=search&searchtype=$searchType&searchkey=" +
            "${encodeQueryValue(query)}&t=${language.queryValue}"
    }

    private fun requirePositive(name: String, value: Int) {
        require(value > 0) { "$name must be greater than zero" }
    }
}
