package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Supported gradient brush kinds. */
enum class GradientKind {
    Linear,
    Radial,
    Sweep,
}

/** Preferred glow implementation. */
enum class GlowMode {
    Soft,
    Blur,
    Auto,
}

/** Built-in render effect kinds that do not require custom shader assets. */
enum class BuiltInEffectKind {
    Blur,
    Shadow,
    InnerShadow,
    Outline,
    Bloom,
    Opacity,
    ColorFilter,
}

/**
 * Universal render style that can be applied to any primitive.
 *
 * @property fillColor base fill color.
 * @property gradient optional gradient in world space.
 * @property stroke optional stroke style.
 * @property glow optional glow style.
 * @property effects combined built-in effects applied around primitive draw.
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
    val effects: List<RenderEffectStyle> = emptyList(),
    val tint: Color = Color.White,
    val shaderId: String? = null,
    val shaderUniforms: Map<String, ShaderUniform> = emptyMap(),
    val beforeEffect: Boolean = true,
    val afterEffect: Boolean = true,
)

/**
 * Gradient style.
 *
 * @property colors gradient color stops.
 * @property start optional world-space start point.
 * @property end optional world-space end point.
 * @property kind gradient kind.
 * @property center optional world-space center for radial/sweep gradients.
 * @property radius optional world-space radius for radial gradients.
 */
data class GradientStyle(
    val colors: List<Color>,
    val start: Offset? = null,
    val end: Offset? = null,
    val kind: GradientKind = GradientKind.Linear,
    val center: Offset? = null,
    val radius: Float? = null,
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
 * @property radius explicit glow radius in world units, defaults to stroke-derived size.
 * @property spread expansion multiplier for soft fallback passes.
 * @property passes soft fallback pass count.
 * @property mode preferred glow implementation.
 */
data class GlowStyle(
    val color: Color? = null,
    val alpha: Float = 0.35f,
    val widthMultiplier: Float = 2f,
    val radius: Float? = null,
    val spread: Float = 1f,
    val passes: Int = 4,
    val mode: GlowMode = GlowMode.Auto,
)

/** One built-in effect entry. */
data class RenderEffectStyle(
    val kind: BuiltInEffectKind,
    val color: Color? = null,
    val alpha: Float = 1f,
    val radius: Float = 0f,
    val spread: Float = 1f,
    val offset: Offset = Offset.Zero,
    val width: Float = 1f,
    val passes: Int = 4,
    val mode: GlowMode = GlowMode.Auto,
)

/**
 * Builder for [RenderStyle].
 */
class RenderStyleBuilder internal constructor() {
    private var fillColor: Color = Color.White
    private var gradient: GradientStyle? = null
    private var stroke: StrokeStyle? = null
    private var glow: GlowStyle? = null
    private val effects = mutableListOf<RenderEffectStyle>()
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
        gradient = GradientStyle(colors = colors, start = start, end = end, kind = GradientKind.Linear)
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
        gradient = GradientStyle(colors = listOf(startColor, endColor), start = start, end = end, kind = GradientKind.Linear)
    }

    /** Sets radial gradient from arbitrary color list. */
    fun radialGradient(
        colors: List<Color>,
        center: Offset? = null,
        radius: Float? = null,
    ) {
        gradient = GradientStyle(colors = colors, kind = GradientKind.Radial, center = center, radius = radius)
    }

    /** Sets radial gradient from two colors. */
    fun radialGradient(
        innerColor: Color,
        outerColor: Color,
        center: Offset? = null,
        radius: Float? = null,
    ) {
        radialGradient(colors = listOf(innerColor, outerColor), center = center, radius = radius)
    }

    /** Sets sweep gradient from arbitrary color list. */
    fun sweepGradient(
        colors: List<Color>,
        center: Offset? = null,
    ) {
        gradient = GradientStyle(colors = colors, kind = GradientKind.Sweep, center = center)
    }

    /** Sets sweep gradient from two colors. */
    fun sweepGradient(
        startColor: Color,
        endColor: Color,
        center: Offset? = null,
    ) {
        sweepGradient(colors = listOf(startColor, endColor), center = center)
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
        radius: Float? = null,
        spread: Float = 1f,
        passes: Int = 4,
        mode: GlowMode = GlowMode.Auto,
    ) {
        glow = GlowStyle(
            color = color,
            alpha = alpha,
            widthMultiplier = widthMultiplier,
            radius = radius,
            spread = spread,
            passes = passes,
            mode = mode,
        )
    }

    fun blur(
        radius: Float,
        mode: GlowMode = GlowMode.Auto,
    ) {
        effects += RenderEffectStyle(kind = BuiltInEffectKind.Blur, radius = radius, mode = mode)
    }

    fun shadow(
        color: Color = Color.Black,
        alpha: Float = 0.35f,
        radius: Float = 8f,
        offset: Offset = Offset(4f, 4f),
        spread: Float = 1f,
        passes: Int = 4,
        mode: GlowMode = GlowMode.Auto,
    ) {
        effects += RenderEffectStyle(
            kind = BuiltInEffectKind.Shadow,
            color = color,
            alpha = alpha,
            radius = radius,
            offset = offset,
            spread = spread,
            passes = passes,
            mode = mode,
        )
    }

    fun innerShadow(
        color: Color = Color.Black,
        alpha: Float = 0.25f,
        width: Float = 2f,
    ) {
        effects += RenderEffectStyle(kind = BuiltInEffectKind.InnerShadow, color = color, alpha = alpha, width = width)
    }

    fun outline(
        color: Color = Color.White,
        width: Float = 1f,
        alpha: Float = 1f,
    ) {
        effects += RenderEffectStyle(kind = BuiltInEffectKind.Outline, color = color, alpha = alpha, width = width)
    }

    fun bloom(
        color: Color? = null,
        alpha: Float = 0.25f,
        radius: Float = 8f,
        spread: Float = 1.4f,
        passes: Int = 5,
        mode: GlowMode = GlowMode.Auto,
    ) {
        effects += RenderEffectStyle(
            kind = BuiltInEffectKind.Bloom,
            color = color,
            alpha = alpha,
            radius = radius,
            spread = spread,
            passes = passes,
            mode = mode,
        )
    }

    fun opacity(alpha: Float) {
        effects += RenderEffectStyle(kind = BuiltInEffectKind.Opacity, alpha = alpha)
    }

    fun colorFilter(color: Color, alpha: Float = 1f) {
        effects += RenderEffectStyle(kind = BuiltInEffectKind.ColorFilter, color = color, alpha = alpha)
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
        effects = effects.toList(),
        tint = tint,
        shaderId = shaderId,
        shaderUniforms = shaderUniforms.toMap(),
        beforeEffect = beforeEffect,
        afterEffect = afterEffect,
    )
}
