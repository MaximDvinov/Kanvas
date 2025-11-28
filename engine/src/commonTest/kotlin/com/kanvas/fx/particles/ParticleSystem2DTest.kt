package com.kanvas.fx.particles

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.Scene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticleSystem2DTest {
    @Test
    fun particleCountDoesNotExceedCapacity() {
        val engine = Engine()
        val scene = Scene("particles")
        val system = ParticleSystem2D(capacity = 4, maxDeltaTimeSeconds = 1f)
        system.registerEmitter(
            id = "main",
            emitter = ParticleEmitter2D(
                particlesPerSecond = 500f,
                minLifetimeSeconds = 5f,
                maxLifetimeSeconds = 5f,
                origin = { Offset.Zero },
            ),
        )

        system.update(frame(engine, scene, dt = 0.2f, elapsed = 0.2f, frame = 1))

        assertEquals(4, system.activeParticles)
        assertEquals(4, system.capacityLimit)
    }

    @Test
    fun particlesExpireByLifetime() {
        val engine = Engine()
        val scene = Scene("particles")
        val system = ParticleSystem2D(capacity = 16, maxDeltaTimeSeconds = 1f)
        system.registerEmitter(
            id = "main",
            emitter = ParticleEmitter2D(
                particlesPerSecond = 10f,
                minLifetimeSeconds = 0.1f,
                maxLifetimeSeconds = 0.1f,
                origin = { Offset.Zero },
            ),
        )

        system.update(frame(engine, scene, dt = 0.2f, elapsed = 0.2f, frame = 1))
        assertTrue(system.activeParticles > 0)

        system.clearEmitters()
        system.update(frame(engine, scene, dt = 0.2f, elapsed = 0.4f, frame = 2))

        assertEquals(0, system.activeParticles)
    }

    private fun frame(
        engine: Engine,
        scene: Scene,
        dt: Float,
        elapsed: Float,
        frame: Long,
    ): FrameContext {
        return FrameContext(
            deltaTimeSeconds = dt,
            elapsedSeconds = elapsed,
            frameIndex = frame,
            engine = engine,
            scene = scene,
        )
    }
}
