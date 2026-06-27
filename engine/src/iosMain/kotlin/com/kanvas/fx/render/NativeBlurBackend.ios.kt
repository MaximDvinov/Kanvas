package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.BlendMode as SkiaBlendMode

internal actual fun drawNativeBlurredGeometry(
    drawScope: DrawScope,
    geometry: PrimitiveGeometry,
    color: Color,
    radiusPx: Float,
    offsetPx: Offset,
    blendMode: BlendMode,
): Boolean {
    if (radiusPx <= 0f || color.alpha <= 0f) return false
    val canvas = drawScope.drawContext.canvas.skiaCanvas
    val sigma = MaskFilter.convertRadiusToSigma(radiusPx)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        mode = PaintMode.FILL
        this.blendMode = blendMode.toSkiaBlendMode()
        maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma, true)
    }
    when (geometry) {
        is PrimitiveGeometry.Circle -> canvas.drawCircle(
            geometry.center.x + offsetPx.x,
            geometry.center.y + offsetPx.y,
            geometry.radius,
            paint,
        )
        is PrimitiveGeometry.Rect -> canvas.drawRect(
            geometry.topLeft.x + offsetPx.x,
            geometry.topLeft.y + offsetPx.y,
            geometry.topLeft.x + geometry.size.width + offsetPx.x,
            geometry.topLeft.y + geometry.size.height + offsetPx.y,
            paint,
        )
        is PrimitiveGeometry.Texture -> canvas.drawRect(
            geometry.topLeft.x + offsetPx.x,
            geometry.topLeft.y + offsetPx.y,
            geometry.topLeft.x + geometry.size.width + offsetPx.x,
            geometry.topLeft.y + geometry.size.height + offsetPx.y,
            paint,
        )
        is PrimitiveGeometry.Line -> {
            paint.mode = PaintMode.STROKE
            paint.strokeWidth = geometry.strokeWidth
            paint.strokeCap = PaintStrokeCap.ROUND
            canvas.drawLine(
                geometry.start.x + offsetPx.x,
                geometry.start.y + offsetPx.y,
                geometry.end.x + offsetPx.x,
                geometry.end.y + offsetPx.y,
                paint,
            )
        }
        is PrimitiveGeometry.Polygon -> {
            canvas.save()
            canvas.translate(offsetPx.x, offsetPx.y)
            canvas.drawPath(geometry.path.asSkiaPath(), paint)
            canvas.restore()
        }
    }
    return true
}

private fun BlendMode.toSkiaBlendMode(): SkiaBlendMode = when (this) {
    BlendMode.Screen -> SkiaBlendMode.SCREEN
    BlendMode.Plus -> SkiaBlendMode.PLUS
    BlendMode.Multiply -> SkiaBlendMode.MULTIPLY
    else -> SkiaBlendMode.SRC_OVER
}
