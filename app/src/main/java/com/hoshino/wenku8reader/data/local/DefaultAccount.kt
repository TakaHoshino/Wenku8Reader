package com.hoshino.wenku8reader.data.local

/**
 * Default account read from 技术性文档(只读勿动)/wenku8account.txt.
 *
 * Used silently on first launch so content is accessible without forcing the
 * user to log in. While this account is active the UI reports "未登录"; the user
 * can log in with their own account at any time.
 */
object DefaultAccount {
    const val USERNAME: String = "w8racc"
    const val PASSWORD: String = "TdAFDyWzXRxEpCmFYPnS"
}
