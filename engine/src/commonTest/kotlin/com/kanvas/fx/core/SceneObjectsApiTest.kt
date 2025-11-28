package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneObjectsApiTest {
    @Test
    fun handleCanControlObjectExternally() {
        val scene = Scene("objects")
        val engine = Engine()
        val handle = scene.objects.create("player")
        scene.start(engine)

        handle.enabled = false
        handle.position = Offset(10f, 20f)
        handle.addTag("hero")
        handle.setRenderOrder(7)

        val entity = handle.requireEntity()
        assertFalse(entity.enabled)
        assertEquals(Offset(10f, 20f), entity.requireComponent<TransformComponent>().position)
        assertTrue(entity.hasTag("hero"))
        assertEquals(7, entity.requireComponent<RenderComponent>().zIndex)
    }

    @Test
    fun removeAndTagQueriesWorkFromHighLevelApi() {
        val scene = Scene("objects")
        val engine = Engine()
        val first = scene.objects.create("first")
        val second = scene.objects.create("second")
        scene.start(engine)

        first.addTag("enemy")
        second.addTag("enemy")
        assertEquals(2, scene.objects.withTag("enemy").size)

        assertTrue(scene.objects.remove(first))
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )
        assertNotNull(scene.objects.get("second"))
        assertEquals(null, scene.objects.get("first"))
    }
}
