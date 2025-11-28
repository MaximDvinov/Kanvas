package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * 2D camera transform used by a scene renderer.
 *
 * @property position camera center in world coordinates.
 * @property zoom zoom factor where `1f` means 1:1 world-to-screen scale.
 * @property minZoom minimum zoom clamp.
 * @property maxZoom maximum zoom clamp.
 */
data class Camera2D(
    var position: Offset = Offset.Zero,
    var zoom: Float = 1f,
    var minZoom: Float = 0.1f,
    var maxZoom: Float = 8f,
) {
    /**
     * Sets absolute zoom and clamps it to `[minZoom, maxZoom]`.
     *
     * @param value requested zoom value.
     */
    fun setZoomLevel(value: Float) {
        zoom = value.coerceIn(minZoom, maxZoom)
    }

    /**
     * Adds delta to current zoom and applies clamp.
     *
     * @param delta zoom increment.
     */
    fun zoomBy(delta: Float) {
        setZoomLevel(zoom + delta)
    }

    /**
     * Applies multiplicative zoom and optionally keeps [anchorWorld] fixed on screen.
     *
     * @param factor multiplicative zoom factor.
     * @param anchorWorld world-space point to keep visually stable.
     */
    fun zoomByFactor(factor: Float, anchorWorld: Offset? = null) {
        if (!factor.isFinite() || factor <= 0f) return
        val oldZoom = zoom
        val newZoom = (oldZoom * factor).coerceIn(minZoom, maxZoom)
        if (abs(newZoom - oldZoom) < 1e-6f) return
        if (anchorWorld != null && oldZoom > 0f && newZoom > 0f) {
            val keepRatio = oldZoom / newZoom
            position = anchorWorld - (anchorWorld - position) * keepRatio
        }
        zoom = newZoom
    }

    /**
     * Pans camera by world-space offset.
     *
     * @param deltaWorld pan vector in world units.
     */
    fun panBy(deltaWorld: Offset) {
        position = position + deltaWorld
    }
}

private operator fun Offset.times(scale: Float): Offset = Offset(x * scale, y * scale)
