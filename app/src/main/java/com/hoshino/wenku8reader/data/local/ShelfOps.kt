package com.hoshino.wenku8reader.data.local

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
 * 某本书实际归属的书架。
 *
 * 清单里没有该名字（改名残留、历史脏数据、搬迁中途失败）时兜底到默认——
 * 宁可把书显示在默认书架，也不能让它从书架上"消失"。
 */
internal fun shelfOf(bookShelf: String?, existing: List<String>): String {
    val name = bookShelf?.takeIf { it.isNotBlank() } ?: return DEFAULT_SHELF
    return if (name == DEFAULT_SHELF || name in existing) name else DEFAULT_SHELF
}
