package com.kanvas.fx.game

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.EngineInputEvent
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.render.RenderMaterial
import com.kanvas.fx.render.Renderer2D

class GameGraph internal constructor(
    val scenes: List<GameSceneNode>,
    val currentScene: String?,
)

enum class InputPhase {
    Capture,
    Target,
    Bubble,
}

data class CameraNode(
    val position: Offset? = null,
    val zoom: Float? = null,
    val minZoom: Float? = null,
    val maxZoom: Float? = null,
)

data class TransformNode(
    val position: Offset = Offset.Zero,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

data class TagNode(
    val values: Set<String>,
)

data class PhysicsNode(
    val payload: Any? = null,
)

data class InputNode(
    val phase: InputPhase = InputPhase.Target,
    val priority: Int = 0,
    val onTap: ((com.kanvas.fx.core.Tap) -> Unit)? = null,
    val onDrag: ((com.kanvas.fx.core.Drag) -> Unit)? = null,
    val onKeyDown: ((com.kanvas.fx.core.KeyDown) -> Unit)? = null,
    val onKeyUp: ((com.kanvas.fx.core.KeyUp) -> Unit)? = null,
    val onScroll: ((com.kanvas.fx.core.Scroll) -> Unit)? = null,
    val onPinch: ((com.kanvas.fx.core.PinchZoom) -> Unit)? = null,
    val onAny: ((EngineInputEvent) -> Unit)? = null,
)

data class RenderNode(
    val zIndex: Int = 0,
    val material: RenderMaterial? = null,
    val block: Renderer2D.(Entity) -> Unit,
)

data class GameSystemNode(
    val id: String,
    val phase: SystemPhase = SystemPhase.PostPhysics,
    val enabled: Boolean = true,
    val order: Int = 0,
    val update: (FrameContext) -> Unit,
)

data class GameEntityNode(
    val key: String,
    val enabled: Boolean = true,
    val alwaysVisible: Boolean = false,
    val transform: TransformNode? = null,
    val tags: TagNode? = null,
    val physics: PhysicsNode? = null,
    val render: RenderNode? = null,
    val inputs: List<InputNode> = emptyList(),
)

data class GameSceneNode(
    val name: String,
    val setCurrent: Boolean = false,
    val timeScale: Float? = null,
    val camera: CameraNode? = null,
    val onEnter: ((Engine) -> Unit)? = null,
    val onExit: ((Engine) -> Unit)? = null,
    val systems: List<GameSystemNode> = emptyList(),
    val entities: List<GameEntityNode> = emptyList(),
    val inputs: List<InputNode> = emptyList(),
)

data class GameNodeMetadata(
    val managed: Boolean,
    val sceneName: String,
)
