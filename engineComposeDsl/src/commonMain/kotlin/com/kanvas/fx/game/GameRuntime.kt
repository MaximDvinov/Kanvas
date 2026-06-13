package com.kanvas.fx.game

import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.EntityComponent
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneObjects

class GameRuntimeContext internal constructor(
    val engine: Engine,
) {
    val currentScene: Scene?
        get() = engine.currentScene

    fun sceneOrNull(name: String): Scene? = engine.sceneOrNull(name)

    fun queryByTag(
        tag: String,
        sceneName: String? = null,
    ): List<Entity> {
        val scene = when {
            sceneName != null -> engine.sceneOrNull(sceneName)
            else -> engine.currentScene
        } ?: return emptyList()
        return scene.entitiesTagged(tag)
    }

    inline fun <reified T : EntityComponent> queryComponents(
        sceneName: String? = null,
    ): List<Entity> {
        val scene = when {
            sceneName != null -> engine.sceneOrNull(sceneName)
            else -> engine.currentScene
        } ?: return emptyList()
        return scene.entitiesWith(T::class)
    }

    fun commands(sceneName: String? = null): GameCommands {
        val scene = when {
            sceneName != null -> engine.sceneOrNull(sceneName)
            else -> engine.currentScene
        }
        return GameCommands(engine, scene)
    }
}

class GameCommands internal constructor(
    private val engine: Engine,
    private val scene: Scene?,
) {
    fun spawn(id: String, configure: (Entity.() -> Unit)? = null): Boolean {
        val target = scene ?: return false
        target.objects.create(id, configure)
        return true
    }

    fun remove(id: String): Boolean {
        val target = scene ?: return false
        return target.removeEntityIfExists(id)
    }

    fun find(id: String): Entity? = scene?.entity(id)

    fun switchScene(name: String): Boolean = engine.switchSceneOrNull(name)

    fun objects(): SceneObjects? = scene?.objects
}

data class GameFrameContext(
    val runtime: GameRuntimeContext,
    val frame: FrameContext,
)

object legacy {
    fun runtime(engine: Engine): GameRuntimeContext = GameRuntimeContext(engine)
}

object advanced {
    fun runtime(engine: Engine): GameRuntimeContext = GameRuntimeContext(engine)
}
