package com.kanvas.fx.render

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope

internal actual fun drawNativeBlurredGeometry(
    drawScope: DrawScope,
    geometry: PrimitiveGeometry,
    color: Color,
    radiusPx: Float,
    offsetPx: Offset,
    blendMode: BlendMode,
): Boolean {
    if (radiusPx <= 0f || color.alpha <= 0f) return false
    val canvas = drawScope.drawContext.canvas.nativeCanvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
        style = Paint.Style.FILL
        blendMode.toPorterDuffMode()?.let { xfermode = PorterDuffXfermode(it) }
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
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = geometry.strokeWidth
            paint.strokeCap = Paint.Cap.ROUND
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
            canvas.drawPath(geometry.path.asAndroidPath(), paint)
            canvas.restore()
        }
    }
    return true
}

private fun BlendMode.toPorterDuffMode(): PorterDuff.Mode? = when (this) {
    BlendMode.Screen -> PorterDuff.Mode.SCREEN
    BlendMode.Plus -> PorterDuff.Mode.ADD
    BlendMode.Multiply -> PorterDuff.Mode.MULTIPLY
    else -> null
}
