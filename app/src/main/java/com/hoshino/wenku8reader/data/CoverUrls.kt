package com.hoshino.wenku8reader.data

/**
 * 由书籍 id 推出站点封面地址（官方 App 的规则，也是站点页面里封面链接的实际布局）。
 *
 * 形如 `https://img.wenku8.com/image/{id / 1000}/{id}/{id}s.jpg`——实测
 * `https://www.wenku8.cc/book/1191.htm` 的封面就是 `…/image/1/1191/1191s.jpg`
 * （见 `Wenku8Hosts` 的说明），`gid` 就是 `id / 1000`。
 *
 * **为什么需要它**：站方书架（`bookcase.php`）只给出书名与最新章，没有封面字段。
 * 有了这条规则就能**零额外请求**地把封面补上；若改为逐本拉 `bookInfo` 取封面，
 * 一个几十本的书架就是一次请求风暴。
 */
fun wenku8CoverUrl(bookId: Int): String =
    "https://img.wenku8.com/image/${bookId / 1000}/$bookId/${bookId}s.jpg"
