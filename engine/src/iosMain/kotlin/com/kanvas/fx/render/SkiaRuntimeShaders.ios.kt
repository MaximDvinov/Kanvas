package com.kanvas.fx.render

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.Data
import org.jetbrains.skia.RuntimeEffect

actual fun defaultRuntimeShaderBrushFactory(): RuntimeShaderBrushFactory? = IosRuntimeShaderBrushFactory

private object IosRuntimeShaderBrushFactory : RuntimeShaderBrushFactory {
    private val effectCache = mutableMapOf<String, RuntimeEffect>()

    override fun create(
        shader: ShaderAsset,
        geometry: PrimitiveGeometry,
        uniforms: Map<String, ShaderUniform>,
        context: RenderContext?,
    ): Brush? {
        val runtime = runtimeShaderSource(shader, uniforms) ?: return null
        return IosRuntimeEffectBrush(
            code = runtime.code,
            uniformOrder = runtime.uniformOrder,
            geometry = geometry,
            uniforms = uniforms,
            context = context,
            effectProvider = { code -> effectCache.getOrPut(code) { RuntimeEffect.makeForShader(code) } },
        )
    }
}

private class IosRuntimeEffectBrush(
    private val code: String,
    private val uniformOrder: List<String>,
    private val geometry: PrimitiveGeometry,
    private val uniforms: Map<String, ShaderUniform>,
    private val context: RenderContext?,
    private val effectProvider: (String) -> RuntimeEffect,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val allUniforms = runtimeShaderUniforms(size, geometry, uniforms, context)
        val ordered = uniformOrder.ifEmpty { allUniforms.keys.toList() }
        val data = Data.makeFromBytes(packRuntimeUniforms(ordered, allUniforms))
        return try {
            effectProvider(code).makeShader(data, null, null).asComposeShader()
        } catch (_: Throwable) {
            effectProvider("half4 main(float2 p) { return half4(0.0); }")
                .makeShader(Data.makeFromBytes(byteArrayOf()), null, null)
                .asComposeShader()
        }
    }
}
