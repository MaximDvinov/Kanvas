package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Universal render style that can be applied to any primitive.
 *
 * @property fillColor base fill color.
 * @property gradient optional linear gradient in world space.
 * @property stroke optional stroke style.
 * @property glow optional glow style.
 * @property tint texture tint color.
 * @property shaderId optional shader id from [AssetRegistry].
 * @property beforeEffect whether shader effect runs before primitive draw.
 * @property afterEffect whether shader effect runs after primitive draw.
 */
data class RenderStyle(
    val fillColor: Color = Color.White,
    val gradient: GradientStyle? = null,
    val stroke: StrokeStyle? = null,
    val glow: GlowStyle? = null,
    val tint: Color = Color.White,
    val shaderId: String? = null,
    val shaderUniforms: Map<String, ShaderUniform> = emptyMap(),
    val beforeEffect: Boolean = true,
    val afterEffect: Boolean = true,
)

/**
 * Linear gradient style.
 *
 * @property colors gradient color stops.
 * @property start optional world-space start point.
 * @property end optional world-space end point.
 */
data class GradientStyle(
    val colors: List<Color>,
    val start: Offset? = null,
    val end: Offset? = null,
)

/**
 * Stroke style for outline drawing.
 *
 * @property color stroke color.
 * @property width stroke width in world units.
 */
data class StrokeStyle(
    val color: Color = Color.White,
    val width: Float = 1f,
)

/**
 * Glow style applied as extra blurred/expanded draw pass.
 *
 * @property color optional glow color, defaults to primitive color.
 * @property alpha glow alpha.
 * @property widthMultiplier glow width multiplier.
 */
data class GlowStyle(
    val color: Color? = null,
    val alpha: Float = 0.35f,
    val widthMultiplier: Float = 2f,
)

/**
 * Builder for [RenderStyle].
 */
class RenderStyleBuilder internal constructor() {
    private var fillColor: Color = Color.White
    private var gradient: GradientStyle? = null
    private var stroke: StrokeStyle? = null
    private var glow: GlowStyle? = null
    private var tint: Color = Color.White
    private var shaderId: String? = null
    private val shaderUniforms = linkedMapOf<String, ShaderUniform>()
    private var beforeEffect: Boolean = true
    private var afterEffect: Boolean = true

    /**
     * Sets fill color.
     *
     * @param color fill color.
     */
    fun fill(color: Color) {
        fillColor = color
    }

    /**
     * Sets linear gradient from arbitrary color list.
     *
     * @param colors gradient colors.
     * @param start optional world-space start.
     * @param end optional world-space end.
     */
    fun linearGradient(
        colors: List<Color>,
        start: Offset? = null,
        end: Offset? = null,
    ) {
        gradient = GradientStyle(colors = colors, start = start, end = end)
    }

    /**
     * Sets linear gradient from two colors.
     *
     * @param startColor gradient start color.
     * @param endColor gradient end color.
     * @param start optional world-space start.
     * @param end optional world-space end.
     */
    fun linearGradient(
        startColor: Color,
        endColor: Color,
        start: Offset? = null,
        end: Offset? = null,
    ) {
        gradient = GradientStyle(colors = listOf(startColor, endColor), start = start, end = end)
    }

    /**
     * Sets stroke style.
     *
     * @param color stroke color.
     * @param width stroke width.
     */
    fun stroke(
        color: Color,
        width: Float = 1f,
    ) {
        stroke = StrokeStyle(color = color, width = width)
    }

    /**
     * Sets glow style.
     *
     * @param color optional glow color.
     * @param alpha glow alpha.
     * @param widthMultiplier glow width multiplier.
     */
    fun glow(
        color: Color? = null,
        alpha: Float = 0.35f,
        widthMultiplier: Float = 2f,
    ) {
        glow = GlowStyle(color = color, alpha = alpha, widthMultiplier = widthMultiplier)
    }

    /**
     * Sets texture tint.
     *
     * @param color tint color.
     */
    fun tint(color: Color) {
        tint = color
    }

    /**
     * References a shader registered in [AssetRegistry].
     *
     * Current runtime keeps it as style metadata and extension point.
     */
    fun shader(id: String) {
        shaderId = id
    }

    fun uniform(name: String, value: Float) {
        shaderUniforms[name] = ShaderUniform.Float1(value)
    }

    fun uniform2(name: String, x: Float, y: Float) {
        shaderUniforms[name] = ShaderUniform.Float2(x, y)
    }

    fun uniform4(name: String, x: Float, y: Float, z: Float, w: Float) {
        shaderUniforms[name] = ShaderUniform.Float4(x, y, z, w)
    }

    fun uniformColor(name: String, color: Color) {
        shaderUniforms[name] = ShaderUniform.Color4(color)
    }

    /**
     * Configures shader effect phases.
     *
     * @param beforeDraw run effect before primitive draw.
     * @param afterDraw run effect after primitive draw.
     */
    fun effectPhase(
        beforeDraw: Boolean = true,
        afterDraw: Boolean = true,
    ) {
        beforeEffect = beforeDraw
        afterEffect = afterDraw
    }

    internal fun build(): RenderStyle = RenderStyle(
        fillColor = fillColor,
        gradient = gradient,
        stroke = stroke,
        glow = glow,
        tint = tint,
        shaderId = shaderId,
        shaderUniforms = shaderUniforms.toMap(),
        beforeEffect = beforeEffect,
        afterEffect = afterEffect,
    )
}
