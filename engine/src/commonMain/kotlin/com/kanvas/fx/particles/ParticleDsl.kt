package com.kanvas.fx.particles

import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.dsl.SystemsBuilder
import com.kanvas.fx.render.RenderStyle

/**
 * Registers [ParticleSystem2D] in scene systems DSL.
 */
fun SystemsBuilder.particles2d(
    system: ParticleSystem2D,
    id: String = "particles-2d",
    phase: SystemPhase = SystemPhase.RenderPrep,
    order: Int = 0,
    enabled: Boolean = true,
) {
    add(
        id = id,
        phase = phase,
        order = order,
        enabled = enabled,
        system = system,
    )
}

/**
 * Creates a single render entity for a [ParticleSystem2D].
 *
 * Rendering particles via one entity avoids per-particle scene overhead.
 */
fun particleRendererEntity(
    id: String,
    system: ParticleSystem2D,
    zIndex: Int = 0,
    style: RenderStyle = RenderStyle(),
): Entity {
    return Entity(id).apply {
        addComponent(
            RenderComponent(
                zIndex = zIndex,
                renderer = {
                    system.render(this, style)
                },
            ),
        )
        alwaysVisible = true
    }
}
