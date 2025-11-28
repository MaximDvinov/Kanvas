package com.kanvas.fx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Animation2DTest {
    @Test
    fun loopingClipAdvancesFramesDeterministically() {
        val scene = Scene("anim")
        val engine = Engine()
        val entity = Entity("player")
            .setAnimationClips(
                listOf(
                    AnimationClip(
                        id = "run",
                        fps = 10f,
                        loop = true,
                        frames = listOf(
                            SpriteFrame("atlas", 0, 0, 16, 16),
                            SpriteFrame("atlas", 16, 0, 16, 16),
                            SpriteFrame("atlas", 32, 0, 16, 16),
                        ),
                    ),
                ),
            )
            .playAnimation("run")
        scene.spawn(entity)
        scene.addSystem(SceneSystemSpec(id = "anim", system = SpriteAnimationSystem()))
        scene.start(engine)

        // 0.25 sec on 10 fps => 2 frames advanced (frame duration 0.1)
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.25f,
                elapsedSeconds = 0.25f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )

        val player = entity.requireComponent<AnimationPlayerComponent>()
        assertEquals(2, player.frameIndex)
        assertFalse(player.paused)
    }

    @Test
    fun nonLoopClipCanQueueNextClip() {
        val scene = Scene("anim")
        val engine = Engine()
        val entity = Entity("enemy")
            .setAnimationClips(
                listOf(
                    AnimationClip(
                        id = "attack",
                        fps = 8f,
                        loop = false,
                        frames = listOf(
                            SpriteFrame("atlas", 0, 0, 16, 16),
                            SpriteFrame("atlas", 16, 0, 16, 16),
                        ),
                    ),
                    AnimationClip(
                        id = "idle",
                        fps = 8f,
                        loop = true,
                        frames = listOf(
                            SpriteFrame("atlas", 32, 0, 16, 16),
                        ),
                    ),
                ),
            )
            .playAnimation("attack")
            .queueAnimation("idle")
        scene.spawn(entity)
        scene.addSystem(SceneSystemSpec(id = "anim", system = SpriteAnimationSystem()))
        scene.start(engine)

        // Enough time to finish attack and switch to idle.
        scene.update(
            FrameContext(
                deltaTimeSeconds = 0.30f,
                elapsedSeconds = 0.30f,
                frameIndex = 1L,
                engine = engine,
                scene = scene,
            ),
        )

        val player = entity.requireComponent<AnimationPlayerComponent>()
        assertEquals("idle", player.currentClipId)
        assertEquals(0, player.frameIndex)
        assertNotNull(entity.currentAnimationFrameOrNull())
    }

    @Test
    fun playAnimationApiControlsQueueAndRestart() {
        val entity = Entity("hero")
            .setAnimationClips(
                listOf(
                    AnimationClip(
                        id = "idle",
                        fps = 5f,
                        loop = true,
                        frames = listOf(SpriteFrame("atlas")),
                    ),
                    AnimationClip(
                        id = "run",
                        fps = 5f,
                        loop = true,
                        frames = listOf(SpriteFrame("atlas")),
                    ),
                ),
            )
            .playAnimation("idle")

        entity.queueAnimation("run")
        var player = entity.requireComponent<AnimationPlayerComponent>()
        assertEquals(1, player.queuedClipIds.size)

        entity.playAnimation("idle", restartIfAlreadyPlaying = false, clearQueue = true)
        player = entity.requireComponent<AnimationPlayerComponent>()
        assertTrue(player.queuedClipIds.isEmpty())
        assertEquals("idle", player.currentClipId)
    }
}
