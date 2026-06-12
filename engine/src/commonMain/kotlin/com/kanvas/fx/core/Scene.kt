package com.kanvas.fx.core

import com.kanvas.fx.render.Renderer2D
import kotlin.reflect.KClass
import kotlin.time.TimeSource

/**
 * Scene-level visibility configuration used by the renderer.
 *
 * Set [debugOverlay] to true when profiling culling decisions or validating object bounds.
 */
data class VisibilityConfig(
    var enabled: Boolean = true,
    var debugOverlay: Boolean = false,
)

/**
 * Per-scene render quality switches.
 *
 * The renderer may clamp these values for very large scenes to keep frame time stable.
 */
data class RenderQualityConfig(
    var effectsEnabled: Boolean = true,
    var maxLightsPerObject: Int = 4,
    var glowQuality: Float = 1f,
)

/**
 * Mutable render counters captured during the most recent scene render.
 */
data class ScenePerfCounters(
    val renderedCount: Int = 0,
    val culledCount: Int = 0,
    val cullingTimeMicros: Long = 0L,
    val drawCalls: Int = 0,
    val materialPasses: Int = 0,
)

/**
 * Immutable frame-stat snapshot suitable for UI panels, diagnostics, and tests.
 */
data class SceneFrameStats(
    val renderedCount: Int = 0,
    val culledCount: Int = 0,
    val cullingTimeMicros: Long = 0L,
    val drawCalls: Int = 0,
    val materialPasses: Int = 0,
)

/**
 * Runtime scene container with camera, entities, camera controls, and ordered systems.
 *
 * Scene update pipeline:
 * 1. flush pending spawn/remove queues;
 * 2. camera controls [CameraControl.onFrame];
 * 3. scene callback [onUpdate];
 * 4. enabled systems sorted by phase/order/id;
 * 5. enabled entities update behaviors;
 * 6. flush queues again.
 *
 * Use one [Scene] for one gameplay state such as a menu, level, world shard, or editor mode.
 *
 * ```kotlin
 * val scene = Scene("level")
 * scene.onUpdate = { frame ->
 *     if (frame.frameIndex % 60L == 0L) println(scene.frameStatsSnapshot())
 * }
 * scene.spawn(Entity("player"))
 * ```
 */
class Scene(
    /** Unique scene name inside an [Engine]. */
    val name: String,
) {
    /** High-level object access API for external control by id. */
    val objects: SceneObjects = SceneObjects(this)

    /** Scene camera used by renderer and input coordinate conversion. */
    val camera: Camera2D = Camera2D()
    /** Additional scene-local time multiplier applied after [Engine.timeScale]. */
    var timeScale: Float = 1f
    /** Visibility and debug overlay settings for this scene. */
    val visibility: VisibilityConfig = VisibilityConfig()
    /** Quality knobs used by render materials and scene-level fallbacks. */
    val renderQuality: RenderQualityConfig = RenderQualityConfig()
    /** Mutable counters from the latest render pass. */
    var perfCounters: ScenePerfCounters = ScenePerfCounters()
        private set
    /** Immutable frame stats from the latest render pass. */
    var lastFrameStats: SceneFrameStats = SceneFrameStats()
        private set

    /**
     * Returns the latest render-stat snapshot.
     *
     * Prefer this method from UI code to avoid depending on mutable internal counters.
     */
    fun frameStatsSnapshot(): SceneFrameStats = lastFrameStats

    private val entities = mutableListOf<Entity>()
    private val pendingAdd = mutableListOf<Entity>()
    private val pendingRemove = mutableListOf<String>()
    private val systems = mutableListOf<SceneSystemSpec>()
    private val cameraControls = mutableListOf<CameraControl>()
    private val entityById = linkedMapOf<String, Entity>()
    private val entitiesByTag = linkedMapOf<String, LinkedHashSet<Entity>>()
    private val entitiesByComponentType = linkedMapOf<KClass<out EntityComponent>, LinkedHashSet<Entity>>()
    private var systemsCacheDirty = true
    private var renderCacheDirty = true
    private var systemsSortedCache: List<SceneSystemSpec> = emptyList()
    private var renderSortedCache: List<Entity> = emptyList()

    /** Called when the scene starts in an engine. */
    var onStart: (Engine) -> Unit = {}
    /** Called when the scene stops or is replaced. */
    var onStop: (Engine) -> Unit = {}
    /** Scene-level update callback before systems/entities update. */
    var onUpdate: (FrameContext) -> Unit = {}
    /** Scene-level input callback before entity input dispatch. */
    var onInput: (EngineInputEvent) -> Unit = {}

    /**
     * Enqueues entity addition.
     *
     * The entity becomes active on queue flush, not immediately in-place. This keeps entity
     * lists stable while systems and behaviors are iterating.
     *
     * ```kotlin
     * scene.spawn(Entity("enemy-1").addComponent(TransformComponent()))
     * ```
     */
    fun spawn(entity: Entity) {
        pendingAdd += entity
    }

    /**
     * Enqueues multiple entities for next flush cycle.
     */
    fun spawnMany(items: Iterable<Entity>) {
        pendingAdd += items
    }

    /**
     * Enqueues removal for entity id.
     */
    fun removeEntity(id: String) {
        pendingRemove += id
    }

    /**
     * Removes [entity] by id.
     *
     * @return true when the entity is currently known by this scene.
     */
    fun removeEntity(entity: Entity): Boolean = removeEntityIfExists(entity.id)

    /**
     * Removes entity when it exists either in active list or pending additions.
     *
     * @return true when removal was enqueued.
     */
    fun removeEntityIfExists(id: String): Boolean {
        val removedFromPending = pendingAdd.removeAll { it.id == id }
        val existsInScene = entityById.containsKey(id)
        if (!removedFromPending && !existsInScene) return false
        if (existsInScene) pendingRemove += id
        return true
    }

    /**
     * Enqueues removal for every id in [ids].
     *
     * Missing ids are ignored during the later queue flush.
     */
    fun removeMany(ids: Iterable<String>) {
        pendingRemove += ids
    }

    /**
     * Removes only existing ids and returns how many removals were enqueued.
     */
    fun removeManyIfExists(ids: Iterable<String>): Int {
        var removed = 0
        for (id in ids) {
            if (removeEntityIfExists(id)) removed++
        }
        return removed
    }

    /**
     * Adds an ordered scene system.
     *
     * Systems are sorted by phase, order, and id during update. Use [upsertSystem] when a
     * stable id should be replaced instead of duplicated.
     */
    fun addSystem(spec: SceneSystemSpec) {
        systems += spec
        systemsCacheDirty = true
    }

    /**
     * Adds or replaces a system by stable [SceneSystemSpec.id].
     */
    fun upsertSystem(spec: SceneSystemSpec) {
        val index = systems.indexOfFirst { it.id == spec.id }
        if (index >= 0) {
            systems[index] = spec
        } else {
            systems += spec
        }
        systemsCacheDirty = true
    }

    /**
     * Removes a system by [id].
     *
     * @return true when the system existed.
     */
    fun removeSystem(id: String): Boolean {
        val removed = systems.removeAll { it.id == id }
        if (removed) systemsCacheDirty = true
        return removed
    }

    /**
     * Adds a camera control that can react to input and frame updates before scene systems.
     */
    fun addCameraControl(control: CameraControl) {
        cameraControls += control
    }

    /**
     * Enables or disables a registered system.
     *
     * @return true when a system with [id] exists.
     */
    fun setSystemEnabled(id: String, enabled: Boolean): Boolean {
        val spec = systems.firstOrNull { it.id == id } ?: return false
        spec.enabled = enabled
        systemsCacheDirty = true
        return true
    }

    /**
     * Returns registered systems in insertion order.
     */
    fun systemsSnapshot(): List<SceneSystemSpec> = systems.toList()

    /**
     * Returns active entities that currently own a component of [type].
     */
    fun <T : EntityComponent> entitiesWith(type: KClass<T>): List<Entity> =
        entitiesByComponentType[type]?.toList() ?: emptyList()

    /**
     * Returns active entities containing [tag] in their [TagsComponent].
     */
    fun entitiesTagged(tag: String): List<Entity> = entitiesByTag[tag]?.toList() ?: emptyList()

    /**
     * Returns an active entity by id, or null when it is not attached to the scene.
     */
    fun entity(id: String): Entity? = entityById[id]

    internal fun start(engine: Engine) {
        flushQueues(engine)
        onStart(engine)
    }

    internal fun stop(engine: Engine) {
        onStop(engine)
        entities.forEach { it.onDetach(engine, this) }
    }

    internal fun handleInput(event: EngineInputEvent) {
        cameraControls.forEach { it.onInput(this, event) }
        onInput(event)
        entities.forEach { it.onInput(event) }
    }

    internal fun update(frame: FrameContext) {
        flushQueues(frame.engine)
        cameraControls.forEach { it.onFrame(this, frame) }
        onUpdate(frame)
        for (spec in sortedSystems()) spec.system.update(frame)
        entities.forEach { it.onUpdate(frame) }
        flushQueues(frame.engine)
    }

    internal fun render(renderer: Renderer2D) {
        val ordered = sortedRenderableEntities()
        val heavyScene = ordered.size > 3_000
        val effectiveLights = if (heavyScene) renderQuality.maxLightsPerObject.coerceAtMost(1) else renderQuality.maxLightsPerObject
        val effectiveGlow = if (heavyScene) renderQuality.glowQuality.coerceAtMost(0.45f) else renderQuality.glowQuality
        renderer.configureQuality(renderQuality.effectsEnabled, effectiveLights, effectiveGlow)
        val viewport = renderer.worldViewport()
        val mark = TimeSource.Monotonic.markNow()
        var rendered = 0
        var culled = 0
        for (entity in ordered) {
            if (!entity.enabled) continue
            if (shouldCull(entity, viewport)) {
                culled++
                continue
            }
            rendered++
            entity.onRender(renderer)
        }
        if (visibility.debugOverlay) {
            renderer.drawDebugOverlay(
                viewport = viewport,
                renderedCount = rendered,
                culledCount = culled,
            )
        }
        val micros = mark.elapsedNow().inWholeMicroseconds
        perfCounters = ScenePerfCounters(
            renderedCount = rendered,
            culledCount = culled,
            cullingTimeMicros = micros,
            drawCalls = renderer.frameStats.drawCalls,
            materialPasses = renderer.frameStats.materialPasses,
        )
        renderer.setExternallyCulledCount(culled)
        val syncedRendererStats = renderer.frameStats
        perfCounters = perfCounters.copy(culledCount = syncedRendererStats.culledCount)
        lastFrameStats = SceneFrameStats(
            renderedCount = perfCounters.renderedCount,
            culledCount = perfCounters.culledCount,
            cullingTimeMicros = perfCounters.cullingTimeMicros,
            drawCalls = syncedRendererStats.drawCalls,
            materialPasses = syncedRendererStats.materialPasses,
        )
    }

    fun snapshotEntities(): List<Entity> = entities.toList()

    private fun sortedSystems(): List<SceneSystemSpec> {
        if (!systemsCacheDirty) return systemsSortedCache
        systemsSortedCache = systems
            .asSequence()
            .filter { it.enabled }
            .sortedWith(compareBy<SceneSystemSpec>({ it.phase.ordinal }, { it.order }, { it.id }))
            .toList()
        systemsCacheDirty = false
        return systemsSortedCache
    }

    private fun sortedRenderableEntities(): List<Entity> {
        if (!renderCacheDirty) return renderSortedCache
        renderSortedCache = entities
            .asSequence()
            .filter { it.componentOrNull<RenderComponent>() != null }
            .sortedBy { it.renderOrder() }
            .toList()
        renderCacheDirty = false
        return renderSortedCache
    }

    private fun shouldCull(entity: Entity, viewport: Renderer2D.WorldViewport): Boolean {
        if (!visibility.enabled) return false
        if (entity.alwaysVisible) return false
        if (entity.componentOrNull<VisibilityComponent>()?.alwaysVisible == true) return false
        val bounds = entity.boundsOrNull() ?: return false
        if (bounds.right < viewport.left) return true
        if (bounds.left > viewport.right) return true
        if (bounds.bottom < viewport.top) return true
        if (bounds.top > viewport.bottom) return true
        return false
    }

    internal fun onEntityComponentAdded(
        entity: Entity,
        type: KClass<out EntityComponent>,
    ) {
        entitiesByComponentType.getOrPut(type) { linkedSetOf() }.add(entity)
        if (type == TagsComponent::class) {
            val tags = entity.componentOrNull<TagsComponent>()?.tags ?: emptySet()
            for (tag in tags) {
                entitiesByTag.getOrPut(tag) { linkedSetOf() }.add(entity)
            }
        }
        if (type == RenderComponent::class) renderCacheDirty = true
    }

    internal fun onEntityComponentRemoved(
        entity: Entity,
        type: KClass<out EntityComponent>,
    ) {
        entitiesByComponentType[type]?.remove(entity)
        if (entitiesByComponentType[type].isNullOrEmpty()) {
            entitiesByComponentType.remove(type)
        }
        if (type == TagsComponent::class) {
            val toRemove = entitiesByTag.keys.filter { key -> entitiesByTag[key]?.contains(entity) == true }
            for (tag in toRemove) {
                entitiesByTag[tag]?.remove(entity)
                if (entitiesByTag[tag].isNullOrEmpty()) entitiesByTag.remove(tag)
            }
        }
        if (type == RenderComponent::class) renderCacheDirty = true
    }

    fun isVisibleInViewport(
        entity: Entity,
        viewport: Renderer2D.WorldViewport,
    ): Boolean = !shouldCull(entity, viewport)

    private fun flushQueues(engine: Engine) {
        if (pendingRemove.isNotEmpty()) {
            val ids = pendingRemove.toSet()
            val removed = entities.filter { it.id in ids }
            removed.forEach {
                unregisterEntity(it)
                it.onDetach(engine, this)
            }
            entities.removeAll { it.id in ids }
            pendingRemove.clear()
            renderCacheDirty = true
        }
        if (pendingAdd.isNotEmpty()) {
            for (entity in pendingAdd) {
                entities += entity
                registerEntity(entity)
                entity.onAttach(engine, this)
            }
            pendingAdd.clear()
            renderCacheDirty = true
        }
    }

    private fun registerEntity(entity: Entity) {
        entityById[entity.id] = entity
        entity.componentOrNull<TagsComponent>()?.tags?.forEach { tag ->
            entitiesByTag.getOrPut(tag) { linkedSetOf() }.add(entity)
        }
        entity.componentClasses().forEach { type ->
            entitiesByComponentType.getOrPut(type) { linkedSetOf() }.add(entity)
        }
    }

    private fun unregisterEntity(entity: Entity) {
        entityById.remove(entity.id)
        entity.componentOrNull<TagsComponent>()?.tags?.forEach { tag ->
            entitiesByTag[tag]?.remove(entity)
            if (entitiesByTag[tag].isNullOrEmpty()) entitiesByTag.remove(tag)
        }
        entity.componentClasses().forEach { type ->
            entitiesByComponentType[type]?.remove(entity)
            if (entitiesByComponentType[type].isNullOrEmpty()) entitiesByComponentType.remove(type)
        }
    }
}
