package com.kanvas.fx.render

import androidx.compose.ui.geometry.Size

internal fun runtimeShaderSource(
    shader: ShaderAsset,
    uniforms: Map<String, ShaderUniform>,
): ShaderSource.SkiaRuntime? = when (val source = shader.source) {
    is ShaderSource.SkiaRuntime -> source
    is ShaderSource.Text -> ShaderSource.SkiaRuntime(source.value, uniforms.keys.toList())
    is ShaderSource.ExternalDsl -> null
}

internal fun runtimeShaderUniforms(
    size: Size,
    geometry: PrimitiveGeometry,
    uniforms: Map<String, ShaderUniform>,
    context: RenderContext?,
): Map<String, ShaderUniform> = buildMap {
    putAll(uniforms)
    put("iResolution", ShaderUniform.Float2(size.width, size.height))
    put("uResolution", ShaderUniform.Float2(size.width, size.height))
    put("uTime", ShaderUniform.Float1(context?.elapsedSeconds ?: 0f))
    put("uFrame", ShaderUniform.Float1((context?.frameIndex ?: 0L).toFloat()))
    put(
        "uCamera",
        ShaderUniform.Float4(
            context?.cameraX ?: 0f,
            context?.cameraY ?: 0f,
            context?.cameraZoom ?: 1f,
            0f,
        ),
    )
    put(
        "uWorldCenter",
        ShaderUniform.Float2(
            context?.worldCenterX ?: 0f,
            context?.worldCenterY ?: 0f,
        ),
    )
    put("uWorldRadius", ShaderUniform.Float1(context?.worldRadius ?: 0f))
    put(
        "uScreenRect",
        ShaderUniform.Float4(
            context?.screenLeft ?: 0f,
            context?.screenTop ?: 0f,
            context?.screenWidth ?: size.width,
            context?.screenHeight ?: size.height,
        ),
    )
    when (geometry) {
        is PrimitiveGeometry.Circle,
        is PrimitiveGeometry.Rect,
        is PrimitiveGeometry.Texture,
        -> {
            put("uCenter", ShaderUniform.Float2(size.width * 0.5f, size.height * 0.5f))
            put("uRadius", ShaderUniform.Float1(size.minDimension * 0.5f))
        }
        else -> Unit
    }
}

internal fun packRuntimeUniforms(
    order: List<String>,
    uniforms: Map<String, ShaderUniform>,
): ByteArray {
    val floats = ArrayList<Float>(order.size * 4)
    order.forEach { name ->
        when (val uniform = uniforms[name]) {
            is ShaderUniform.Float1 -> floats += uniform.value
            is ShaderUniform.Float2 -> {
                floats += uniform.x
                floats += uniform.y
            }
            is ShaderUniform.Float4 -> {
                floats += uniform.x
                floats += uniform.y
                floats += uniform.z
                floats += uniform.w
            }
            is ShaderUniform.Color4 -> {
                floats += uniform.value.red
                floats += uniform.value.green
                floats += uniform.value.blue
                floats += uniform.value.alpha
            }
            null -> Unit
        }
    }
    val bytes = ByteArray(floats.size * 4)
    floats.forEachIndexed { index, value ->
        val bits = value.toBits()
        val offset = index * 4
        bytes[offset] = bits.toByte()
        bytes[offset + 1] = (bits shr 8).toByte()
        bytes[offset + 2] = (bits shr 16).toByte()
        bytes[offset + 3] = (bits shr 24).toByte()
    }
    return bytes
}
