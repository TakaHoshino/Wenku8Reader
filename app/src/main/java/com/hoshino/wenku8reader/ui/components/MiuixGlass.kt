package com.hoshino.wenku8reader.ui.components

import android.os.Build

/**
 * MIUIX 液态玻璃（液态亚克力）支持判定。
 *
 * miuix-blur 的模糊依赖 `RenderEffect`，库自身声明 minSdk 32；项目最低支持 26，
 * 因此在 manifest 里用 `tools:overrideLibrary` 放行，并在这里做运行时门控：
 * 低于 Android 12L 时**不调用任何模糊 API**，底栏退化为半透明纯色（观感接近、零风险）。
 *
 * 悬浮底栏的玻璃效果现在直接由 `MiuixFloatingBottomBar` 用 miuix-blur 的 `textureBlur`
 * 实现（含高光描边与提饱和），这里只保留"是否可用"的判定，供调用方决定要不要传 backdrop。
 */
val isMiuixGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2
