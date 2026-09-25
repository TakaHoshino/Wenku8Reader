package com.hoshino.wenku8reader.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 应用本地数据库：书架 + 阅读进度。
 *
 * `exportSchema = false`：本工程只做**同版本内的数据搬迁**（旧 JSON / 偏好 → Room），
 * 尚未有 Room 版本升级需求，导出 schema 只会多出一份无人核对的文件；
 * 将来一旦真的要写 Room 迁移，请先打开它并补上迁移测试。
 */
@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        /** 数据库文件名。 */
        const val NAME = "wenku8.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
