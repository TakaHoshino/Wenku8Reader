package com.hoshino.wenku8reader.data.local.db

import android.content.Context

/**
 * 一次性数据搬迁：旧 SharedPreferences（`library.json` + `reading` 进度键）→ Room。
 *
 * ### 为什么必须"先校验、后清理"
 *
 * 旧数据是用户唯一的书架与阅读记录，一旦清理而导入不完整就**不可恢复**。因此顺序固定为：
 * 解析 → upsert 导入 → **逐条读回比对** → 全部一致才清旧数据。任何一步抛异常或比对不一致，
 * 旧数据原样保留（下次启动会再迁一次），宁可重复迁移也不能丢。旧存储里若有**读不出来的条目**，
 * 同样只导入能读的部分、保留全部旧数据并上报失败，绝不当成"读出来就是全部"。
 *
 * ### 幂等
 *
 * 导入走主键 upsert，重复执行只会覆盖同样的值；且清理成功后再跑会因旧数据为空而直接跳过。
 * 即使清理失败（进程被杀、写入失败），下次启动重跑也不会产生重复行。
 */

/** 迁移结果，供日志/测试断言。 */
internal sealed interface MigrationResult {
    /** 旧数据已为空（已迁移过，或全新安装）：什么都没做。 */
    data object NothingToDo : MigrationResult

    /** 导入并通过校验，旧数据已清理。 */
    data class Imported(val books: Int, val progressRows: Int) : MigrationResult

    /**
     * 迁移未完整完成：**至少有一部分旧数据被保留**，下次启动会重试。
     *
     * [importedBooks]/[importedProgressRows] 是本次已成功写入的行数——失败≠什么都没做，
     * 调用方据此判断要不要提示用户。
     */
    data class Failed(
        val error: Throwable,
        val importedBooks: Int = 0,
        val importedProgressRows: Int = 0,
    ) : MigrationResult
}

/**
 * 旧存储的访问入口。抽成接口是为了让迁移逻辑（含"校验失败不清理"这条关键行为）
 * 能在 JVM 单测里用假实现覆盖，而不需要 Android 运行时。
 */
internal interface LegacySource {

    /** `library` 偏好里的整份书架 JSON；不存在返回 null。 */
    fun libraryJson(): String?

    /** `reading` 偏好的全量快照。 */
    fun readingSnapshot(): Map<String, Any?>

    /** 清理书架旧数据；**只在导入校验通过后调用**。 */
    fun clearLibrary()

    /** 清理这些书的进度键；**只在导入校验通过后调用**。 */
    fun clearReading(bookIds: Set<Int>)
}

/** 生产实现：`library` / `reading` 两个 SharedPreferences 文件。 */
internal class SharedPrefsLegacySource(context: Context) : LegacySource {

    private val library =
        context.applicationContext.getSharedPreferences(LEGACY_PREFS_LIBRARY, Context.MODE_PRIVATE)
    private val reading =
        context.applicationContext.getSharedPreferences(LEGACY_PREFS_READING, Context.MODE_PRIVATE)

    override fun libraryJson(): String? = library.getString(LEGACY_KEY_LIBRARY_DATA, null)

    override fun readingSnapshot(): Map<String, Any?> = reading.all

    // 用 commit() 而不是 apply()：清理是"删除用户数据"的最后一步，
    // 必须确认落盘后才算迁移完成；apply() 在进程立刻被杀时可能丢失，
    // 留下半清状态（虽然靠幂等仍能自愈，但没必要引入这种不确定性）。
    // 调用方保证在 IO 线程执行。
    override fun clearLibrary() {
        library.edit().clear().commit()
    }

    override fun clearReading(bookIds: Set<Int>) {
        if (bookIds.isEmpty()) return
        val editor = reading.edit()
        bookIds.forEach { id ->
            editor.remove("$LEGACY_KEY_RESUME_CID$id")
            editor.remove("$LEGACY_KEY_PROGRESS_AT$id")
            editor.remove("$LEGACY_KEY_PROGRESS_TOTAL$id")
            editor.remove("$LEGACY_KEY_FINISHED$id")
        }
        editor.commit()
    }
}

/**
 * 执行搬迁。调用方需保证在 IO 线程运行（内含数据库与磁盘 I/O）。
 *
 * @param dao 目标数据库入口。
 * @param legacy 旧存储入口。
 */
internal suspend fun migrateLegacyStores(
    dao: LibraryDao,
    legacy: LegacySource,
): MigrationResult {
    val library = parseLegacyLibrary(legacy.libraryJson())
    val reading = parseLegacyReading(legacy.readingSnapshot())
    val problems = buildList {
        if (!library.readable) add("旧书架 JSON 无法解析")
        if (library.droppedEntries > 0) add("旧书架有 ${library.droppedEntries} 条无法解析")
        if (reading.droppedEntries > 0) add("旧阅读记录有 ${reading.droppedEntries} 条无法解析")
    }
    val books = library.books
    val progress = reading.rows

    if (books.isEmpty() && progress.isEmpty()) {
        return if (problems.isEmpty()) {
            MigrationResult.NothingToDo
        } else {
            MigrationResult.Failed(IllegalStateException(problems.joinToString("；")))
        }
    }

    // 已写入且**已通过校验**的行数；清理阶段出错时据此上报"至少搬过来多少"。
    var importedBooks = 0
    var importedProgressRows = 0
    return try {
        if (books.isNotEmpty()) dao.upsertBooks(books)
        if (progress.isNotEmpty()) dao.upsertProgressAll(progress)

        // 逐条读回比对：只有能完整读出来、且字段一字不差，才认为导入成功。
        // 这一层是"删旧数据"的唯一放行条件，宁可保守。
        verifyImported(dao, books, progress)
        importedBooks = books.size
        importedProgressRows = progress.size

        // 仍有读不出来的条目 → 保留旧数据（下次启动重试），本次只上报。
        if (library.canClear) legacy.clearLibrary()
        if (reading.canClear) legacy.clearReading(progress.mapTo(mutableSetOf()) { it.bookId })

        if (problems.isEmpty()) {
            MigrationResult.Imported(books = books.size, progressRows = progress.size)
        } else {
            MigrationResult.Failed(
                error = IllegalStateException(problems.joinToString("；")),
                importedBooks = importedBooks,
                importedProgressRows = importedProgressRows,
            )
        }
    } catch (t: Throwable) {
        MigrationResult.Failed(
            error = t,
            importedBooks = importedBooks,
            importedProgressRows = importedProgressRows,
        )
    }
}

/** 校验：导入的每一条都能原样读回；缺一条或字段不一致就抛 [IllegalStateException]。 */
private suspend fun verifyImported(
    dao: LibraryDao,
    books: List<BookEntity>,
    progress: List<ReadingProgressEntity>,
) {
    if (books.isNotEmpty()) {
        val stored = dao.books().associateBy { it.id }
        books.forEach { expected ->
            val actual = stored[expected.id]
            check(actual == expected) {
                "书架导入校验失败：id=${expected.id} 期望=$expected 实际=$actual"
            }
        }
    }
    if (progress.isNotEmpty()) {
        val stored = dao.allProgress().associateBy { it.bookId }
        progress.forEach { expected ->
            val actual = stored[expected.bookId]
            check(actual == expected) {
                "阅读进度导入校验失败：bookId=${expected.bookId} 期望=$expected 实际=$actual"
            }
        }
    }
}
