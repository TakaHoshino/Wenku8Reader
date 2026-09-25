package com.hoshino.wenku8reader.data.repository

import com.hoshino.wenku8reader.data.BookInfo

/**
 * `/novel/` 链接所需的 `gid`：详情页给了就用它，没给则回退 `id / 1000`（至少为 1）。
 *
 * 从 [Wenku8Repository.groupIdOf] 提为顶层函数以便单测：这条回退规则是"目录/正文/下载
 * 能否取到数据"的分水岭——之前 Reader/DownloadEngine 与目录页对缺字段的处理不一致
 *（一处写死 `?: 1`），导致同一本书在不同页面表现不同。回退错了不会报错，只会取不到内容。
 */
internal fun bookGroupId(info: BookInfo): Int =
    info.groupId ?: (info.id / 1000).coerceAtLeast(1)
