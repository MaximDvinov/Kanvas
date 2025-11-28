package com.kanvas.fx.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.io.InputStream

actual fun defaultResourceResolver(): ResourceResolver = ResourceResolver { path ->
    val normalized = path.removePrefix("/")
    openClasspath(normalized)?.use { return@ResourceResolver it.readBytes() }
    openClasspath("/$normalized")?.use { return@ResourceResolver it.readBytes() }
    openAndroidAsset(normalized)?.use { return@ResourceResolver it.readBytes() }
    null
}

actual fun defaultResourceImageDecoder(): ResourceImageDecoder = ResourceImageDecoder { bytes ->
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@ResourceImageDecoder null
    bitmap.asImageBitmap()
}

private fun openClasspath(path: String): InputStream? {
    return AssetRegistry::class.java.getResourceAsStream(path)
}

private fun openAndroidAsset(path: String): InputStream? {
    val app = runCatching {
        val cls = Class.forName("android.app.ActivityThread")
        val method = cls.getMethod("currentApplication")
        method.invoke(null) as? android.app.Application
    }.getOrNull() ?: return null

    val candidatePaths = buildList {
        add(path)
        add("composeResources/$path")
        // Standard Compose MPP files location inside assets.
        val roots = runCatching { app.assets.list("composeResources")?.toList().orEmpty() }.getOrDefault(emptyList())
        roots.forEach { moduleDir ->
            add("composeResources/$moduleDir/files/$path")
        }
    }
    for (candidate in candidatePaths.distinct()) {
        val stream = runCatching { app.assets.open(candidate) }.getOrNull()
        if (stream != null) return stream
    }
    return null
}
