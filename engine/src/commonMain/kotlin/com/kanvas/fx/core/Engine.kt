package com.kanvas.fx.core

import com.kanvas.fx.render.Renderer2D

/**
 * Main simulation runtime that manages scene lifecycle and simulation clock.
 *
 * @property config engine runtime configuration.
 */
class Engine(
    /** Engine runtime configuration. */
    val config: EngineConfig = EngineConfig(),
) {
    private val stateLock = PlatformLock()
    private val scenes = linkedMapOf<String, Scene>()
    private var started = false
    private var accumulator = 0f

    /** Currently active scene. */
    var currentScene: Scene? = null
        private set

    /** Monotonic simulation frame counter. */
    var frameIndex: Long = 0
        private set

    /** Total simulated seconds advanced by [tick] or [stepOnce]. */
    var elapsedSeconds: Float = 0f
        private set

    /** Global pause switch. When true, [tick] does not advance simulation. */
    var isPaused: Boolean = false
    /** Global simulation speed multiplier. */
    var timeScale: Float = 1f

    /** Hook executed before scene update each frame. */
    var onBeforeUpdate: (FrameContext) -> Unit = {}
    /** Hook executed after scene update each frame. */
    var onAfterUpdate: (FrameContext) -> Unit = {}
    /** Hook called after current scene changes. */
    var onSceneChanged: (Scene) -> Unit = {}

    /**
     * Registers a scene in this engine.
     */
    fun addScene(scene: Scene, setCurrent: Boolean = currentScene == null) {
        stateLock.withLock {
            scenes[scene.name] = scene
            if (setCurrent) currentScene = scene
        }
    }

    /**
     * Switches active scene by name.
     */
    fun switchScene(name: String) {
        stateLock.withLock {
            val next = scenes[name] ?: error("Scene '$name' is not registered.")
            switchSceneLocked(next)
        }
    }

    /**
     * Returns registered scene by [name], or null if it does not exist.
     */
    fun sceneOrNull(name: String): Scene? = stateLock.withLock { scenes[name] }

    /**
     * Returns registered scene by [name] or throws with a stable message.
     */
    fun requireScene(name: String): Scene =
        sceneOrNull(name) ?: error("Scene '$name' is not registered.")

    /**
     * Switches active scene by [name].
     *
     * @return true when scene exists and switch was applied.
     */
    fun switchSceneOrNull(name: String): Boolean {
        var switched = false
        stateLock.withLock {
            val next = scenes[name]
            if (next != null) {
                switchSceneLocked(next)
                switched = true
            }
        }
        return switched
    }

    /**
     * Switches active scene instance.
     */
    fun switchScene(scene: Scene) {
        stateLock.withLock {
            switchSceneLocked(scene)
        }
    }

    /**
     * Starts engine and current scene lifecycle.
     */
    fun start() {
        stateLock.withLock {
            if (!started) {
                started = true
                currentScene?.start(this)
            }
        }
    }

    /**
     * Dispatches input event to current scene.
     */
    fun dispatchInput(event: EngineInputEvent) {
        stateLock.withLock {
            currentScene?.handleInput(event)
        }
    }

    /**
     * Creates object in current scene.
     *
     * @return handle to created object, or null when there is no current scene.
     */
    fun addObjectToCurrentScene(
        id: String,
        configure: (Entity.() -> Unit)? = null,
    ): ObjectHandle? {
        var created: ObjectHandle? = null
        stateLock.withLock {
            val scene = currentScene
            if (scene != null) {
                created = scene.objects.create(id, configure)
            }
        }
        return created
    }

    /**
     * Creates object in target scene by [sceneName] or throws when scene is missing.
     */
    fun addObjectToScene(
        sceneName: String,
        id: String,
        configure: (Entity.() -> Unit)? = null,
    ): ObjectHandle {
        return addObjectToSceneOrNull(sceneName, id, configure)
            ?: error("Scene '$sceneName' is not registered.")
    }

    /**
     * Creates object in target scene by [sceneName].
     *
     * @return created handle, or null when scene does not exist.
     */
    fun addObjectToSceneOrNull(
        sceneName: String,
        id: String,
        configure: (Entity.() -> Unit)? = null,
    ): ObjectHandle? {
        var created: ObjectHandle? = null
        stateLock.withLock {
            val scene = scenes[sceneName]
            if (scene != null) {
                created = scene.objects.create(id, configure)
            }
        }
        return created
    }

    /**
     * Removes object by [id] from current scene.
     *
     * @return true when object existed and removal was enqueued/applied.
     */
    fun removeObjectFromCurrentScene(id: String): Boolean {
        var removed = false
        stateLock.withLock {
            removed = currentScene?.removeEntityIfExists(id) == true
        }
        return removed
    }

    /**
     * Removes object by [id] from named scene.
     *
     * @return true when scene exists and object removal was enqueued/applied.
     */
    fun removeObjectFromSceneOrNull(
        sceneName: String,
        id: String,
    ): Boolean {
        var removed = false
        stateLock.withLock {
            val scene = scenes[sceneName]
            if (scene != null) {
                removed = scene.removeEntityIfExists(id)
            }
        }
        return removed
    }

    /**
     * Removes object by [id] from named scene or throws when scene is missing.
     */
    fun removeObjectFromScene(
        sceneName: String,
        id: String,
    ): Boolean {
        var removed = false
        stateLock.withLock {
            val scene = scenes[sceneName] ?: error("Scene '$sceneName' is not registered.")
            removed = scene.removeEntityIfExists(id)
        }
        return removed
    }

    /**
     * Renders current scene into [renderer].
     */
    fun renderCurrentScene(renderer: Renderer2D) {
        stateLock.withLock {
            renderer.beginFrame(
                frameIndex = frameIndex,
                elapsedSeconds = elapsedSeconds,
            )
            currentScene?.render(renderer)
        }
    }

    /**
     * Advances engine time by real frame delta.
     *
     * Applies global and scene time scales and respects fixed-step scheduler
     * from [EngineConfig.clock].
     */
    fun tick(realDeltaTimeSeconds: Float) {
        stateLock.withLock {
            val scene = currentScene
            if (scene == null) return@withLock
            if (!started) start()

            val effectiveDelta = realDeltaTimeSeconds * timeScale.coerceAtLeast(0f) * scene.timeScale.coerceAtLeast(0f)
            if (!isPaused) elapsedSeconds += effectiveDelta

            val fixedStep = config.clock.fixedTimeStepSeconds
            if (fixedStep == null) {
                if (!isPaused) updateScene(scene, effectiveDelta)
                return@withLock
            }

            if (isPaused) return@withLock

            accumulator += effectiveDelta
            var substeps = 0
            while (accumulator >= fixedStep && substeps < config.clock.maxSubstepsPerFrame) {
                updateScene(scene, fixedStep)
                accumulator -= fixedStep
                substeps++
            }

            if (substeps == config.clock.maxSubstepsPerFrame && accumulator >= fixedStep) {
                accumulator = fixedStep * 0.5f
            }
        }
    }

    /**
     * Advances exactly one simulation step even when paused.
     */
    fun stepOnce() {
        stateLock.withLock {
            val scene = currentScene
            if (scene == null) return@withLock
            if (!started) start()
            val fixed = config.clock.fixedTimeStepSeconds ?: (1f / 120f)
            elapsedSeconds += fixed
            updateScene(scene, fixed)
        }
    }

    /**
     * Stops current scene lifecycle and pauses simulation updates.
     *
     * Engine keeps registered scenes and can be resumed later.
     */
    fun stop() {
        stateLock.withLock {
            if (!started) return@withLock
            currentScene?.stop(this)
            started = false
        }
    }

    /**
     * Resumes engine lifecycle if it was previously stopped.
     */
    fun resume() {
        start()
    }

    private fun switchSceneLocked(scene: Scene) {
        if (currentScene === scene) return
        currentScene?.stop(this)
        currentScene = scene
        if (started) scene.start(this)
        onSceneChanged(scene)
    }

    private fun updateScene(scene: Scene, deltaTimeSeconds: Float) {
        val frame = FrameContext(
            deltaTimeSeconds = deltaTimeSeconds,
            elapsedSeconds = elapsedSeconds,
            frameIndex = frameIndex,
            engine = this,
            scene = scene,
        )
        onBeforeUpdate(frame)
        scene.update(frame)
        onAfterUpdate(frame)
        frameIndex++
    }
}

/**
 * Fixed-step simulation clock settings.
 *
 * @property fixedTimeStepSeconds fixed update step, or null for variable-step mode.
 * @property maxSubstepsPerFrame maximum number of catch-up substeps per frame.
 */
data class EngineClock(
    /** Fixed update step, or null for variable-step update. */
    val fixedTimeStepSeconds: Float? = 1f / 120f,
    /** Maximum number of substeps processed per frame to avoid spiral-of-death. */
    val maxSubstepsPerFrame: Int = 5,
)

/**
 * Engine runtime configuration container.
 *
 * @property clock fixed-step clock configuration.
 */
data class EngineConfig(
    val clock: EngineClock = EngineClock(),
)

/**
 * Frame context passed to systems and entity behaviors.
 *
 * @property deltaTimeSeconds delta time for current update step in simulation seconds.
 * @property elapsedSeconds total elapsed simulation time.
 * @property frameIndex sequential frame index.
 * @property engine engine that owns this frame.
 * @property scene scene being updated.
 */
data class FrameContext(
    /** Delta time for current update step in simulation seconds. */
    val deltaTimeSeconds: Float,
    /** Total elapsed simulation time. */
    val elapsedSeconds: Float,
    /** Sequential frame index. */
    val frameIndex: Long,
    /** Engine that owns the update. */
    val engine: Engine,
    /** Scene being updated. */
    val scene: Scene,
)
