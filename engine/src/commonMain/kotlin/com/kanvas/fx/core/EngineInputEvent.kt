package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset

/**
 * Input event stream consumed by scene and object callbacks.
 */
sealed interface EngineInputEvent {
    /** Pointer identifier when event originates from pointer input. */
    val pointerId: Long?
    /** World-space pointer position for pointer-driven events. */
    val position: Offset?
}

/**
 * Pointer/button press event.
 *
 * @property pointerId pointer identifier.
 * @property position pointer position in world space.
 */
data class PointerDown(
    override val pointerId: Long,
    override val position: Offset,
) : EngineInputEvent

/**
 * Pointer move event.
 *
 * @property pointerId pointer identifier.
 * @property position pointer position in world space.
 */
data class PointerMove(
    override val pointerId: Long,
    override val position: Offset,
) : EngineInputEvent

/**
 * Pointer/button release event.
 *
 * @property pointerId pointer identifier.
 * @property position pointer position in world space.
 */
data class PointerUp(
    override val pointerId: Long,
    override val position: Offset,
) : EngineInputEvent

/**
 * Keyboard key pressed event.
 *
 * @property keyCode platform key code.
 * @property pointerId optional pointer context id.
 * @property position optional pointer context position.
 */
data class KeyDown(
    val keyCode: Long,
    override val pointerId: Long? = null,
    override val position: Offset? = null,
) : EngineInputEvent

/**
 * Keyboard key released event.
 *
 * @property keyCode platform key code.
 * @property pointerId optional pointer context id.
 * @property position optional pointer context position.
 */
data class KeyUp(
    val keyCode: Long,
    override val pointerId: Long? = null,
    override val position: Offset? = null,
) : EngineInputEvent

/**
 * Pointer wheel/trackpad scroll event.
 *
 * @property pointerId optional pointer context id.
 * @property position optional pointer context position.
 * @property delta scroll delta vector.
 */
data class Scroll(
    override val pointerId: Long? = null,
    override val position: Offset?,
    val delta: Offset,
) : EngineInputEvent

/**
 * Pinch zoom gesture event.
 *
 * @property pointerId optional pointer context id.
 * @property position pinch centroid in world space.
 * @property zoomFactor multiplicative zoom factor where `1f` means no zoom change.
 */
data class PinchZoom(
    override val pointerId: Long? = null,
    override val position: Offset,
    val zoomFactor: Float,
) : EngineInputEvent

/**
 * Tap gesture event.
 *
 * @property pointerId pointer identifier.
 * @property position tap position in world space.
 */
data class Tap(
    override val pointerId: Long,
    override val position: Offset,
) : EngineInputEvent

/**
 * Drag gesture start event.
 *
 * @property pointerId pointer identifier.
 * @property position drag start position in world space.
 */
data class DragStart(
    override val pointerId: Long,
    override val position: Offset,
) : EngineInputEvent

/**
 * Drag gesture update event.
 *
 * @property pointerId pointer identifier.
 * @property position current drag position in world space.
 * @property dragDelta drag delta in world units.
 */
data class Drag(
    override val pointerId: Long,
    override val position: Offset,
    val dragDelta: Offset,
) : EngineInputEvent

/**
 * Drag gesture end/cancel event.
 *
 * @property pointerId pointer identifier.
 * @property position end position, null when canceled.
 */
data class DragEnd(
    override val pointerId: Long,
    override val position: Offset?,
) : EngineInputEvent
