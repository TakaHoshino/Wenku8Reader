package com.hoshino.wenku8reader.data.local

import org.json.JSONArray

/**
 * 多书架的**纯逻辑**：名称校验、清单增删改、归属兜底。
 *
 * 抽成顶层纯函数的原因与 `staleProgressBookIds` 相同：这些判断直接决定
 * 「用户的书会不会凭空消失」和「书架会不会重名/删空」，一旦埋进 ViewModel
 * 就只能靠人工点按验证。这里用 `ShelfOpsTest` 把边界钉死。
 *
 * ### 关键约定：默认书架是**隐式**的
 *
 * 持久化的清单里**只存用户自建的书架**；展示时恒为 `[DEFAULT_SHELF] + 自建清单`
 * （见 [shelfNames]）。于是"默认不可删除、不可改名、永远至少有一个书架"由结构保证，
 * 不需要额外的禁用态判断，也天然满足「禁止删除最后一个书架」这条需求。
 *
 * ### 一本书可以同时在多个书架（多选）
 *
 * 书的归属是**集合**而不是单个名字，持久化在 `books.shelf` 这一列里（JSON 数组，
 * 见 [encodeMembership] / [shelvesOf]）。阅读进度不在这条链路上——它按 `bookId` 存在
 * `reading_progress` 表里，所以同一本书出现在多个书架时**共用同一份进度**。
 */

/** 默认书架名。沿用旧数据的字面量 `"默认"`，保证老收藏零迁移。 */
internal const val DEFAULT_SHELF = "默认"

/** 书架名长度上限（同时约束 UI 排版与持久化体积）。 */
internal const val SHELF_NAME_MAX_LENGTH = 20

/** 名称不可用的原因——给用户具体提示，而不是笼统一句"失败"。 */
internal enum class ShelfNameError {
    /** 去掉首尾空白后为空。 */
    EMPTY,

    /** 超过 [SHELF_NAME_MAX_LENGTH]。 */
    TOO_LONG,

    /** 与已有书架重名（默认书架永远算占用）。 */
    DUPLICATE,
}

/**
 * 归一化名称：**只去首尾空白**。
 *
 * 内部空格（如「轻 小说」）保留——那是用户有意的命名。全角空格不会被 trim，
 * 与用户直觉一致（看起来是空的名字才拒绝）。
 */
internal fun normalizeShelfName(raw: String): String = raw.trim()

/**
 * 校验新名称，返回 `null` 表示可用。
 *
 * @param existing 现有**自建**书架清单（不含默认书架）。
 * @param renaming 正在改名的书架名；改成自己视为"没变化"，不算重名。
 */
internal fun validateShelfName(
    raw: String,
    existing: List<String>,
    renaming: String? = null,
): ShelfNameError? {
    val name = normalizeShelfName(raw)
    if (name.isEmpty()) return ShelfNameError.EMPTY
    if (name.length > SHELF_NAME_MAX_LENGTH) return ShelfNameError.TOO_LONG
    // 默认书架永远算占用；改名时把自己排除，否则"改成原名"会被判成重名
    val taken = listOf(DEFAULT_SHELF) + existing.filter { it != renaming }
    return if (taken.any { it == name }) ShelfNameError.DUPLICATE else null
}

/**
 * 新建书架：追加到清单末尾（顺序 = 创建顺序）。
 *
 * 名称非法时**原样返回**——调用方应先走 [validateShelfName] 给出具体提示，
 * 这里的兜底是为了让纯函数在任何输入下都不会产生坏数据。
 */
internal fun withShelfCreated(existing: List<String>, raw: String): List<String> {
    if (validateShelfName(raw, existing) != null) return existing
    return existing + normalizeShelfName(raw)
}

/** 重命名：**保持原位置**（顺序不因改名而跳动）；别名/重名/不存在都是无操作。 */
internal fun withShelfRenamed(existing: List<String>, from: String, raw: String): List<String> {
    if (from !in existing) return existing
    if (validateShelfName(raw, existing, renaming = from) != null) return existing
    val name = normalizeShelfName(raw)
    if (name == from) return existing
    return existing.map { if (it == from) name else it }
}

/**
 * 删除书架：默认书架与不存在的名字都是无操作。
 *
 * 注意这里**只改清单**；书架里的书由调用方先搬回默认（见 `LibraryStore.moveShelf`），
 * 顺序不能反——先删清单再搬书的话，中途失败会让那批书找不到归属。
 */
internal fun withShelfDeleted(existing: List<String>, name: String): List<String> =
    if (name == DEFAULT_SHELF) existing else existing.filterNot { it == name }

/** 该书架是否允许删除（默认书架恒不可删）。 */
internal fun isShelfDeletable(name: String): Boolean = name != DEFAULT_SHELF

/** 完整的展示清单：默认书架恒在首位。 */
internal fun shelfNames(existing: List<String>): List<String> =
    listOf(DEFAULT_SHELF) + existing

/**
 * 解析书的归属：`books.shelf` 列 → 书架名字列表。
 *
 * 兼容两种历史形态：
 * - **旧格式**：单个书架名的裸字符串（如 `默认`、`轻小说`）——多书架改造前写入的数据；
 * - **新格式**：JSON 数组（如 `["默认","轻小说"]`）。
 *
 * 只有当字符串确实能被解析成 JSON 数组时才按新格式处理：用户的**书架名本身可能带方括号**
 * （如 `[测试]`），那种情况下退回"整串当一个名字"，不会把书错算到默认书架。
 */
internal fun parseMembership(raw: String?): List<String> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    if (text.startsWith("[")) {
        val parsed = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }.getOrNull()
        // 注意 org.json 非常宽松：`[测试]` 这种没有引号的内容它也能解析成 ["测试"]。
        // 所以不能"能解析就算新格式"，而是要求**重新编码后与原文完全一致**——
        // 只有这样才说明它确实是我们写出去的 JSON 数组；否则按旧格式当成一个书架名
        //（用户完全可能把书架命名为「[测试]」）。
        if (parsed != null && encodeMembership(parsed) == text) return parsed
    }
    return listOf(text)
}

/**
 * 某本书实际归属的书架集合。
 *
 * 清单里没有该名字（改名残留、历史脏数据、书架被删而搬迁中途失败）的成员会被丢弃；
 * 一个都不剩时兜底到默认——
 * 宁可把书显示在默认书架，也不能让它从书架上"消失"。
 */
internal fun shelvesOf(raw: String?, existing: List<String>): Set<String> =
    normalizeMembership(parseMembership(raw), existing)

/**
 * 归属过滤：丢掉**已经不存在**的书架名（改名残留、书架被删但搬迁中途失败、历史脏数据），
 * 一个都不剩时回退到默认书架——宁可把书显示在默认书架，也不能让它从书架上"消失"。
 */
internal fun normalizeMembership(shelves: Collection<String>, existing: List<String>): Set<String> {
    val known = shelves.filter { it == DEFAULT_SHELF || it in existing }
    return if (known.isEmpty()) setOf(DEFAULT_SHELF) else known.toSet()
}

/**
 * 归属集合 → 持久化字符串（JSON 数组，保序去重）。
 *
 * 注意命名：这是**书的归属**编解码，与 `ShelfStore` 的 `encodeShelfList`（书架**清单**）
 * 是两件事——清单里默认书架是隐式的、会被省略，归属里默认书架必须显式保留。
 * 两者曾是同名重载（`List` / `Collection` 两个 JVM 签名），调用时命中了清单那个、
 * 把归属里的"默认"丢掉了（由 `ShelfOpsTest` 的往返用例抓到），所以刻意用不同的名字。
 */
internal fun encodeMembership(shelves: Collection<String>): String {
    val arr = JSONArray()
    shelves
        .map { normalizeShelfName(it) }
        .filter { it.isNotEmpty() && it.length <= SHELF_NAME_MAX_LENGTH }
        .distinct()
        .forEach { arr.put(it) }
    return arr.toString()
}

/**
 * 从一本书的归属里去掉某个书架（删除书架时用）。
 *
 * 去掉后**空集合会回退到默认书架**：书必须至少属于一个书架，否则它就等于不在书架里了，
 * 而"删除书架"承诺的是"其中的书移回默认书架"。
 */
internal fun withShelfRemoved(shelves: Collection<String>, name: String): Set<String> {
    val rest = shelves.filterNot { it == name }
    return if (rest.isEmpty()) setOf(DEFAULT_SHELF) else rest.toSet()
}

/** 书架改名后，书里的归属同步改名。 */
internal fun withShelfRenamedIn(shelves: Collection<String>, from: String, to: String): Set<String> =
    shelves.map { if (it == from) to else it }.toSet()
