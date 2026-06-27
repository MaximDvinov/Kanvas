package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kanvas.fx.core.Camera2D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * World-space draw facade used by [com.kanvas.fx.core.RenderComponent] and entity render behaviors.
 *
 * Coordinate system:
 * - public primitive APIs accept world coordinates;
 * - camera transform (`position` + `zoom`) is applied internally;
 * - draw output is emitted into current Compose [DrawScope].
 *
 * Common flow per frame:
 * 1. [beginFrame]
 * 2. for each renderable entity: [beginEntity] -> primitive draws -> [endEntity]
 */
class Renderer2D(
    private val drawScope: DrawScope,
    val assets: AssetRegistry,
    private val camera: Camera2D,
) {
    data class FrameStats(
        val drawCalls: Int = 0,
        val materialPasses: Int = 0,
        val culledCount: Int = 0,
    )

    private companion object {
        const val TRAIL_FAST_PATH_SEGMENTS = 64
    }

    private var activeEntityId: String = ""
    private var activeMaterial: RenderMaterial? = null
    private var frameIndex: Long = 0L
    private var elapsedSeconds: Float = 0f
    private var sceneLights: List<Light2D> = emptyList()
    private var effectsEnabled: Boolean = true
    private var maxLightsPerObject: Int = 4
    private var glowQuality: Float = 1f
    private var drawCallsCounter: Int = 0
    private var materialPassCounter: Int = 0
    private var externalCulledCount: Int = 0
    var frameStats: FrameStats = FrameStats()
        private set

    data class WorldViewport(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    fun beginFrame(
        frameIndex: Long,
        elapsedSeconds: Float,
    ) {
        this.frameIndex = frameIndex
        this.elapsedSeconds = elapsedSeconds
        drawCallsCounter = 0
        materialPassCounter = 0
        externalCulledCount = 0
        frameStats = FrameStats()
    }

    fun beginEntity(
        entityId: String,
        material: RenderMaterial?,
    ) {
        activeEntityId = entityId
        activeMaterial = material
    }

    fun endEntity() {
        activeEntityId = ""
        activeMaterial = null
    }

    fun setSceneLights(lights: List<Light2D>) {
        sceneLights = lights
    }

    fun setExternallyCulledCount(value: Int) {
        externalCulledCount = value.coerceAtLeast(0)
        frameStats = frameStats.copy(culledCount = externalCulledCount)
    }

    fun configureQuality(
        effectsEnabled: Boolean,
        maxLightsPerObject: Int,
        glowQuality: Float,
    ) {
        this.effectsEnabled = effectsEnabled
        this.maxLightsPerObject = maxLightsPerObject.coerceAtLeast(0)
        this.glowQuality = glowQuality.coerceAtLeast(0f)
    }

    fun worldViewport(): WorldViewport {
        val halfWidth = drawScope.size.width * 0.5f / camera.zoom.coerceAtLeast(0.0001f)
        val halfHeight = drawScope.size.height * 0.5f / camera.zoom.coerceAtLeast(0.0001f)
        return WorldViewport(
            left = camera.position.x - halfWidth,
            top = camera.position.y - halfHeight,
            right = camera.position.x + halfWidth,
            bottom = camera.position.y + halfHeight,
        )
    }

    fun drawDebugOverlay(
        viewport: WorldViewport,
        renderedCount: Int,
        culledCount: Int,
    ) {
        val topLeft = worldToScreen(Offset(viewport.left, viewport.top))
        val bottomRight = worldToScreen(Offset(viewport.right, viewport.bottom))
        val width = (bottomRight.x - topLeft.x).coerceAtLeast(1f)
        val height = (bottomRight.y - topLeft.y).coerceAtLeast(1f)
        drawScope.drawRect(
            color = Color(0xFF56D8FF).copy(alpha = 0.65f),
            topLeft = topLeft,
            size = Size(width, height),
            style = Stroke(1.5f),
        )
        val total = (renderedCount + culledCount).coerceAtLeast(1)
        val culledRatio = culledCount.toFloat() / total.toFloat()
        val barWidth = 160f
        val barHeight = 8f
        val barTopLeft = Offset(12f, 12f)
        drawScope.drawRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = barTopLeft,
            size = Size(barWidth, barHeight),
            style = Fill,
        )
        drawScope.drawRect(
            color = Color(0xFFFFA24C).copy(alpha = 0.85f),
            topLeft = barTopLeft,
            size = Size(barWidth * culledRatio, barHeight),
            style = Fill,
        )
    }

    fun style(block: RenderStyleBuilder.() -> Unit): RenderStyle {
        val builder = RenderStyleBuilder()
        builder.block()
        return builder.build()
    }

    private fun worldToScreen(world: Offset): Offset {
        val viewportCenter = Offset(drawScope.size.width * 0.5f, drawScope.size.height * 0.5f)
        val dx = (world.x - camera.position.x) * camera.zoom
        val dy = (world.y - camera.position.y) * camera.zoom
        return Offset(viewportCenter.x + dx, viewportCenter.y + dy)
    }

    private fun styleBrush(style: RenderStyle): Brush {
        val gradient = style.gradient
        if (gradient != null && gradient.colors.isNotEmpty()) {
            return when (gradient.kind) {
                GradientKind.Linear -> {
                    val start = worldToScreen(gradient.start ?: Offset.Zero)
                    val end = worldToScreen(gradient.end ?: Offset(drawScope.size.width, drawScope.size.height))
                    Brush.linearGradient(colors = gradient.colors, start = start, end = end)
                }
                GradientKind.Radial -> {
                    val center = worldToScreen(gradient.center ?: Offset.Zero)
                    Brush.radialGradient(
                        colors = gradient.colors,
                        center = center,
                        radius = (gradient.radius ?: max(drawScope.size.width, drawScope.size.height)) * camera.zoom,
                    )
                }
                GradientKind.Sweep -> Brush.sweepGradient(
                    colors = gradient.colors,
                    center = worldToScreen(gradient.center ?: Offset.Zero),
                )
            }
        }
        return SolidColor(style.fillColor)
    }

    private fun styledColor(
        color: Color,
        effects: List<RenderEffectStyle>,
    ): Color {
        var out = color
        effects.forEach { effect ->
            when (effect.kind) {
                BuiltInEffectKind.Opacity -> out = out.copy(alpha = out.alpha * effect.alpha.coerceIn(0f, 1f))
                BuiltInEffectKind.ColorFilter -> {
                    val c = effect.color ?: return@forEach
                    val t = effect.alpha.coerceIn(0f, 1f)
                    out = Color(
                        red = out.red + (c.red - out.red) * t,
                        green = out.green + (c.green - out.green) * t,
                        blue = out.blue + (c.blue - out.blue) * t,
                        alpha = out.alpha,
                    )
                }
                else -> Unit
            }
        }
        return out
    }

    private fun legacyGlowEffect(
        glow: GlowStyle,
        fallbackStrokeWidth: Float,
    ): RenderEffectStyle {
        val baseRadius = glow.radius ?: fallbackStrokeWidth * glow.widthMultiplier
        return RenderEffectStyle(
            kind = BuiltInEffectKind.Bloom,
            color = glow.color,
            alpha = glow.alpha,
            radius = baseRadius,
            spread = glow.spread,
            passes = glow.passes,
            mode = glow.mode,
        )
    }

    private fun allEffects(
        style: RenderStyle,
        fallbackStrokeWidth: Float,
    ): List<RenderEffectStyle> {
        val legacy = style.glow?.let { listOf(legacyGlowEffect(it, fallbackStrokeWidth)) } ?: emptyList()
        return legacy + style.effects
    }

    private fun screenOffset(offset: Offset): Offset = Offset(offset.x * camera.zoom, offset.y * camera.zoom)

    private fun drawEffectGeometry(
        geometry: PrimitiveGeometry,
        color: Color,
        expansionPx: Float,
        strokeWidthPx: Float,
        blendMode: BlendMode = BlendMode.SrcOver,
        offsetPx: Offset = Offset.Zero,
        fill: Boolean = true,
    ) {
        when (geometry) {
            is PrimitiveGeometry.Circle -> drawScope.drawCircle(
                color = color,
                center = geometry.center + offsetPx,
                radius = (geometry.radius + expansionPx).coerceAtLeast(0f),
                style = if (fill) Fill else Stroke(strokeWidthPx + expansionPx),
                blendMode = blendMode,
            )
            is PrimitiveGeometry.Line -> drawScope.drawLine(
                color = color,
                start = geometry.start + offsetPx,
                end = geometry.end + offsetPx,
                strokeWidth = (strokeWidthPx + expansionPx * 2f).coerceAtLeast(0.1f),
                blendMode = blendMode,
            )
            is PrimitiveGeometry.Rect -> {
                val topLeft = geometry.topLeft + offsetPx - Offset(expansionPx, expansionPx)
                val size = Size(
                    width = geometry.size.width + expansionPx * 2f,
                    height = geometry.size.height + expansionPx * 2f,
                )
                drawScope.drawRect(
                    color = color,
                    topLeft = topLeft,
                    size = size,
                    style = if (fill) Fill else Stroke(strokeWidthPx + expansionPx),
                    blendMode = blendMode,
                )
            }
            is PrimitiveGeometry.Texture -> {
                val topLeft = geometry.topLeft + offsetPx - Offset(expansionPx, expansionPx)
                val size = Size(
                    width = geometry.size.width + expansionPx * 2f,
                    height = geometry.size.height + expansionPx * 2f,
                )
                drawScope.drawRect(
                    color = color,
                    topLeft = topLeft,
                    size = size,
                    style = if (fill) Fill else Stroke(strokeWidthPx + expansionPx),
                    blendMode = blendMode,
                )
            }
            is PrimitiveGeometry.Polygon -> drawScope.withTransform({
                translate(left = offsetPx.x, top = offsetPx.y)
            }) {
                drawPath(
                    path = geometry.path,
                    color = color,
                    style = if (fill) Fill else Stroke(strokeWidthPx + expansionPx),
                    blendMode = blendMode,
                )
            }
        }
    }

    private fun drawSoftEffect(
        geometry: PrimitiveGeometry,
        color: Color,
        radiusPx: Float,
        alpha: Float,
        passes: Int,
        spread: Float,
        strokeWidthPx: Float,
        blendMode: BlendMode,
        offsetPx: Offset = Offset.Zero,
    ) {
        if (geometry is PrimitiveGeometry.Circle && radiusPx > 0f) {
            val outerRadius = (geometry.radius + radiusPx * spread.coerceAtLeast(0f)).coerceAtLeast(geometry.radius)
            drawScope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (alpha * 0.52f).coerceIn(0f, 1f)),
                        color.copy(alpha = (alpha * 0.22f).coerceIn(0f, 1f)),
                        color.copy(alpha = (alpha * 0.07f).coerceIn(0f, 1f)),
                        color.copy(alpha = 0f),
                    ),
                    center = geometry.center + offsetPx,
                    radius = outerRadius,
                ),
                center = geometry.center + offsetPx,
                radius = outerRadius,
                blendMode = blendMode,
            )
            return
        }

        val safePasses = max(passes, 18).coerceIn(1, 32)
        repeat(safePasses) { index ->
            val t = (index + 1).toFloat() / safePasses.toFloat()
            val expansion = radiusPx * spread.coerceAtLeast(0f) * t
            val falloff = (1f - t).coerceIn(0f, 1f)
            val passAlpha = alpha * falloff * falloff / safePasses.toFloat()
            drawEffectGeometry(
                geometry = geometry,
                color = color.copy(alpha = passAlpha.coerceIn(0f, 1f)),
                expansionPx = expansion,
                strokeWidthPx = strokeWidthPx,
                blendMode = blendMode,
                offsetPx = offsetPx,
            )
        }
    }

    private fun drawBlurLikeEffect(
        geometry: PrimitiveGeometry,
        color: Color,
        radiusPx: Float,
        alpha: Float,
        passes: Int,
        spread: Float,
        mode: GlowMode,
        strokeWidthPx: Float,
        blendMode: BlendMode,
        offsetPx: Offset = Offset.Zero,
    ) {
        val shouldTryNative = mode == GlowMode.Blur || mode == GlowMode.Auto
        if (shouldTryNative && drawNativeBlurredGeometry(drawScope, geometry, color.copy(alpha = alpha), radiusPx, offsetPx, blendMode)) {
            return
        }
        drawSoftEffect(
            geometry = geometry,
            color = color,
            radiusPx = radiusPx,
            alpha = alpha,
            passes = passes,
            spread = spread,
            strokeWidthPx = strokeWidthPx,
            blendMode = blendMode,
            offsetPx = offsetPx,
        )
    }

    private fun applyBuiltInEffects(
        effects: List<RenderEffectStyle>,
        geometry: PrimitiveGeometry,
        baseColor: Color,
        strokeWidthPx: Float,
        beforeBase: Boolean,
    ) {
        if (!effectsEnabled) return
        effects.forEach { effect ->
            val color = effect.color ?: baseColor
            val radiusPx = effect.radius.coerceAtLeast(0f) * camera.zoom * glowQuality
            val offsetPx = screenOffset(effect.offset)
            when (effect.kind) {
                BuiltInEffectKind.Shadow -> if (beforeBase) {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = color,
                        radiusPx = radiusPx,
                        alpha = effect.alpha,
                        passes = effect.passes,
                        spread = effect.spread,
                        mode = effect.mode,
                        strokeWidthPx = strokeWidthPx,
                        blendMode = BlendMode.SrcOver,
                        offsetPx = offsetPx,
                    )
                }
                BuiltInEffectKind.Blur -> if (beforeBase) {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = baseColor,
                        radiusPx = radiusPx,
                        alpha = 0.22f * effect.alpha,
                        passes = effect.passes,
                        spread = effect.spread,
                        mode = effect.mode,
                        strokeWidthPx = strokeWidthPx,
                        blendMode = BlendMode.SrcOver,
                    )
                }
                BuiltInEffectKind.Bloom -> if (beforeBase) {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = color,
                        radiusPx = radiusPx,
                        alpha = effect.alpha,
                        passes = effect.passes,
                        spread = effect.spread,
                        mode = effect.mode,
                        strokeWidthPx = strokeWidthPx,
                        blendMode = BlendMode.Screen,
                    )
                }
                BuiltInEffectKind.Outline -> if (!beforeBase) {
                    drawEffectGeometry(
                        geometry = geometry,
                        color = color.copy(alpha = effect.alpha.coerceIn(0f, 1f)),
                        expansionPx = effect.width.coerceAtLeast(0f) * camera.zoom,
                        strokeWidthPx = effect.width.coerceAtLeast(0f) * camera.zoom,
                        fill = false,
                    )
                }
                BuiltInEffectKind.InnerShadow -> if (!beforeBase) {
                    drawEffectGeometry(
                        geometry = geometry,
                        color = color.copy(alpha = effect.alpha.coerceIn(0f, 1f)),
                        expansionPx = 0f,
                        strokeWidthPx = effect.width.coerceAtLeast(0f) * camera.zoom,
                        fill = false,
                    )
                }
                BuiltInEffectKind.Opacity,
                BuiltInEffectKind.ColorFilter,
                -> Unit
            }
        }
    }

    private fun maybeResolveShader(style: RenderStyle): ShaderAsset? {
        val id = style.shaderId ?: return null
        return assets.shader(id)
    }

    private fun applyRenderEffect(
        style: RenderStyle,
        phase: RenderPhase,
        primitive: PrimitiveKind,
        geometry: PrimitiveGeometry,
    ) {
        if (!effectsEnabled) return
        val shader = maybeResolveShader(style) ?: return
        val effect = (shader.source as? ShaderSource.ExternalDsl)?.value as? RenderEffect ?: return
        val shouldRun = when (phase) {
            RenderPhase.Pre -> style.beforeEffect
            RenderPhase.Post -> style.afterEffect
        }
        if (!shouldRun) return
        effect.apply(
            drawScope = drawScope,
            phase = phase,
            primitive = primitive,
            geometry = geometry,
        )
    }

    private fun runtimeBrush(
        style: RenderStyle,
        geometry: PrimitiveGeometry,
        effectBounds: Rect? = null,
    ): Brush? {
        if (!effectsEnabled) return null
        val shader = maybeResolveShader(style) ?: return null
        return assets.runtimeShaderBrush(
            shader = shader,
            geometry = geometry,
            uniforms = style.shaderUniforms,
            context = renderContextForGeometry(geometry, effectBounds),
        )
    }

    private fun renderContextForGeometry(
        geometry: PrimitiveGeometry,
        effectBounds: Rect? = null,
    ): RenderContext {
        val bounds = effectBounds ?: geometryBounds(geometry)
        val centerX = bounds.left + bounds.width * 0.5f
        val centerY = bounds.top + bounds.height * 0.5f
        val worldCenter = screenToWorld(Offset(centerX, centerY))
        val worldRadius = (max(bounds.width, bounds.height) * 0.5f) / camera.zoom.coerceAtLeast(0.0001f)
        return RenderContext(
            entityId = activeEntityId,
            frameIndex = frameIndex,
            elapsedSeconds = elapsedSeconds,
            worldCenterX = worldCenter.x,
            worldCenterY = worldCenter.y,
            worldRadius = worldRadius,
            screenLeft = bounds.left,
            screenTop = bounds.top,
            screenWidth = bounds.width,
            screenHeight = bounds.height,
            cameraX = camera.position.x,
            cameraY = camera.position.y,
            cameraZoom = camera.zoom,
        )
    }

    private fun screenToWorld(screen: Offset): Offset {
        val viewportCenter = Offset(drawScope.size.width * 0.5f, drawScope.size.height * 0.5f)
        return Offset(
            x = camera.position.x + (screen.x - viewportCenter.x) / camera.zoom,
            y = camera.position.y + (screen.y - viewportCenter.y) / camera.zoom,
        )
    }

    private fun geometryBounds(geometry: PrimitiveGeometry): Rect = when (geometry) {
        is PrimitiveGeometry.Circle -> Rect(
            left = geometry.center.x - geometry.radius,
            top = geometry.center.y - geometry.radius,
            right = geometry.center.x + geometry.radius,
            bottom = geometry.center.y + geometry.radius,
        )
        is PrimitiveGeometry.Line -> Rect(
            left = min(geometry.start.x, geometry.end.x),
            top = min(geometry.start.y, geometry.end.y),
            right = max(geometry.start.x, geometry.end.x),
            bottom = max(geometry.start.y, geometry.end.y),
        )
        is PrimitiveGeometry.Rect -> Rect(
            left = geometry.topLeft.x,
            top = geometry.topLeft.y,
            right = geometry.topLeft.x + geometry.size.width,
            bottom = geometry.topLeft.y + geometry.size.height,
        )
        is PrimitiveGeometry.Texture -> Rect(
            left = geometry.topLeft.x,
            top = geometry.topLeft.y,
            right = geometry.topLeft.x + geometry.size.width,
            bottom = geometry.topLeft.y + geometry.size.height,
        )
        is PrimitiveGeometry.Polygon -> geometry.path.getBounds()
    }

    private fun boundsWithPadding(
        bounds: Rect,
        paddingPx: Float,
    ): Rect = Rect(
        left = bounds.left - paddingPx,
        top = bounds.top - paddingPx,
        right = bounds.right + paddingPx,
        bottom = bounds.bottom + paddingPx,
    )

    private fun runMaterialPasses(
        phase: EffectPhase,
        geometry: PrimitiveGeometry,
        baseColor: Color,
    ) {
        if (!effectsEnabled) return
        if (geometry is PrimitiveGeometry.Line || geometry is PrimitiveGeometry.Polygon) return
        val material = activeMaterial ?: return
        val allPasses = material.effects.basePass +
            material.effects.emissionPass +
            material.effects.lightingPass +
            material.effects.postPass
        allPasses.filter { it.phase == phase }.forEach { pass ->
            materialPassCounter++
            val scaledPadding = pass.paddingPx * camera.zoom.coerceAtLeast(0.0001f)
            val bounds = boundsWithPadding(geometryBounds(geometry), scaledPadding)
            val circleCenter = Offset(bounds.left + bounds.width * 0.5f, bounds.top + bounds.height * 0.5f)
            val circleRadius = min(bounds.width, bounds.height) * 0.5f
            when (pass.kind) {
                EffectKind.Glow -> {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = pass.color,
                        radiusPx = (circleRadius * (0.8f + pass.intensity * 0.45f)).coerceAtLeast(scaledPadding),
                        alpha = (0.42f * pass.intensity).coerceIn(0f, 1f),
                        passes = 5,
                        spread = 1.4f,
                        mode = GlowMode.Auto,
                        strokeWidthPx = scaledPadding.coerceAtLeast(1f),
                        blendMode = pass.blendMode,
                    )
                }
                EffectKind.BloomLite -> {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = pass.color,
                        radiusPx = (circleRadius * (1.15f + pass.intensity * 0.35f)).coerceAtLeast(scaledPadding),
                        alpha = (0.22f * pass.intensity).coerceIn(0f, 1f),
                        passes = 6,
                        spread = 1.65f,
                        mode = GlowMode.Auto,
                        strokeWidthPx = scaledPadding.coerceAtLeast(1f),
                        blendMode = BlendMode.Screen,
                    )
                }
                EffectKind.Blur -> {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = baseColor,
                        radiusPx = pass.paddingPx.coerceAtLeast(pass.intensity) * camera.zoom * glowQuality,
                        alpha = (0.22f * pass.intensity).coerceIn(0f, 1f),
                        passes = 4,
                        spread = 1f,
                        mode = GlowMode.Auto,
                        strokeWidthPx = scaledPadding.coerceAtLeast(1f),
                        blendMode = pass.blendMode,
                    )
                }
                EffectKind.Shadow -> {
                    drawBlurLikeEffect(
                        geometry = geometry,
                        color = pass.color,
                        radiusPx = pass.paddingPx.coerceAtLeast(4f) * camera.zoom * glowQuality,
                        alpha = (0.35f * pass.intensity).coerceIn(0f, 1f),
                        passes = 4,
                        spread = 1f,
                        mode = GlowMode.Auto,
                        strokeWidthPx = scaledPadding.coerceAtLeast(1f),
                        blendMode = BlendMode.SrcOver,
                        offsetPx = Offset(
                            pass.paddingPx * 0.35f * camera.zoom,
                            pass.paddingPx * 0.35f * camera.zoom,
                        ),
                    )
                }
                EffectKind.Outline -> {
                    drawEffectGeometry(
                        geometry = geometry,
                        color = pass.color.copy(alpha = pass.intensity.coerceIn(0f, 1f)),
                        expansionPx = scaledPadding.coerceAtLeast(1f),
                        strokeWidthPx = scaledPadding.coerceAtLeast(1f),
                        blendMode = pass.blendMode,
                        fill = false,
                    )
                }
                EffectKind.Shader -> {
                    val shaderId = pass.shaderId ?: return@forEach
                    val style = RenderStyle(
                        fillColor = baseColor,
                        shaderId = shaderId,
                        shaderUniforms = pass.uniforms,
                        beforeEffect = false,
                        afterEffect = false,
                    )
                    val brush = runtimeBrush(style, geometry, effectBounds = bounds) ?: return@forEach
                    when (geometry) {
                        is PrimitiveGeometry.Circle -> {
                            drawScope.drawCircle(
                                brush = brush,
                                center = geometry.center,
                                radius = geometry.radius + scaledPadding,
                                blendMode = pass.blendMode,
                            )
                        }
                        is PrimitiveGeometry.Texture -> {
                            drawScope.drawCircle(
                                brush = brush,
                                center = circleCenter,
                                radius = circleRadius,
                                blendMode = pass.blendMode,
                            )
                        }
                        is PrimitiveGeometry.Rect,
                        -> {
                            drawScope.drawRect(
                                brush = brush,
                                topLeft = Offset(bounds.left, bounds.top),
                                size = Size(bounds.width, bounds.height),
                                blendMode = pass.blendMode,
                            )
                        }
                    }
                }
            }
        }

        if (material.lighting.enabled && phase == EffectPhase.AfterBase) {
            val bounds = geometryBounds(geometry)
            val center = Offset(bounds.left + bounds.width * 0.5f, bounds.top + bounds.height * 0.5f)
            val radius = min(bounds.width, bounds.height) * 0.5f
            val drawAsCircle = geometry is PrimitiveGeometry.Circle || geometry is PrimitiveGeometry.Texture
            val objectWorld = screenToWorld(center)
            val lightHits = sceneLights
                .asSequence()
                .map { light ->
                    val dx = light.x - objectWorld.x
                    val dy = light.y - objectWorld.y
                    val d = sqrt(dx * dx + dy * dy)
                    val influence = (1f - d / light.radius).coerceIn(0f, 1f) * light.intensity
                    influence to light.color
                }
                .filter { it.first > 0f }
                .sortedByDescending { it.first }
                .take(material.lighting.maxLightsPerObject)
                .take(maxLightsPerObject)
                .toList()
            lightHits.forEach { (v, c) ->
                if (drawAsCircle) {
                    drawScope.drawCircle(
                        color = c.copy(alpha = (0.08f * v).coerceIn(0f, 0.4f)),
                        center = center,
                        radius = radius,
                        blendMode = BlendMode.Screen,
                    )
                } else {
                    drawScope.drawRect(
                        color = c.copy(alpha = (0.08f * v).coerceIn(0f, 0.4f)),
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
            if (drawAsCircle) {
                drawScope.drawCircle(
                    color = material.lighting.ambientColor.copy(
                        alpha = material.lighting.ambientIntensity.coerceIn(0f, 1f),
                    ),
                    center = center,
                    radius = radius,
                    blendMode = BlendMode.SrcOver,
                )
            } else {
                drawScope.drawRect(
                    color = material.lighting.ambientColor.copy(
                        alpha = material.lighting.ambientIntensity.coerceIn(0f, 1f),
                    ),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    blendMode = BlendMode.SrcOver,
                )
            }
        }
    }

    fun circle(
        center: Offset,
        radius: Float,
        color: Color,
        style: RenderStyle = RenderStyle(),
    ) {
        val baseStyle = style.copy(fillColor = color)
        maybeResolveShader(baseStyle)
        val screenCenter = worldToScreen(center)
        val screenRadius = radius * camera.zoom
        val geometry = PrimitiveGeometry.Circle(
            center = screenCenter,
            radius = screenRadius,
        )
        val effects = allEffects(baseStyle, baseStyle.stroke?.width ?: 2f)
        val effectiveColor = styledColor(color, effects)
        val effectiveStyle = baseStyle.copy(fillColor = effectiveColor)
        runMaterialPasses(EffectPhase.BeforeBase, geometry, effectiveColor)
        applyRenderEffect(effectiveStyle, RenderPhase.Pre, PrimitiveKind.Circle, geometry)
        applyBuiltInEffects(effects, geometry, effectiveColor, (baseStyle.stroke?.width ?: 2f) * camera.zoom, beforeBase = true)
        val brush = runtimeBrush(effectiveStyle, geometry) ?: styleBrush(effectiveStyle)
        drawScope.drawCircle(
            brush = brush,
            center = screenCenter,
            radius = screenRadius,
        )
        baseStyle.stroke?.let { stroke ->
            drawScope.drawCircle(
                color = stroke.color,
                center = screenCenter,
                radius = screenRadius,
                style = Stroke(stroke.width * camera.zoom),
            )
        }
        applyBuiltInEffects(effects, geometry, effectiveColor, (baseStyle.stroke?.width ?: 2f) * camera.zoom, beforeBase = false)
        applyRenderEffect(effectiveStyle, RenderPhase.Post, PrimitiveKind.Circle, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, effectiveColor)
        onDrawCall()
    }

    fun line(
        start: Offset,
        end: Offset,
        color: Color,
        strokeWidth: Float = 1f,
        style: RenderStyle = RenderStyle(),
    ) {
        val baseStyle = style.copy(fillColor = color)
        maybeResolveShader(baseStyle)
        val s = worldToScreen(start)
        val e = worldToScreen(end)
        val width = baseStyle.stroke?.width ?: strokeWidth
        val geometry = PrimitiveGeometry.Line(
            start = s,
            end = e,
            strokeWidth = width,
        )
        val effects = allEffects(baseStyle, width)
        val effectiveColor = styledColor(color, effects)
        val effectiveStyle = baseStyle.copy(fillColor = effectiveColor)
        runMaterialPasses(EffectPhase.BeforeBase, geometry, effectiveColor)
        applyRenderEffect(effectiveStyle, RenderPhase.Pre, PrimitiveKind.Line, geometry)
        applyBuiltInEffects(effects, geometry, effectiveColor, width, beforeBase = true)
        val brush = if (effectiveStyle.gradient != null) styleBrush(effectiveStyle) else SolidColor(effectiveColor)
        drawScope.drawLine(
            brush = brush,
            start = s,
            end = e,
            strokeWidth = width,
        )
        applyBuiltInEffects(effects, geometry, effectiveColor, width, beforeBase = false)
        applyRenderEffect(effectiveStyle, RenderPhase.Post, PrimitiveKind.Line, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, effectiveColor)
        onDrawCall()
    }

    fun rect(
        topLeft: Offset,
        size: Size,
        color: Color,
        strokeWidth: Float? = null,
        style: RenderStyle = RenderStyle(),
    ) {
        val baseStyle = style.copy(fillColor = color)
        maybeResolveShader(baseStyle)
        val screenTopLeft = worldToScreen(topLeft)
        val screenSize = Size(size.width * camera.zoom, size.height * camera.zoom)
        val geometry = PrimitiveGeometry.Rect(
            topLeft = screenTopLeft,
            size = screenSize,
        )
        val fallbackStroke = baseStyle.stroke?.width ?: strokeWidth ?: 2f
        val effects = allEffects(baseStyle, fallbackStroke)
        val effectiveColor = styledColor(color, effects)
        val effectiveStyle = baseStyle.copy(fillColor = effectiveColor)
        runMaterialPasses(EffectPhase.BeforeBase, geometry, effectiveColor)
        applyRenderEffect(effectiveStyle, RenderPhase.Pre, PrimitiveKind.Rect, geometry)
        applyBuiltInEffects(effects, geometry, effectiveColor, fallbackStroke * camera.zoom, beforeBase = true)
        val brush = runtimeBrush(effectiveStyle, geometry) ?: styleBrush(effectiveStyle)
        drawScope.drawRect(
            brush = brush,
            topLeft = screenTopLeft,
            size = screenSize,
            style = Fill,
        )
        val stroke = baseStyle.stroke ?: strokeWidth?.let { StrokeStyle(color = color, width = it) }
        stroke?.let {
            drawScope.drawRect(
                color = it.color,
                topLeft = screenTopLeft,
                size = screenSize,
                style = Stroke(it.width * camera.zoom),
            )
        }
        applyBuiltInEffects(effects, geometry, effectiveColor, fallbackStroke * camera.zoom, beforeBase = false)
        applyRenderEffect(effectiveStyle, RenderPhase.Post, PrimitiveKind.Rect, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, effectiveColor)
        onDrawCall()
    }

    fun polygon(
        points: List<Offset>,
        color: Color,
        strokeWidth: Float? = null,
        style: RenderStyle = RenderStyle(),
    ) {
        if (points.size < 3) return
        val baseStyle = style.copy(fillColor = color)
        maybeResolveShader(baseStyle)
        val path = Path().apply {
            val first = worldToScreen(points.first())
            moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val p = worldToScreen(points[i])
                lineTo(p.x, p.y)
            }
            close()
        }
        val geometry = PrimitiveGeometry.Polygon(path = path)
        val fallbackStroke = baseStyle.stroke?.width ?: strokeWidth ?: 2f
        val effects = allEffects(baseStyle, fallbackStroke)
        val effectiveColor = styledColor(color, effects)
        val effectiveStyle = baseStyle.copy(fillColor = effectiveColor)
        runMaterialPasses(EffectPhase.BeforeBase, geometry, effectiveColor)
        applyRenderEffect(effectiveStyle, RenderPhase.Pre, PrimitiveKind.Polygon, geometry)
        applyBuiltInEffects(effects, geometry, effectiveColor, fallbackStroke * camera.zoom, beforeBase = true)
        drawScope.drawPath(path = path, brush = styleBrush(effectiveStyle), style = Fill)
        val stroke = baseStyle.stroke ?: strokeWidth?.let { StrokeStyle(color = color, width = it) }
        stroke?.let {
            drawScope.drawPath(
                path = path,
                color = it.color,
                style = Stroke(it.width * camera.zoom),
            )
        }
        applyBuiltInEffects(effects, geometry, effectiveColor, fallbackStroke * camera.zoom, beforeBase = false)
        applyRenderEffect(effectiveStyle, RenderPhase.Post, PrimitiveKind.Polygon, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, effectiveColor)
        onDrawCall()
    }

    /**
     * Draw texture by asset id. Works for [TextureSource.Bitmap] out of the box.
     */
    fun texture(
        textureId: String,
        topLeft: Offset,
        size: Size,
        tint: Color = Color.White,
        preserveAspect: Boolean = false,
        rotationDegrees: Float = 0f,
        style: RenderStyle = RenderStyle(),
        sourceX: Int = 0,
        sourceY: Int = 0,
        sourceWidth: Int? = null,
        sourceHeight: Int? = null,
    ) {
        val texture = assets.texture(textureId) ?: return
        val baseStyle = style.copy(tint = tint)
        maybeResolveShader(baseStyle)
        when (val source = texture.source) {
            is TextureSource.Bitmap -> {
                val bitmapWidth = source.value.width.coerceAtLeast(1)
                val bitmapHeight = source.value.height.coerceAtLeast(1)
                val safeSourceX = sourceX.coerceIn(0, bitmapWidth - 1)
                val safeSourceY = sourceY.coerceIn(0, bitmapHeight - 1)
                val safeSourceWidth = (sourceWidth ?: (bitmapWidth - safeSourceX)).coerceAtLeast(1)
                    .coerceAtMost(bitmapWidth - safeSourceX)
                val safeSourceHeight = (sourceHeight ?: (bitmapHeight - safeSourceY)).coerceAtLeast(1)
                    .coerceAtMost(bitmapHeight - safeSourceY)
                val requestedTopLeft = worldToScreen(topLeft)
                val requestedSize = Size(size.width * camera.zoom, size.height * camera.zoom)
                val dst = if (preserveAspect) {
                    val srcWidth = safeSourceWidth.toFloat().coerceAtLeast(1f)
                    val srcHeight = safeSourceHeight.toFloat().coerceAtLeast(1f)
                    val srcAspect = srcWidth / srcHeight
                    val boxAspect = (requestedSize.width / requestedSize.height).coerceAtLeast(0.0001f)
                    if (srcAspect > boxAspect) {
                        val fittedHeight = requestedSize.width / srcAspect
                        val offsetY = (requestedSize.height - fittedHeight) * 0.5f
                        Offset(requestedTopLeft.x, requestedTopLeft.y + offsetY) to Size(requestedSize.width, fittedHeight)
                    } else {
                        val fittedWidth = requestedSize.height * srcAspect
                        val offsetX = (requestedSize.width - fittedWidth) * 0.5f
                        Offset(requestedTopLeft.x + offsetX, requestedTopLeft.y) to Size(fittedWidth, requestedSize.height)
                    }
                } else {
                    requestedTopLeft to requestedSize
                }
                val dstTopLeft = dst.first
                val dstSize = dst.second
                val geometry = PrimitiveGeometry.Texture(
                    topLeft = dstTopLeft,
                    size = dstSize,
                )
                val fallbackStroke = baseStyle.stroke?.width ?: 2f
                val effects = allEffects(baseStyle, fallbackStroke)
                val effectiveTint = styledColor(tint, effects)
                val effectiveStyle = baseStyle.copy(tint = effectiveTint, fillColor = effectiveTint)
                runMaterialPasses(EffectPhase.BeforeBase, geometry, effectiveTint)
                applyRenderEffect(effectiveStyle, RenderPhase.Pre, PrimitiveKind.Texture, geometry)
                val pivot = Offset(
                    x = dstTopLeft.x + dstSize.width * 0.5f,
                    y = dstTopLeft.y + dstSize.height * 0.5f,
                )
                applyBuiltInEffects(effects, geometry, effectiveTint, fallbackStroke * camera.zoom, beforeBase = true)
                drawScope.withTransform({
                    rotate(degrees = rotationDegrees, pivot = pivot)
                }) {
                    drawImage(
                        image = source.value,
                        srcOffset = IntOffset(safeSourceX, safeSourceY),
                        srcSize = IntSize(safeSourceWidth, safeSourceHeight),
                        dstOffset = IntOffset(
                            dstTopLeft.x.toInt(),
                            dstTopLeft.y.toInt(),
                        ),
                        dstSize = IntSize(
                            width = dstSize.width.toInt().coerceAtLeast(1),
                            height = dstSize.height.toInt().coerceAtLeast(1),
                        ),
                        colorFilter = if (effectiveStyle.tint == Color.White) null else ColorFilter.tint(effectiveStyle.tint),
                    )
                }
                applyBuiltInEffects(effects, geometry, effectiveTint, fallbackStroke * camera.zoom, beforeBase = false)
                applyRenderEffect(effectiveStyle, RenderPhase.Post, PrimitiveKind.Texture, geometry)
                runMaterialPasses(EffectPhase.AfterBase, geometry, effectiveTint)
                baseStyle.stroke?.let { stroke ->
                    drawScope.withTransform({
                        rotate(degrees = rotationDegrees, pivot = pivot)
                    }) {
                        drawRect(
                            color = stroke.color,
                            topLeft = dstTopLeft,
                            size = dstSize,
                            style = Stroke(stroke.width * camera.zoom),
                        )
                    }
                }
                onDrawCall()
            }

            is TextureSource.Path -> Unit
        }
    }

    fun vector(
        start: Offset,
        end: Offset,
        color: Color,
        arrowSize: Float = 10f,
        strokeWidth: Float = 2f,
        style: RenderStyle = RenderStyle(),
    ) {
        line(start, end, color = color, strokeWidth = strokeWidth, style = style)
        val dir = end - start
        val len = sqrt(dir.x * dir.x + dir.y * dir.y)
        if (len < 0.001f) return
        val nx = dir.x / len
        val ny = dir.y / len
        val px = -ny
        val py = nx
        val tip = end
        val base = end - Offset(nx * arrowSize, ny * arrowSize)
        val wing = arrowSize * 0.55f
        line(base + Offset(px * wing, py * wing), tip, color = color, strokeWidth = strokeWidth, style = style)
        line(base - Offset(px * wing, py * wing), tip, color = color, strokeWidth = strokeWidth, style = style)
    }

    fun trail(
        points: List<Offset>,
        color: Color,
        strokeWidth: Float = 2f,
        style: RenderStyle = RenderStyle(),
    ) {
        if (points.size < 2) return
        val count = points.size - 1
        if (count > TRAIL_FAST_PATH_SEGMENTS) {
            val step = (count.toFloat() / TRAIL_FAST_PATH_SEGMENTS.toFloat()).toInt().coerceAtLeast(1)
            val glow = style.glow
            var i = 0
            while (i < count) {
                val next = min(i + step, count)
                val t = next.toFloat() / count.toFloat()
                val alpha = 0.08f + t * 0.8f
                val start = worldToScreen(points[i])
                val end = worldToScreen(points[next])
                glow?.let {
                    drawScope.drawLine(
                        color = (it.color ?: color).copy(alpha = (it.alpha * 0.35f).coerceIn(0f, 1f)),
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth * it.widthMultiplier,
                    )
                }
                drawScope.drawLine(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                )
                i = next
            }
            return
        }
        for (i in 0 until count) {
            val t0 = i.toFloat() / count.toFloat()
            val t1 = (i + 1).toFloat() / count.toFloat()
            val c0 = color.copy(alpha = 0.05f + t0 * 0.55f)
            val c1 = color.copy(alpha = 0.08f + t1 * 0.85f)
            val segStyle = style.copy(
                gradient = GradientStyle(
                    colors = listOf(c0, c1),
                    start = points[i],
                    end = points[i + 1],
                ),
            )
            line(
                start = points[i],
                end = points[i + 1],
                color = color,
                strokeWidth = strokeWidth,
                style = segStyle,
            )
        }
    }

    private fun onDrawCall() {
        drawCallsCounter++
        frameStats = FrameStats(
            drawCalls = drawCallsCounter,
            materialPasses = materialPassCounter,
            culledCount = externalCulledCount,
        )
    }
}
