package com.kanvas.fx.physics

import com.kanvas.fx.dsl.SystemsBuilder
import com.kanvas.fx.core.SystemPhase

/**
 * Adds a generic 2D physics world stepper system.
 */
fun SystemsBuilder.physics2d(
    world: PhysicsWorld2D,
    id: String = "physics2d",
    order: Int = 0,
) {
    add(
        id = id,
        phase = SystemPhase.Physics,
        order = order,
        system = PhysicsSystem2D(world),
    )
}

fun physicsWorld2dNoBroadphase(
    gravity: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(0f, 400f),
    iterations: Int = 4,
): PhysicsWorld2D = PhysicsWorld2D(
    gravity = gravity,
    iterations = iterations,
    broadphaseStrategy = NoBroadphase,
)

fun physicsWorld2dUniformGrid(
    gravity: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(0f, 400f),
    iterations: Int = 4,
    cellSize: Float = 256f,
): PhysicsWorld2D = PhysicsWorld2D(
    gravity = gravity,
    iterations = iterations,
    broadphaseStrategy = UniformGridBroadphase(cellSize = cellSize),
)
