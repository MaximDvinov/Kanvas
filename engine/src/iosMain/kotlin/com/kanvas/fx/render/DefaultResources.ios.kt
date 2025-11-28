package com.kanvas.fx.render

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(BetaInteropApi::class)
actual fun defaultResourceResolver(): ResourceResolver = ResourceResolver { path ->
    val normalized = path.removePrefix("/")
    val bundlePath = NSBundle.mainBundle.pathForResource(normalized, null)
    if (bundlePath != null) {
        val data = NSData.create(contentsOfFile = bundlePath) ?: return@ResourceResolver null
        return@ResourceResolver data.toByteArray()
    }
    null
}

actual fun defaultResourceImageDecoder(): ResourceImageDecoder = ResourceImageDecoder { bytes ->
    runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
