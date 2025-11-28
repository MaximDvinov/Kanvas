package com.kanvas.fx.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.TextureResolver
import org.jetbrains.skia.Image
import java.io.File

internal fun AssetRegistry.enableDesktopTextureAutoResolve() {
    addTextureResolver(
        TextureResolver { path ->
            val normalized = if (path.startsWith("/")) path else "/$path"
            object {}.javaClass.getResourceAsStream(normalized)?.use { stream ->
                return@TextureResolver Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
            }
            val file = File(path)
            if (!file.exists()) return@TextureResolver null
            Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        },
    )
}
