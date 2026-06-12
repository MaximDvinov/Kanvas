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
            val start = worldToScreen(gradient.start ?: Offset.Zero)
            val end = worldToScreen(gradient.end ?: Offset(drawScope.size.width, drawScope.size.height))
            return Brush.linearGradient(
                colors = gradient.colors,
                start = start,
                end = end,
            )
        }
        return SolidColor(style.fillColor)
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
            val drawAsCircle = geometry is PrimitiveGeometry.Circle || geometry is PrimitiveGeometry.Texture
            when (pass.kind) {
                EffectKind.Glow -> {
                    if (drawAsCircle) {
                        val glowRadius = circleRadius * (1.9f + pass.intensity * 0.35f)
                        val glowBrush = Brush.radialGradient(
                            colors = listOf(
                                pass.color.copy(alpha = (0.26f * pass.intensity).coerceIn(0f, 0.72f)),
                                pass.color.copy(alpha = (0.11f * pass.intensity).coerceIn(0f, 0.45f)),
                                pass.color.copy(alpha = (0.03f * pass.intensity).coerceIn(0f, 0.2f)),
                                pass.color.copy(alpha = 0f),
                            ),
                            center = circleCenter,
                            radius = glowRadius,
                        )
                        drawScope.drawCircle(
                            brush = glowBrush,
                            center = circleCenter,
                            radius = glowRadius,
                            blendMode = pass.blendMode,
                        )
                    } else {
                        drawScope.drawRect(
                            color = pass.color.copy(alpha = 0.14f * pass.intensity),
                            topLeft = Offset(bounds.left, bounds.top),
                            size = Size(bounds.width, bounds.height),
                            blendMode = pass.blendMode,
                        )
                    }
                }
                EffectKind.BloomLite -> {
                    if (drawAsCircle) {
                        val bloomRadius = circleRadius * (2.35f + pass.intensity * 0.25f)
                        val bloomBrush = Brush.radialGradient(
                            colors = listOf(
                                pass.color.copy(alpha = (0.09f * pass.intensity).coerceIn(0f, 0.3f)),
                                pass.color.copy(alpha = (0.035f * pass.intensity).coerceIn(0f, 0.16f)),
                                pass.color.copy(alpha = 0f),
                            ),
                            center = circleCenter,
                            radius = bloomRadius,
                        )
                        drawScope.drawCircle(
                            brush = bloomBrush,
                            center = circleCenter,
                            radius = bloomRadius,
                            blendMode = BlendMode.Screen,
                        )
                    } else {
                        drawScope.drawRect(
                            color = pass.color.copy(alpha = 0.09f * pass.intensity),
                            topLeft = Offset(bounds.left, bounds.top),
                            size = Size(bounds.width, bounds.height),
                            blendMode = BlendMode.Screen,
                        )
                    }
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
                        else -> Unit
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
        runMaterialPasses(EffectPhase.BeforeBase, geometry, color)
        applyRenderEffect(baseStyle, RenderPhase.Pre, PrimitiveKind.Circle, geometry)
        val brush = runtimeBrush(baseStyle, geometry) ?: styleBrush(baseStyle)
        baseStyle.glow?.let { glow ->
            drawScope.drawCircle(
                brush = SolidColor((glow.color ?: color).copy(alpha = glow.alpha)),
                center = screenCenter,
                radius = screenRadius + (baseStyle.stroke?.width ?: 2f) * glow.widthMultiplier * glowQuality * camera.zoom,
            )
        }
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
        applyRenderEffect(baseStyle, RenderPhase.Post, PrimitiveKind.Circle, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, color)
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
        runMaterialPasses(EffectPhase.BeforeBase, geometry, color)
        applyRenderEffect(baseStyle, RenderPhase.Pre, PrimitiveKind.Line, geometry)
        val brush = if (baseStyle.gradient != null) styleBrush(baseStyle) else SolidColor(color)
        baseStyle.glow?.let { glow ->
            drawScope.drawLine(
                color = (glow.color ?: color).copy(alpha = glow.alpha),
                start = s,
                end = e,
                strokeWidth = width * glow.widthMultiplier,
            )
        }
        drawScope.drawLine(
            brush = brush,
            start = s,
            end = e,
            strokeWidth = width,
        )
        applyRenderEffect(baseStyle, RenderPhase.Post, PrimitiveKind.Line, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, color)
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
        runMaterialPasses(EffectPhase.BeforeBase, geometry, color)
        applyRenderEffect(baseStyle, RenderPhase.Pre, PrimitiveKind.Rect, geometry)
        val brush = runtimeBrush(baseStyle, geometry) ?: styleBrush(baseStyle)
        baseStyle.glow?.let { glow ->
            drawScope.drawRect(
                color = (glow.color ?: color).copy(alpha = glow.alpha),
                topLeft = screenTopLeft,
                size = screenSize,
                style = Stroke(((baseStyle.stroke?.width ?: strokeWidth ?: 2f) * glow.widthMultiplier) * camera.zoom),
            )
        }
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
        applyRenderEffect(baseStyle, RenderPhase.Post, PrimitiveKind.Rect, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, color)
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
        runMaterialPasses(EffectPhase.BeforeBase, geometry, color)
        applyRenderEffect(baseStyle, RenderPhase.Pre, PrimitiveKind.Polygon, geometry)
        baseStyle.glow?.let { glow ->
            drawScope.drawPath(
                path = path,
                color = (glow.color ?: color).copy(alpha = glow.alpha),
                style = Stroke(((baseStyle.stroke?.width ?: strokeWidth ?: 2f) * glow.widthMultiplier) * camera.zoom),
            )
        }
        drawScope.drawPath(path = path, brush = styleBrush(baseStyle), style = Fill)
        val stroke = baseStyle.stroke ?: strokeWidth?.let { StrokeStyle(color = color, width = it) }
        stroke?.let {
            drawScope.drawPath(
                path = path,
                color = it.color,
                style = Stroke(it.width * camera.zoom),
            )
        }
        applyRenderEffect(baseStyle, RenderPhase.Post, PrimitiveKind.Polygon, geometry)
        runMaterialPasses(EffectPhase.AfterBase, geometry, color)
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
                runMaterialPasses(EffectPhase.BeforeBase, geometry, tint)
                applyRenderEffect(baseStyle, RenderPhase.Pre, PrimitiveKind.Texture, geometry)
                val pivot = Offset(
                    x = dstTopLeft.x + dstSize.width * 0.5f,
                    y = dstTopLeft.y + dstSize.height * 0.5f,
                )
                baseStyle.glow?.let { glow ->
                    drawScope.withTransform({
                        rotate(degrees = rotationDegrees, pivot = pivot)
                    }) {
                        drawRect(
                            color = (glow.color ?: Color.White).copy(alpha = glow.alpha),
                            topLeft = dstTopLeft,
                            size = dstSize,
                            style = Stroke((baseStyle.stroke?.width ?: 2f) * glow.widthMultiplier),
                        )
                    }
                }
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
                        colorFilter = if (baseStyle.tint == Color.White) null else ColorFilter.tint(baseStyle.tint),
                    )
                }
                applyRenderEffect(baseStyle, RenderPhase.Post, PrimitiveKind.Texture, geometry)
                runMaterialPasses(EffectPhase.AfterBase, geometry, tint)
                baseStyle.stroke?.let { stroke ->
                    drawScope.withTransform({
                        rotate(degrees = rotationDegrees, pivot = pivot)
                    }) {
                        drawRect(
                            color = stroke.color,
                            topLeft = dstTopLeft,
                            size = dstSize,
                            style = Stroke(stroke.width),
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
