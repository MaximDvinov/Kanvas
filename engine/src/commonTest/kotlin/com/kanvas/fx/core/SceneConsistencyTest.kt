package com.kanvas.fx.core

import com.kanvas.fx.render.Renderer2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneConsistencyTest {
    @Test
    fun removeApiByEntityAndBatchVariantWorkWithPendingAndExisting() {
        val scene = Scene("api")
        val engine = Engine()
        val first = Entity("first")
        val second = Entity("second")
        scene.spawn(first)
        scene.spawn(second)

        assertTrue(scene.removeEntity(first))
        assertEquals(1, scene.removeManyIfExists(listOf("second", "missing")))

        scene.start(engine)
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )

        assertNull(scene.entity("first"))
        assertNull(scene.entity("second"))
    }

    @Test
    fun indexesStayConsistentWhenComponentsMutateAfterSpawn() {
        val scene = Scene("test")
        val engine = Engine()
        val entity = Entity("player")
            .addComponent(RenderComponent(zIndex = 1))
            .addComponent(TagsComponent(linkedSetOf("hero")))

        scene.spawn(entity)
        scene.start(engine)

        assertEquals(entity, scene.entity("player"))
        assertEquals(1, scene.entitiesTagged("hero").size)
        assertEquals(1, scene.entitiesWith(RenderComponent::class).size)

        entity.removeComponent(TagsComponent::class)
        assertTrue(scene.entitiesTagged("hero").isEmpty())

        entity.addComponent(TagsComponent(linkedSetOf("npc")))
        assertEquals(1, scene.entitiesTagged("npc").size)

        entity.removeComponent(RenderComponent::class)
        assertTrue(scene.entitiesWith(RenderComponent::class).isEmpty())

        scene.removeEntity("player")
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.016f,
                elapsedSeconds = 0.016f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )
        assertNull(scene.entity("player"))
    }

    @Test
    fun visibilityRulesAreDeterministicOnBordersAndFlags() {
        val scene = Scene("culling")
        val entity = Entity("e")
            .addComponent(BoundsComponent(left = 0f, top = 0f, right = 10f, bottom = 10f))
        val viewport = Renderer2D.WorldViewport(left = 10f, top = 10f, right = 20f, bottom = 20f)

        assertTrue(scene.isVisibleInViewport(entity, viewport))

        entity.removeComponent(BoundsComponent::class)
        assertTrue(scene.isVisibleInViewport(entity, viewport))

        entity.addComponent(BoundsComponent(left = -50f, top = -50f, right = -40f, bottom = -40f))
        assertFalse(scene.isVisibleInViewport(entity, viewport))

        entity.alwaysVisible = true
        assertTrue(scene.isVisibleInViewport(entity, viewport))

        entity.alwaysVisible = false
        scene.visibility.enabled = false
        assertTrue(scene.isVisibleInViewport(entity, viewport))
    }
}
