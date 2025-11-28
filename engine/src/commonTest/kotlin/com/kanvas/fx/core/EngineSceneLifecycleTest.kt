package com.kanvas.fx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EngineSceneLifecycleTest {
    @Test
    fun switchSceneInvokesStopAndStartInOrder() {
        val events = mutableListOf<String>()
        val engine = Engine()
        val first = Scene("first").apply {
            onStart = { events += "first-start" }
            onStop = { events += "first-stop" }
        }
        val second = Scene("second").apply {
            onStart = { events += "second-start" }
            onStop = { events += "second-stop" }
        }

        engine.addScene(first, setCurrent = true)
        engine.addScene(second, setCurrent = false)
        engine.start()
        engine.switchScene("second")

        assertEquals(listOf("first-start", "first-stop", "second-start"), events)
    }

    @Test
    fun pendingSpawnAndRemoveAreAppliedAfterSceneSwitch() {
        val engine = Engine()
        val first = Scene("first")
        val second = Scene("second")
        engine.addScene(first, setCurrent = true)
        engine.addScene(second, setCurrent = false)
        engine.start()

        second.spawn(Entity("queued"))
        engine.switchScene("second")
        assertNotNull(second.entity("queued"))

        second.removeEntity("queued")
        second.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = second,
            ),
        )
        assertNull(second.entity("queued"))
    }
}
