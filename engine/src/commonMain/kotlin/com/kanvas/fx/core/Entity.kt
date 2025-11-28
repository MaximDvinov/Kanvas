package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.render.Renderer2D
import kotlin.reflect.KClass

/**
 * Marker interface for components that can be attached to an [Entity].
 */
interface EntityComponent

/**
 * Lifecycle hooks for reusable entity behavior units.
 *
 * Behaviors are invoked by the scene runtime when an entity is attached, detached,
 * updated, receives input, or is rendered.
 */
interface EntityBehavior {
    /**
     * Called once when [entity] is attached to a started [scene].
     */
    fun onAttach(entity: Entity, engine: Engine, scene: Scene) = Unit

    /**
     * Called once before [entity] is removed from [scene] or when the scene stops.
     */
    fun onDetach(entity: Entity, engine: Engine, scene: Scene) = Unit

    /**
     * Called every simulation update while [entity] is enabled.
     */
    fun onUpdate(entity: Entity, frame: FrameContext) = Unit

    /**
     * Called for every input event routed to [entity] while it is enabled.
     */
    fun onInput(entity: Entity, event: EngineInputEvent) = Unit

    /**
     * Called during render pass after [RenderComponent] rendering.
     */
    fun onRender(entity: Entity, renderer: Renderer2D) = Unit
}

/**
 * Standard transform component in world space.
 */
data class TransformComponent(
    var position: Offset = Offset.Zero,
    var rotationDegrees: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
) : EntityComponent

/**
 * Input handler component for per-entity input logic.
 */
class InputComponent(
    var handler: (Entity, EngineInputEvent) -> Unit = { _, _ -> },
) : EntityComponent

/**
 * Render component with draw callback and z-order.
 */
class RenderComponent(
    var zIndex: Int = 0,
    var material: com.kanvas.fx.render.RenderMaterial? = null,
    var renderer: Renderer2D.(Entity) -> Unit = {},
) : EntityComponent

/**
 * Axis-aligned world-space bounds used for visibility culling.
 */
data class BoundsComponent(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
) : EntityComponent {
    init {
        require(right >= left) { "Bounds right must be >= left." }
        require(bottom >= top) { "Bounds bottom must be >= top." }
    }
}

/**
 * Optional visibility settings attached to entity.
 */
data class VisibilityComponent(
    var alwaysVisible: Boolean = false,
) : EntityComponent

/**
 * Arbitrary string tags for grouping and filtering entities.
 */
class TagsComponent(
    val tags: MutableSet<String> = linkedSetOf(),
) : EntityComponent

/**
 * Generic key/value storage for custom runtime metadata.
 */
class MetadataComponent(
    val values: MutableMap<String, Any?> = linkedMapOf(),
) : EntityComponent

/**
 * Runtime scene entity composed from components and behaviors.
 */
class Entity(
    val id: String,
) {
    /**
     * When `false`, entity update/input/render callbacks are skipped.
     */
    var enabled: Boolean = true
    var alwaysVisible: Boolean = false

    private val components = mutableListOf<EntityComponent>()
    private val behaviors = mutableListOf<EntityBehavior>()
    private var ownerScene: Scene? = null

    /**
     * Adds or replaces a component of the same runtime type.
     *
     * @return this entity for fluent configuration.
     */
    fun addComponent(component: EntityComponent): Entity {
        removeComponent(component::class)
        components += component
        ownerScene?.onEntityComponentAdded(this, component::class)
        return this
    }

    /**
     * Removes a component by its class.
     */
    fun <T : EntityComponent> removeComponent(type: KClass<T>) {
        val removed = components.removeAll { type.isInstance(it) }
        if (removed) {
            ownerScene?.onEntityComponentRemoved(this, type)
        }
    }

    /**
     * Adds a behavior instance.
     *
     * @return this entity for fluent configuration.
     */
    fun addBehavior(behavior: EntityBehavior): Entity {
        behaviors += behavior
        return this
    }

    /**
     * Returns a component of [type], or null if it is not attached.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : EntityComponent> componentOrNull(type: KClass<T>): T? {
        return components.firstOrNull { type.isInstance(it) } as? T
    }

    internal fun componentClasses(): List<KClass<out EntityComponent>> =
        components.map { it::class }

    /**
     * Reified shortcut for [componentOrNull].
     */
    inline fun <reified T : EntityComponent> componentOrNull(): T? = componentOrNull(T::class)

    /**
     * Returns a required component or throws if missing.
     */
    inline fun <reified T : EntityComponent> requireComponent(): T =
        componentOrNull<T>() ?: error("Entity '$id' does not have ${T::class.simpleName}.")

    /**
     * Checks whether [TagsComponent] contains [tag].
     */
    fun hasTag(tag: String): Boolean = componentOrNull<TagsComponent>()?.tags?.contains(tag) == true

    fun boundsOrNull(): BoundsComponent? = componentOrNull()

    internal fun onAttach(engine: Engine, scene: Scene) {
        ownerScene = scene
        behaviors.forEach { it.onAttach(this, engine, scene) }
    }

    internal fun onDetach(engine: Engine, scene: Scene) {
        behaviors.forEach { it.onDetach(this, engine, scene) }
        ownerScene = null
    }

    internal fun onInput(event: EngineInputEvent) {
        if (!enabled) return
        componentOrNull<InputComponent>()?.handler?.invoke(this, event)
        behaviors.forEach { it.onInput(this, event) }
    }

    internal fun onUpdate(frame: FrameContext) {
        if (!enabled) return
        behaviors.forEach { it.onUpdate(this, frame) }
    }

    internal fun onRender(renderer: Renderer2D) {
        if (!enabled) return
        val renderComponent = componentOrNull<RenderComponent>()
        if (renderComponent != null) {
            renderer.beginEntity(
                entityId = id,
                material = renderComponent.material,
            )
            renderComponent.renderer.invoke(renderer, this)
            renderer.endEntity()
        }
        behaviors.forEach { it.onRender(this, renderer) }
    }

    internal fun renderOrder(): Int = componentOrNull<RenderComponent>()?.zIndex ?: 0
}
