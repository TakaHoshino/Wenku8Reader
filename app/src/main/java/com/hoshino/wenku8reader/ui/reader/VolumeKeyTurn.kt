package com.hoshino.wenku8reader.ui.reader

/**
 * Bridge so MainActivity can forward volume-key presses to the open reader,
 * since Compose does not receive volume keys directly.
 */
object VolumeKeyTurn {
    @Volatile var enabled: Boolean = false
    @Volatile var onVolumeUp: (() -> Unit)? = null
    @Volatile var onVolumeDown: (() -> Unit)? = null
}
