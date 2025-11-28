package com.kanvas.fx.gravity

import androidx.compose.ui.geometry.Offset

/**
 * Mutable body state used by gravity simulation systems.
 */
data class GravityBody(
    val id: String,
    var position: Offset,
    var velocity: Offset,
    var mass: Float,
)
