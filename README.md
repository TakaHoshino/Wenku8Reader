#  轻小说文库（Wenku8Reader）
![w8r.png](w8r.png)

> 一款面向轻小说爱好者的 Android 阅读客户端，数据来自 wenku8.net 轻小说文库。

<p align="center">
  <a href="https://qm.qq.com/q/PeFKgwevG6">
    <img src="https://img.shields.io/badge/QQ%E4%BA%A4%E6%B5%81%E7%BE%A4-1106956143-12B7F5?style=flat-square&logo=tencentqq&logoColor=white" alt="QQ交流群" />
  </a>
  <a href="https://www.ifdian.net/a/wenku8reader">
    <img src="https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E7%BB%99%E4%BD%9C%E8%80%85%E4%B9%B0%E6%9D%AF%E5%92%96%E5%95%A1-946CE6?style=flat-square&logo=buymeacoffee&logoColor=white" alt="爱发电" />
  </a>
</p>


## 简介

**轻小说文库**是一款原生 Android 阅读器：浏览 wenku8 全站栏目、搜索与分类找书、一键加入书架，并内置了沉浸式在线阅读器——支持侧滑翻页、自动翻页、音量键翻页、简繁转换，还可将整本书离线下载为 TXT / EPUB 随时阅读。

应用采用 **Material Design 3** 设计语言：卡片化界面、动态取色（跟随系统壁纸生成主题色）、深色/纯黑（OLED 省电）模式，并适配 120Hz 高刷新率屏幕，滑动与翻页顺滑跟手。


## 功能特性

-  **探索发现**：首页栏目（封面轮播 + 文字榜单）、书名/作者搜索、分类标签浏览
-  **我的书架**：收藏管理、多种排序（默认/最近更新/书名/字数/倒序）、一键继续阅读
-  **沉浸式阅读器**：
  - 侧滑翻页 / 滚动翻页双模式，点按左右翻页
  - 自动翻页（可设间隔）、音量键翻页
  - 章节进度自动记录，重开自动续读
  - 简体 ↔ 繁体一键切换
  - 阅读外观自由定制：背景色/背景图、正文字色、字体、字号、行距、四周边距
  - 插图章节直接显示图片
-  **离线下载**：整本下载为 TXT（简/繁/UTF-8）或 EPUB，保存至 `Downloads/Wenku8/`
-  **本地缓存**：已加载的详情/目录/章节自动落盘缓存，断网也能读看过的章节，二次打开不重复联网
-  **网络韧性**：Cloudflare 自动绕过（多镜像 + cf_clearance 复用）、主站域名可在设置中切换
-  **个性化外观**：动态取色 / 手动种子色、深色模式（跟随系统/浅色/深色）、OLED 纯黑模式

## 界面一览

| 页面 | 说明 |
|---|---|
| 探索 | 顶栏 + 圆角搜索条，推荐/标签双 Tab（内置 50 个分类），封面卡片流 |
| 书架 | 卡片化书单，进度一目了然，一键续读 |
| 详情 | 封面信息卡、标签药丸、开始/继续阅读、离线下载卡、内容简介 |
| 阅读器 | 沉浸式全屏，状态指示器（电量/时间/章节/进度），悬浮章节进度条 |
| 设置 | 分组卡片：账号、外观（主题/纯黑/取色）、阅读设置、关于 |

## 系统要求

- Android 8.0（API 26）及以上
- 建议 Android 12+ 以体验完整的动态取色与高刷新率效果

## 下载与构建

- **安装包**：GitHub Releases 发布（自动化：推送 `main` 后依据 Conventional Commits 自动升级版本、构建并发布，见 [VERSIONING.md](VERSIONING.md)）
- **本地构建**：`./gradlew :app:assembleDebug`（产物 `app/build/outputs/apk/debug/app-debug.apk`），或用 Android Studio 打开后 **Run ▶**
- 需要 JDK 17+ 与 Android SDK 34

## 支持与交流

<p align="center">
  <a href="https://qm.qq.com/q/PeFKgwevG6">
    <img src="https://img.shields.io/badge/QQ%E4%BA%A4%E6%B5%81%E7%BE%A4-%E7%82%B9%E5%87%BB%E5%8A%A0%E5%85%A5-12B7F5?style=flat-square&logo=tencentqq&logoColor=white" alt="QQ交流群" />
  </a>
  <a href="https://www.ifdian.net/a/wenku8reader">
    <img src="https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E7%BB%99%E4%BD%9C%E8%80%85%E4%B9%B0%E6%9D%AF%E5%92%96%E5%95%A1-946CE6?style=flat-square&logo=buymeacoffee&logoColor=white" alt="爱发电" />
  </a>
</p>

## 鸣谢

- [**LightNovelReader**](https://github.com/dmzz-yyhyy/LightNovelReader) —— 本项目网络层与 Cloudflare 绕过方案的**重要参考**：cf_clearance 会话复用、随机 UA、内置分类清单、多镜像兜底等实现均借鉴自该项目，特此鸣谢。

## 技术栈

原生 **Kotlin + Jetpack Compose + Material Design 3** 构建，无 WebView 外壳；网络层基于 OkHttp + Cronet，图片加载使用 Coil，简繁转换使用 opencc4j。

## 声明

- 本应用仅用于**个人学习与交流**，请遵守 wenku8.net 站点使用条款，支持正版。
