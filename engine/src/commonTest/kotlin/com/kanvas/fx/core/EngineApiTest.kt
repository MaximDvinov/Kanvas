package com.kanvas.fx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EngineApiTest {
    @Test
    fun switchSceneOrNullIsSafeForMissingNames() {
        val engine = Engine()
        val first = Scene("first")
        val second = Scene("second")
        engine.addScene(first, setCurrent = true)
        engine.addScene(second, setCurrent = false)

        assertFalse(engine.switchSceneOrNull("missing"))
        assertSame(first, engine.currentScene)

        assertTrue(engine.switchSceneOrNull("second"))
        assertSame(second, engine.currentScene)
    }

    @Test
    fun sceneLookupApisReturnExpectedScenes() {
        val engine = Engine()
        val scene = Scene("main")
        engine.addScene(scene)

        assertSame(scene, engine.sceneOrNull("main"))
        assertSame(scene, engine.requireScene("main"))
        assertEquals(null, engine.sceneOrNull("unknown"))
    }

    @Test
    fun canAddObjectsExternallyThroughEngineApi() {
        val engine = Engine()
        val scene = Scene("main")
        engine.addScene(scene, setCurrent = true)
        scene.start(engine)

        val currentHandle = engine.addObjectToCurrentScene("player") {
            addComponent(TransformComponent())
        }
        val namedHandle = engine.addObjectToScene("main", "enemy")
        val missing = engine.addObjectToSceneOrNull("missing", "ghost")

        assertNotNull(currentHandle)
        assertNotNull(namedHandle)
        assertNull(missing)

        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )
        assertNotNull(scene.entity("player"))
        assertNotNull(scene.entity("enemy"))
    }

    @Test
    fun canRemoveObjectsExternallyThroughEngineApi() {
        val engine = Engine()
        val scene = Scene("main")
        engine.addScene(scene, setCurrent = true)
        scene.start(engine)
        scene.objects.create("a")
        scene.objects.create("b")
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )

        assertTrue(engine.removeObjectFromCurrentScene("a"))
        assertTrue(engine.removeObjectFromScene("main", "b"))
        assertFalse(engine.removeObjectFromSceneOrNull("missing", "x"))
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.032f,
                frameIndex = 2L,
                engine = engine,
                scene = scene,
            ),
        )

        assertNull(scene.entity("a"))
        assertNull(scene.entity("b"))
    }
}
