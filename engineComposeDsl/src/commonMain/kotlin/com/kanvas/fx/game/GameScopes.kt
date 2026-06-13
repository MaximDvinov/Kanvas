package com.kanvas.fx.game

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.render.RenderMaterial
import com.kanvas.fx.render.Renderer2D

class GameHostScope internal constructor() {
    private val scenes = mutableListOf<GameSceneNode>()

    fun Scene(
        name: String,
        setCurrent: Boolean = false,
        block: GameSceneScope.() -> Unit,
    ) {
        val scope = GameSceneScope(name, setCurrent)
        scope.block()
        scenes += scope.build()
    }

    internal fun build(currentScene: String?): GameGraph = GameGraph(
        scenes = scenes.toList(),
        currentScene = currentScene ?: scenes.firstOrNull { it.setCurrent }?.name,
    )
}

class GameSceneScope internal constructor(
    private val name: String,
    private val setCurrent: Boolean,
) {
    private var timeScale: Float? = null
    private var camera: CameraNode? = null
    private var onEnter: ((com.kanvas.fx.core.Engine) -> Unit)? = null
    private var onExit: ((com.kanvas.fx.core.Engine) -> Unit)? = null
    private val systems = mutableListOf<GameSystemNode>()
    private val entities = mutableListOf<GameEntityNode>()
    private val inputs = mutableListOf<InputNode>()

    fun timeScale(value: Float) {
        timeScale = value
    }

    fun camera(
        position: Offset? = null,
        zoom: Float? = null,
        minZoom: Float? = null,
        maxZoom: Float? = null,
    ) {
        camera = CameraNode(position = position, zoom = zoom, minZoom = minZoom, maxZoom = maxZoom)
    }

    fun System(
        id: String,
        phase: SystemPhase = SystemPhase.PostPhysics,
        enabled: Boolean = true,
        order: Int = 0,
        update: (com.kanvas.fx.core.FrameContext) -> Unit,
    ) {
        require(id.isNotBlank()) { "System id must not be blank." }
        systems += GameSystemNode(id = id, phase = phase, enabled = enabled, order = order, update = update)
    }

    fun onEnter(block: (com.kanvas.fx.core.Engine) -> Unit) {
        onEnter = block
    }

    fun onExit(block: (com.kanvas.fx.core.Engine) -> Unit) {
        onExit = block
    }

    fun <S, A> ReducerSystem(
        id: String,
        store: GameStore<S, A>,
        phase: SystemPhase = SystemPhase.PostPhysics,
        enabled: Boolean = true,
        order: Int = 0,
        onFrame: (state: S, frame: com.kanvas.fx.core.FrameContext) -> S,
        onStateCommitted: ((S) -> Unit)? = null,
    ) {
        System(id = id, phase = phase, enabled = enabled, order = order) { frame ->
            store.reducePendingActions()
            val next = onFrame(store.state, frame)
            store.setState(next)
            onStateCommitted?.invoke(next)
        }
    }

    fun Entity(
        key: String,
        block: GameEntityScope.() -> Unit,
    ) {
        val scope = GameEntityScope(key)
        scope.block()
        entities += scope.build()
    }

    fun input(
        phase: InputPhase = InputPhase.Target,
        priority: Int = 0,
        onTap: ((com.kanvas.fx.core.Tap) -> Unit)? = null,
        onDrag: ((com.kanvas.fx.core.Drag) -> Unit)? = null,
        onKeyDown: ((com.kanvas.fx.core.KeyDown) -> Unit)? = null,
        onKeyUp: ((com.kanvas.fx.core.KeyUp) -> Unit)? = null,
        onScroll: ((com.kanvas.fx.core.Scroll) -> Unit)? = null,
        onPinch: ((com.kanvas.fx.core.PinchZoom) -> Unit)? = null,
        onAny: ((com.kanvas.fx.core.EngineInputEvent) -> Unit)? = null,
    ) {
        inputs += InputNode(
            phase = phase,
            priority = priority,
            onTap = onTap,
            onDrag = onDrag,
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp,
            onScroll = onScroll,
            onPinch = onPinch,
            onAny = onAny,
        )
    }

    internal fun build(): GameSceneNode = GameSceneNode(
        name = name,
        setCurrent = setCurrent,
        timeScale = timeScale,
        camera = camera,
        onEnter = onEnter,
        onExit = onExit,
        systems = systems.toList(),
        entities = entities.toList(),
        inputs = inputs.toList(),
    )
}

class GameEntityScope internal constructor(
    private val key: String,
) {
    private var enabled: Boolean = true
    private var alwaysVisible: Boolean = false
    private var transform: TransformNode? = null
    private var tags: TagNode? = null
    private var physics: PhysicsNode? = null
    private var render: RenderNode? = null
    private val inputs = mutableListOf<InputNode>()

    fun enabled(value: Boolean) {
        enabled = value
    }

    fun alwaysVisible(value: Boolean = true) {
        alwaysVisible = value
    }

    fun Transform(
        position: Offset = Offset.Zero,
        rotationDegrees: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
    ) {
        transform = TransformNode(position = position, rotationDegrees = rotationDegrees, scaleX = scaleX, scaleY = scaleY)
    }

    fun Tag(vararg values: String) {
        tags = TagNode(values.toSet())
    }

    fun Physics(payload: Any? = null) {
        physics = PhysicsNode(payload)
    }

    fun Render(
        zIndex: Int = 0,
        material: RenderMaterial? = null,
        block: Renderer2D.(com.kanvas.fx.core.Entity) -> Unit,
    ) {
        render = RenderNode(zIndex = zIndex, material = material, block = block)
    }

    fun input(
        phase: InputPhase = InputPhase.Target,
        priority: Int = 0,
        onTap: ((com.kanvas.fx.core.Tap) -> Unit)? = null,
        onDrag: ((com.kanvas.fx.core.Drag) -> Unit)? = null,
        onKeyDown: ((com.kanvas.fx.core.KeyDown) -> Unit)? = null,
        onKeyUp: ((com.kanvas.fx.core.KeyUp) -> Unit)? = null,
        onScroll: ((com.kanvas.fx.core.Scroll) -> Unit)? = null,
        onPinch: ((com.kanvas.fx.core.PinchZoom) -> Unit)? = null,
        onAny: ((com.kanvas.fx.core.EngineInputEvent) -> Unit)? = null,
    ) {
        inputs += InputNode(
            phase = phase,
            priority = priority,
            onTap = onTap,
            onDrag = onDrag,
            onKeyDown = onKeyDown,
            onKeyUp = onKeyUp,
            onScroll = onScroll,
            onPinch = onPinch,
            onAny = onAny,
        )
    }

    internal fun build(): GameEntityNode = GameEntityNode(
        key = key,
        enabled = enabled,
        alwaysVisible = alwaysVisible,
        transform = transform,
        tags = tags,
        physics = physics,
        render = render,
        inputs = inputs.toList(),
    )
}
