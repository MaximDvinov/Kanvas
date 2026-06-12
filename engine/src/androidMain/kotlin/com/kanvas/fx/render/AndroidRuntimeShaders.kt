package com.kanvas.fx.render

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

actual fun defaultRuntimeShaderBrushFactory(): RuntimeShaderBrushFactory? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AndroidRuntimeShaderBrushFactory else null

private object AndroidRuntimeShaderBrushFactory : RuntimeShaderBrushFactory {
    override fun create(
        shader: ShaderAsset,
        geometry: PrimitiveGeometry,
        uniforms: Map<String, ShaderUniform>,
        context: RenderContext?,
    ): Brush? {
        val runtime = runtimeShaderSource(shader, uniforms) ?: return null
        return AndroidRuntimeShaderBrush(
            code = runtime.code,
            uniformOrder = runtime.uniformOrder,
            geometry = geometry,
            uniforms = uniforms,
            context = context,
        )
    }
}

private class AndroidRuntimeShaderBrush(
    private val code: String,
    private val uniformOrder: List<String>,
    private val geometry: PrimitiveGeometry,
    private val uniforms: Map<String, ShaderUniform>,
    private val context: RenderContext?,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        return try {
            val shader = RuntimeShader(code)
            val allUniforms = runtimeShaderUniforms(size, geometry, uniforms, context)
            val names = uniformOrder.ifEmpty { allUniforms.keys.toList() }
            names.forEach { name ->
                val uniform = allUniforms[name] ?: return@forEach
                when (uniform) {
                    is ShaderUniform.Float1 -> shader.setFloatUniform(name, uniform.value)
                    is ShaderUniform.Float2 -> shader.setFloatUniform(name, uniform.x, uniform.y)
                    is ShaderUniform.Float4 -> shader.setFloatUniform(name, uniform.x, uniform.y, uniform.z, uniform.w)
                    is ShaderUniform.Color4 -> shader.setFloatUniform(
                        name,
                        uniform.value.red,
                        uniform.value.green,
                        uniform.value.blue,
                        uniform.value.alpha,
                    )
                }
            }
            shader
        } catch (_: Throwable) {
            RuntimeShader("half4 main(float2 p) { return half4(0.02, 0.07, 0.22, 1.0); }")
        }
    }
}
