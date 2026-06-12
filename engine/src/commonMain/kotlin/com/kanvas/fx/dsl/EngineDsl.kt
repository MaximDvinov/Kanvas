package com.kanvas.fx.dsl

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.CameraControl
import com.kanvas.fx.core.SpriteAnimationSystem
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.EngineConfig
import com.kanvas.fx.core.EngineInputEvent
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.EntityBehavior
import com.kanvas.fx.core.EntityComponent
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.GestureCameraControl
import com.kanvas.fx.core.InputComponent
import com.kanvas.fx.core.KeyboardCameraControl
import com.kanvas.fx.core.BoundsComponent
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.SceneSystemSpec
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.core.TagsComponent
import com.kanvas.fx.core.TransformComponent
import com.kanvas.fx.core.VisibilityComponent
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.ShaderAsset
import com.kanvas.fx.render.ShaderSource
import com.kanvas.fx.render.TextureAsset
import com.kanvas.fx.render.TextureSource

/**
 * Builds an [Engine] with scenes, reusable entity templates, systems, and optional assets.
 *
 * ```kotlin
 * val engine = engine {
 *     entityTemplate("spark") {
 *         transform { scale(0.5f) }
 *         tags("effect")
 *     }
 *
 *     scene("level", setCurrent = true) {
 *         camera { desktopDefaults() }
 *         entities {
 *             spawn("spark", id = "spark-1") {
 *                 transform { position(100f, 120f) }
 *             }
 *         }
 *     }
 * }
 * ```
 */
fun engine(
    config: EngineConfig = EngineConfig(),
    block: EngineBuilder.() -> Unit,
): Engine {
    val builder = EngineBuilder(config)
    builder.block()
    return builder.build()
}

/**
 * Top-level engine DSL builder.
 *
 * The builder is evaluated once during [engine] construction. Runtime changes should be
 * made through [Engine], [Scene], or [SceneObjects].
 */
class EngineBuilder internal constructor(
    private val config: EngineConfig,
) {
    private val engine = Engine(config)
    val assets = AssetRegistry()
    private val templates = linkedMapOf<String, EntityTemplate>()

    /**
     * Registers a scene definition.
     *
     * Scene names must be unique if you want to preserve previous scenes. Registering the
     * same name again replaces the scene stored by [Engine].
     */
    fun scene(
        name: String,
        setCurrent: Boolean = engine.currentScene == null,
        block: SceneBuilder.() -> Unit = {},
    ) {
        require(name.isNotBlank()) { "Scene name must not be blank." }
        val builder = SceneBuilder(name, templates)
        builder.block()
        engine.addScene(builder.build(), setCurrent)
    }

    /**
     * Registers a reusable entity template.
     *
     * Templates are copied into each spawned entity and may be overridden by the spawn block.
     */
    fun entityTemplate(name: String, block: EntityBuilder.() -> Unit) {
        require(name.isNotBlank()) { "Entity template name must not be blank." }
        templates[name] = EntityTemplate(name, block)
    }

    /**
     * Registers a texture asset by id and filesystem or resource path.
     *
     * The path is resolved lazily by [AssetRegistry] when the texture is first requested.
     */
    fun texture(id: String, path: String) {
        require(id.isNotBlank()) { "Texture id must not be blank." }
        require(path.isNotBlank()) { "Texture path must not be blank." }
        assets.registerTexture(TextureAsset(id, TextureSource.Path(path)))
    }

    /**
     * Registers text shader source.
     */
    fun shader(id: String, text: String) {
        require(id.isNotBlank()) { "Shader id must not be blank." }
        require(text.isNotBlank()) { "Shader source text must not be blank." }
        assets.registerShader(ShaderAsset(id, ShaderSource.Text(text)))
    }

    /**
     * Registers external shader DSL object.
     */
    fun shaderDsl(id: String, dslObject: Any) {
        require(id.isNotBlank()) { "Shader id must not be blank." }
        assets.registerShader(ShaderAsset(id, ShaderSource.ExternalDsl(dslObject)))
    }

    /**
     * Finalizes and returns engine instance.
     */
    fun build(): Engine = engine
}

/**
 * Scene definition DSL.
 *
 * Use this scope to configure scene lifecycle, camera controls, systems, and initial
 * entities. Entities spawned here are queued and attached when the scene starts.
 */
class SceneBuilder internal constructor(
    private val name: String,
    private val templates: Map<String, EntityTemplate>,
) {
    private val scene = Scene(name)

    /**
     * Configures scene camera.
     */
    fun camera(block: CameraBuilder.() -> Unit) {
        CameraBuilder(scene).apply(block)
    }

    /**
     * Configures scene-level visibility and debug culling output.
     */
    fun visibility(block: VisibilityBuilder.() -> Unit) {
        VisibilityBuilder(scene).apply(block)
    }

    /**
     * Configures scene-level render quality switches.
     */
    fun renderQuality(block: RenderQualityBuilder.() -> Unit) {
        RenderQualityBuilder(scene).apply(block)
    }

    /**
     * Sets scene-local time scale multiplier.
     */
    fun timeScale(value: Float) {
        require(value.isFinite() && value >= 0f) { "Scene timeScale must be finite and >= 0." }
        scene.timeScale = value
    }

    /**
     * Scene lifecycle callback.
     */
    fun onStart(block: (Engine) -> Unit) {
        scene.onStart = block
    }

    /**
     * Scene lifecycle callback.
     */
    fun onStop(block: (Engine) -> Unit) {
        scene.onStop = block
    }

    /**
     * Scene update callback.
     */
    fun onUpdate(block: (FrameContext) -> Unit) {
        scene.onUpdate = block
    }

    /**
     * Scene input callback.
     */
    fun onInput(block: (EngineInputEvent) -> Unit) {
        scene.onInput = block
    }

    /**
     * Adds ordered systems to the scene.
     */
    fun systems(block: SystemsBuilder.() -> Unit) {
        val builder = SystemsBuilder()
        builder.block()
        builder.build().forEach(scene::addSystem)
    }

    /**
     * Declares scene entities and template spawns.
     */
    fun entities(block: EntitiesBuilder.() -> Unit) {
        val builder = EntitiesBuilder(scene, templates)
        builder.block()
    }

    internal fun build(): Scene = scene
}

/**
 * Entity declaration scope for a scene.
 *
 * ```kotlin
 * entities {
 *     entity("player") {
 *         transform { position(32f, 64f) }
 *         bounds { rect(-16f, -16f, 32f, 32f) }
 *         tags("player", "actor")
 *     }
 * }
 * ```
 */
class EntitiesBuilder internal constructor(
    private val scene: Scene,
    private val templates: Map<String, EntityTemplate>,
) {
    /**
     * Creates and spawns a new entity.
     */
    fun entity(
        id: String,
        block: EntityBuilder.() -> Unit,
    ): Entity {
        require(id.isNotBlank()) { "Entity id must not be blank." }
        val e = EntityBuilder(id).apply(block).build()
        scene.spawn(e)
        return e
    }

    /**
     * Spawns entity from a previously registered template.
     */
    fun spawn(
        template: String,
        id: String,
        overrides: (EntityBuilder.() -> Unit)? = null,
    ): Entity {
        require(id.isNotBlank()) { "Entity id must not be blank." }
        val spec = templates[template] ?: error("Template '$template' is not registered.")
        val builder = EntityBuilder(id)
        builder.apply(spec.block)
        overrides?.let { builder.apply(it) }
        val entity = builder.build()
        scene.spawn(entity)
        return entity
    }

    /**
     * Spawns multiple prebuilt entities.
     */
    fun spawnMany(items: List<Entity>) {
        scene.spawnMany(items)
    }
}

/**
 * Entity construction DSL.
 *
 * Each component builder replaces the previous component of the same kind, so calling
 * `transform { ... }` multiple times leaves one final [TransformComponent].
 */
class EntityBuilder internal constructor(
    private val id: String,
) {
    private val components = mutableListOf<EntityComponent>()
    private val behaviors = mutableListOf<EntityBehavior>()

    /**
     * Configures [TransformComponent].
     */
    fun transform(block: TransformBuilder.() -> Unit) {
        val current = components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
        val updated = TransformBuilder(current).apply(block).build()
        components.removeAll { it is TransformComponent }
        components += updated
    }

    /**
     * Adds/replaces [InputComponent].
     */
    fun input(block: (Entity, EngineInputEvent) -> Unit) {
        components.removeAll { it is InputComponent }
        components += InputComponent(block)
    }

    /**
     * Adds/replaces [RenderComponent].
     */
    fun render(
        zIndex: Int = 0,
        material: com.kanvas.fx.render.RenderMaterial? = null,
        block: com.kanvas.fx.render.Renderer2D.(Entity) -> Unit,
    ) {
        components.removeAll { it is RenderComponent }
        components += RenderComponent(zIndex = zIndex, material = material, renderer = block)
    }

    /**
     * Adds or replaces [BoundsComponent] used by viewport culling.
     */
    fun bounds(block: BoundsBuilder.() -> Unit) {
        val current = components.filterIsInstance<BoundsComponent>().firstOrNull()
            ?: BoundsComponent(0f, 0f, 0f, 0f)
        val updated = BoundsBuilder(current).apply(block).build()
        components.removeAll { it is BoundsComponent }
        components += updated
    }

    /**
     * Convenience helper for rectangular bounds in local authoring coordinates.
     */
    fun autoBoundsRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        require(width >= 0f && height >= 0f) { "Bounds rect width and height must be >= 0." }
        bounds { rect(x, y, width, height) }
    }

    /**
     * Convenience helper that creates square bounds around a circle.
     */
    fun autoBoundsCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        require(radius >= 0f) { "Bounds circle radius must be >= 0." }
        bounds { circle(centerX, centerY, radius) }
    }

    /**
     * Convenience helper for texture-sized rectangular bounds.
     */
    fun autoBoundsTexture(
        topLeftX: Float,
        topLeftY: Float,
        width: Float,
        height: Float,
    ) {
        require(width >= 0f && height >= 0f) { "Bounds texture width and height must be >= 0." }
        bounds { rect(topLeftX, topLeftY, width, height) }
    }

    /**
     * Marks the entity as always visible, bypassing viewport culling.
     */
    fun alwaysVisible(value: Boolean = true) {
        val visibility = components.filterIsInstance<VisibilityComponent>().firstOrNull() ?: VisibilityComponent()
        visibility.alwaysVisible = value
        components.removeAll { it is VisibilityComponent }
        components += visibility
    }

    /**
     * Adds string tags to [TagsComponent].
     */
    fun tags(vararg values: String) {
        val tags = components.filterIsInstance<TagsComponent>().firstOrNull()?.tags ?: linkedSetOf()
        tags += values
        components.removeAll { it is TagsComponent }
        components += TagsComponent(tags)
    }

    /**
     * Adds/replaces arbitrary component by runtime class.
     */
    fun component(component: EntityComponent) {
        components.removeAll { it::class == component::class }
        components += component
    }

    /**
     * Appends behavior instance.
     */
    fun behavior(behavior: EntityBehavior) {
        behaviors += behavior
    }

    internal fun build(): Entity {
        val entity = Entity(id)
        components.forEach { entity.addComponent(it) }
        behaviors.forEach { entity.addBehavior(it) }
        entity.alwaysVisible = entity.componentOrNull<VisibilityComponent>()?.alwaysVisible == true
        if (entity.componentOrNull<TransformComponent>() == null) {
            entity.addComponent(TransformComponent())
        }
        return entity
    }
}

/**
 * Camera configuration DSL.
 *
 * Camera controls registered here receive input before scene-level input handlers.
 */
class CameraBuilder internal constructor(
    private val scene: Scene,
) {
    /**
     * Sets camera world position.
     */
    fun position(x: Float, y: Float) {
        scene.camera.position = Offset(x, y)
    }

    /**
     * Sets absolute zoom value.
     */
    fun zoom(value: Float) {
        require(value.isFinite() && value > 0f) { "Camera zoom must be finite and > 0." }
        scene.camera.setZoomLevel(value)
    }

    /**
     * Sets zoom limits and clamps current zoom.
     */
    fun zoomRange(min: Float, max: Float) {
        require(min.isFinite() && max.isFinite()) { "Camera zoom range must be finite." }
        require(min > 0f) { "Camera zoom range min must be > 0." }
        require(max >= min) { "Camera zoom range max must be >= min." }
        scene.camera.minZoom = min
        scene.camera.maxZoom = max
        scene.camera.setZoomLevel(scene.camera.zoom)
    }

    /**
     * Registers custom camera control.
     */
    fun control(control: CameraControl) {
        scene.addCameraControl(control)
    }

    /**
     * Adds keyboard camera controls.
     */
    fun keyboardDefaults(
        panSpeedWorldUnitsPerSecond: Float = 800f,
        zoomSpeedPerSecond: Float = 1.2f,
    ) {
        require(panSpeedWorldUnitsPerSecond.isFinite() && panSpeedWorldUnitsPerSecond >= 0f) {
            "Keyboard pan speed must be finite and >= 0."
        }
        require(zoomSpeedPerSecond.isFinite() && zoomSpeedPerSecond >= 0f) {
            "Keyboard zoom speed must be finite and >= 0."
        }
        scene.addCameraControl(
            KeyboardCameraControl(
                panSpeedWorldUnitsPerSecond = panSpeedWorldUnitsPerSecond,
                zoomSpeedPerSecond = zoomSpeedPerSecond,
            ),
        )
    }

    /**
     * Adds gesture camera controls.
     */
    fun gestureDefaults(
        panEnabled: Boolean = true,
        zoomEnabled: Boolean = true,
        zoomStep: Float = 0.06f,
        requireModifierKeyCode: Long? = null,
        enabled: () -> Boolean = { true },
    ) {
        require(zoomStep.isFinite() && zoomStep >= 0f) { "Gesture zoomStep must be finite and >= 0." }
        scene.addCameraControl(
            GestureCameraControl(
                panEnabled = panEnabled,
                zoomEnabled = zoomEnabled,
                zoomStep = zoomStep,
                requireModifierKey = requireModifierKeyCode,
                enabled = enabled,
            ),
        )
    }

    /**
     * Adds default desktop camera controls.
     */
    fun desktopDefaults() {
        keyboardDefaults()
        gestureDefaults()
    }

    /**
     * Adds default touch camera controls.
     */
    fun touchDefaults() {
        gestureDefaults(panEnabled = true, zoomEnabled = true)
    }
}

/**
 * DSL for [com.kanvas.fx.core.VisibilityConfig].
 */
class VisibilityBuilder internal constructor(
    private val scene: Scene,
) {
    /**
     * Enables or disables scene rendering visibility checks.
     */
    fun enabled(value: Boolean) {
        scene.visibility.enabled = value
    }

    /**
     * Shows renderer diagnostics such as culled and rendered entity counts.
     */
    fun debugOverlay(value: Boolean) {
        scene.visibility.debugOverlay = value
    }
}

/**
 * DSL for [com.kanvas.fx.core.RenderQualityConfig].
 */
class RenderQualityBuilder internal constructor(
    private val scene: Scene,
) {
    /** Enables material effects and runtime shader passes where supported. */
    fun effectsEnabled(value: Boolean) {
        scene.renderQuality.effectsEnabled = value
    }

    /** Sets the maximum number of lights evaluated per rendered object. */
    fun maxLightsPerObject(value: Int) {
        require(value >= 0) { "maxLightsPerObject must be >= 0." }
        scene.renderQuality.maxLightsPerObject = value
    }

    /** Sets glow quality multiplier; higher values may cost more GPU time. */
    fun glowQuality(value: Float) {
        require(value.isFinite() && value >= 0f) { "glowQuality must be finite and >= 0." }
        scene.renderQuality.glowQuality = value
    }
}

/**
 * System registration DSL.
 */
class SystemsBuilder internal constructor() {
    private val systems = mutableListOf<SceneSystemSpec>()

    /**
     * Registers a system descriptor.
     */
    fun add(
        id: String,
        phase: SystemPhase = SystemPhase.PostPhysics,
        order: Int = 0,
        enabled: Boolean = true,
        system: SceneSystem,
    ) {
        require(id.isNotBlank()) { "System id must not be blank." }
        require(systems.none { it.id == id }) { "System id '$id' is already registered in this scene." }
        systems += SceneSystemSpec(id = id, system = system, phase = phase, order = order, enabled = enabled)
    }

    /**
     * Registers system with generated identifier.
     */
    fun add(system: SceneSystem) {
        val nextId = "system-${systems.size}"
        add(id = nextId, system = system)
    }

    /**
     * Registers default 2D sprite animation system.
     */
    fun spriteAnimation2d(
        id: String = "sprite-animation-2d",
        phase: SystemPhase = SystemPhase.PostPhysics,
        order: Int = 0,
        enabled: Boolean = true,
    ) {
        add(
            id = id,
            phase = phase,
            order = order,
            enabled = enabled,
            system = SpriteAnimationSystem(),
        )
    }

    internal fun build(): List<SceneSystemSpec> = systems.toList()
}

/**
 * Transform mutation DSL.
 *
 * Values are stored in [TransformComponent] and interpreted in world space.
 */
class TransformBuilder internal constructor(
    private var value: TransformComponent,
) {
    /**
     * Sets transform position.
     */
    fun position(x: Float, y: Float) {
        value = value.copy(position = Offset(x, y))
    }

    /**
     * Sets transform rotation in degrees.
     */
    fun rotation(degrees: Float) {
        value = value.copy(rotationDegrees = degrees)
    }

    /**
     * Sets transform scale.
     */
    fun scale(x: Float, y: Float = x) {
        require(x.isFinite() && y.isFinite()) { "Transform scale values must be finite." }
        value = value.copy(scaleX = x, scaleY = y)
    }

    internal fun build(): TransformComponent = value
}

/**
 * Bounds construction DSL for [BoundsComponent].
 */
class BoundsBuilder internal constructor(
    private var value: BoundsComponent,
) {
    /**
     * Sets explicit axis-aligned bounds.
     */
    fun aabb(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        value = BoundsComponent(left = left, top = top, right = right, bottom = bottom)
    }

    /**
     * Sets bounds from top-left position and size.
     */
    fun rect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        require(width >= 0f && height >= 0f) { "Bounds rect width and height must be >= 0." }
        value = BoundsComponent(left = x, top = y, right = x + width, bottom = y + height)
    }

    /**
     * Sets square bounds that fully contain a circle.
     */
    fun circle(
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        require(radius >= 0f) { "Bounds circle radius must be >= 0." }
        value = BoundsComponent(
            left = centerX - radius,
            top = centerY - radius,
            right = centerX + radius,
            bottom = centerY + radius,
        )
    }

    internal fun build(): BoundsComponent = value
}

/**
 * Reusable template definition for entity construction.
 *
 * Templates are stored by [EngineBuilder] and applied by [EntitiesBuilder.spawn].
 */
data class EntityTemplate(
    val name: String,
    val block: EntityBuilder.() -> Unit,
)
