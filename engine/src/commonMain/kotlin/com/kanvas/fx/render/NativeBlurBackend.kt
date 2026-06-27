package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

internal expect fun drawNativeBlurredGeometry(
    drawScope: DrawScope,
    geometry: PrimitiveGeometry,
    color: Color,
    radiusPx: Float,
    offsetPx: Offset,
    blendMode: BlendMode,
): Boolean
