package com.kanvas.fx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineRuntimeTest {
    @Test
    fun stopAndResumeControlSceneLifecycle() {
        val engine = Engine()
        val scene = Scene("main")
        var starts = 0
        var stops = 0
        scene.onStart = { starts++ }
        scene.onStop = { stops++ }
        engine.addScene(scene, setCurrent = true)

        engine.start()
        engine.stop()
        engine.resume()

        assertEquals(2, starts)
        assertEquals(1, stops)
    }

    @Test
    fun pausePreventsSimulationAdvance() {
        val engine = Engine(
            config = EngineConfig(clock = EngineClock(fixedTimeStepSeconds = 0.1f, maxSubstepsPerFrame = 8)),
        )
        val scene = Scene("main")
        var updates = 0
        scene.onUpdate = { updates++ }
        engine.addScene(scene, setCurrent = true)

        engine.isPaused = true
        engine.tick(0.35f)

        assertEquals(0, updates)
        assertEquals(0L, engine.frameIndex)
        assertEquals(0f, engine.elapsedSeconds)
    }

    @Test
    fun timeScaleAffectsElapsedAndFixedStepSubsteps() {
        val engine = Engine(
            config = EngineConfig(clock = EngineClock(fixedTimeStepSeconds = 0.1f, maxSubstepsPerFrame = 8)),
        )
        val scene = Scene("main")
        var updates = 0
        scene.onUpdate = { updates++ }
        scene.timeScale = 0.5f
        engine.timeScale = 2f
        engine.addScene(scene, setCurrent = true)

        engine.tick(0.25f)

        // effectiveDelta = 0.25 * 2.0 * 0.5 = 0.25 => two fixed steps and 0.05 remainder
        assertEquals(2, updates)
        assertEquals(2L, engine.frameIndex)
        assertTrue(engine.elapsedSeconds > 0.24f && engine.elapsedSeconds < 0.26f)
    }

    @Test
    fun maxSubstepsClampsCatchUpWorkPerFrame() {
        val engine = Engine(
            config = EngineConfig(clock = EngineClock(fixedTimeStepSeconds = 0.1f, maxSubstepsPerFrame = 2)),
        )
        val scene = Scene("main")
        var updates = 0
        scene.onUpdate = { updates++ }
        engine.addScene(scene, setCurrent = true)

        engine.tick(1f)

        assertEquals(2, updates)
        assertEquals(2L, engine.frameIndex)
        assertEquals(1f, engine.elapsedSeconds)
    }
}
