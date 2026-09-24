package com.hoshino.wenku8reader.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * MIUIX 液态玻璃（液态亚克力）支持判定。
 *
 * miuix-blur 的模糊依赖 `RenderEffect`，库自身声明 minSdk 32；项目最低支持 26，
 * 因此在 manifest 里用 `tools:overrideLibrary` 放行，并在这里做运行时门控：
 * 低于 Android 12L 时**不调用任何模糊 API**，底栏退化为半透明纯色（观感接近、零风险）。
 */
val isMiuixGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2

/**
 * 给组件加一层"液态玻璃"背景：对 [backdrop]（通常是底下滚动的内容）做实时模糊。
 *
 * [backdrop] 为 null 或系统版本不支持时原样返回，调用方负责给一个半透明底色兜底——
 * 这样低版本设备的底栏仍然与 HyperOS 的观感一致，只是没有实时模糊。
 */
fun Modifier.miuixGlass(
    backdrop: Backdrop?,
    shape: Shape,
    radius: Float = 26f,
): Modifier =
    if (backdrop != null && isMiuixGlassSupported) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(radius, radius) },
        )
    } else {
        this
    }
