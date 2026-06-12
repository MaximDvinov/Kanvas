package com.kanvas.fx.render

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.w3c.xhr.XMLHttpRequest

actual fun defaultResourceResolver(): ResourceResolver = ResourceResolver { path ->
    val normalized = path.removePrefix("/")
    val candidates = listOf(
        normalized,
        "composeResources/$normalized",
        "composeResources/com.kanvas.fx.shared.generated.resources/files/$normalized",
        "composeResources/kanvas.planetsample.shared.generated.resources/files/$normalized",
    )
    for (candidate in candidates) {
        val bytes = fetchBytesSync(candidate)
        if (bytes != null) return@ResourceResolver bytes
    }
    null
}

actual fun defaultResourceImageDecoder(): ResourceImageDecoder = ResourceImageDecoder { bytes ->
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

private fun fetchBytesSync(path: String): ByteArray? {
    val request = XMLHttpRequest()
    return try {
        request.open("GET", path, false)
        request.overrideMimeType("text/plain; charset=x-user-defined")
        request.send()
        val status = request.status.toInt()
        if (status !in 200..299 && status != 0) return null
        val text = request.responseText ?: return null
        if (status == 0 && text.isEmpty()) return null
        ByteArray(text.length) { i ->
            (text[i].code and 0xFF).toByte()
        }
    } catch (_: Throwable) {
        null
    }
}
