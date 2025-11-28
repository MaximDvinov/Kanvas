package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.kanvas.fx.render.RenderStyle
import com.kanvas.fx.render.Renderer2D

/**
 * Draws current animation frame for [entity], if present.
 */
fun Renderer2D.animatedSprite(
    entity: Entity,
    topLeft: Offset,
    size: Size,
    tint: Color = Color.White,
    preserveAspect: Boolean = false,
    rotationDegrees: Float = 0f,
    style: RenderStyle = RenderStyle(),
) {
    val frame = entity.currentAnimationFrameOrNull() ?: return
    texture(
        textureId = frame.textureId,
        topLeft = topLeft,
        size = size,
        tint = tint,
        preserveAspect = preserveAspect,
        rotationDegrees = rotationDegrees,
        style = style,
        sourceX = frame.sourceX,
        sourceY = frame.sourceY,
        sourceWidth = frame.sourceWidth,
        sourceHeight = frame.sourceHeight,
    )
}
