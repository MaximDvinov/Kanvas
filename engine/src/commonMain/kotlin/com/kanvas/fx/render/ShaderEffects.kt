package com.kanvas.fx.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Render phase passed into [RenderEffect].
 */
enum class RenderPhase {
    /** Effect callback before primitive base draw. */
    Pre,
    /** Effect callback after primitive base draw. */
    Post,
}

/**
 * Primitive kind passed into [RenderEffect].
 */
enum class PrimitiveKind {
    Circle,
    Line,
    Rect,
    Polygon,
    Texture,
}

/**
 * Geometry payloads provided to [RenderEffect].
 */
sealed interface PrimitiveGeometry {
    /**
     * Circle geometry.
     *
     * @property center screen-space center.
     * @property radius screen-space radius.
     */
    data class Circle(
        val center: Offset,
        val radius: Float,
    ) : PrimitiveGeometry

    /**
     * Line geometry.
     *
     * @property start screen-space start point.
     * @property end screen-space end point.
     * @property strokeWidth screen-space stroke width.
     */
    data class Line(
        val start: Offset,
        val end: Offset,
        val strokeWidth: Float,
    ) : PrimitiveGeometry

    /**
     * Rectangle geometry.
     *
     * @property topLeft screen-space top-left point.
     * @property size screen-space size.
     */
    data class Rect(
        val topLeft: Offset,
        val size: Size,
    ) : PrimitiveGeometry

    /**
     * Polygon geometry.
     *
     * @property path screen-space polygon path.
     */
    data class Polygon(
        val path: Path,
    ) : PrimitiveGeometry

    /**
     * Texture destination geometry.
     *
     * @property topLeft screen-space destination top-left.
     * @property size screen-space destination size.
     */
    data class Texture(
        val topLeft: Offset,
        val size: Size,
    ) : PrimitiveGeometry
}

/**
 * Generic extension point for custom render effects.
 *
 * Engine only provides invocation lifecycle. Concrete effect logic belongs to app side.
 */
fun interface RenderEffect {
    /**
     * Applies effect hook for one primitive.
     *
     * @param drawScope compose draw scope.
     * @param phase draw lifecycle phase.
     * @param primitive primitive type.
     * @param geometry primitive geometry payload.
     */
    fun apply(
        drawScope: DrawScope,
        phase: RenderPhase,
        primitive: PrimitiveKind,
        geometry: PrimitiveGeometry,
    )
}
