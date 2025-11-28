package com.kanvas.fx.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File

actual fun defaultResourceResolver(): ResourceResolver = ResourceResolver { path ->
    val normalized = if (path.startsWith("/")) path else "/$path"
    AssetRegistry::class.java.getResourceAsStream(normalized)?.use { stream ->
        return@ResourceResolver stream.readBytes()
    }
    val file = File(path)
    if (!file.exists() || !file.isFile) return@ResourceResolver null
    file.readBytes()
}

actual fun defaultResourceImageDecoder(): ResourceImageDecoder = ResourceImageDecoder { bytes ->
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

