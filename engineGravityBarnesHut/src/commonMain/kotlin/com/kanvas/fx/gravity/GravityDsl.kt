package com.kanvas.fx.gravity

import com.kanvas.fx.dsl.SystemsBuilder
import com.kanvas.fx.core.SystemPhase

/**
 * Adds Barnes-Hut gravity as a scene system through the generic systems DSL.
 */
fun SystemsBuilder.gravityBarnesHut(
    bodiesProvider: () -> List<GravityBody>,
    gravityConstant: Float = 3_800f,
    softening: Float = 28f,
    theta: Float = 0.65f,
) {
    add(
        id = "gravity-barnes-hut",
        phase = SystemPhase.PrePhysics,
        order = 0,
        system = BarnesHutGravitySystem(
            bodiesProvider = bodiesProvider,
            gravityConstant = gravityConstant,
            softening = softening,
            theta = theta,
        ),
    )
}
