package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import kotlin.math.abs
import kotlin.math.exp

/**
 * Camera input behavior unit that can react to input events and per-frame updates.
 */
interface CameraControl {
    fun onInput(scene: Scene, event: EngineInputEvent) {}
    fun onFrame(scene: Scene, frame: FrameContext) {}
}

/**
 * Keyboard camera controls:
 * - pan with WASD
 * - zoom with Q/E
 */
class KeyboardCameraControl(
    private val panSpeedWorldUnitsPerSecond: Float = 800f,
    private val zoomSpeedPerSecond: Float = 1.2f,
    private val leftKey: Long = Key.A.keyCode,
    private val rightKey: Long = Key.D.keyCode,
    private val upKey: Long = Key.W.keyCode,
    private val downKey: Long = Key.S.keyCode,
    private val zoomInKey: Long = Key.E.keyCode,
    private val zoomOutKey: Long = Key.Q.keyCode,
) : CameraControl {
    private val pressed = mutableSetOf<Long>()

    override fun onInput(scene: Scene, event: EngineInputEvent) {
        when (event) {
            is KeyDown -> pressed += event.keyCode
            is KeyUp -> pressed -= event.keyCode
            else -> Unit
        }
    }

    override fun onFrame(scene: Scene, frame: FrameContext) {
        val camera = scene.camera
        var dx = 0f
        var dy = 0f
        if (leftKey in pressed) dx -= 1f
        if (rightKey in pressed) dx += 1f
        if (upKey in pressed) dy -= 1f
        if (downKey in pressed) dy += 1f
        if (dx != 0f || dy != 0f) {
            val speed = panSpeedWorldUnitsPerSecond / camera.zoom
            camera.panBy(Offset(dx * speed * frame.deltaTimeSeconds, dy * speed * frame.deltaTimeSeconds))
        }
        if (zoomInKey in pressed) camera.zoomBy(zoomSpeedPerSecond * frame.deltaTimeSeconds)
        if (zoomOutKey in pressed) camera.zoomBy(-zoomSpeedPerSecond * frame.deltaTimeSeconds)
    }
}

/**
 * Gesture camera controls:
 * - pan by drag
 * - zoom by scroll (when available on platform)
 */
class GestureCameraControl(
    private val panEnabled: Boolean = true,
    private val zoomEnabled: Boolean = true,
    private val zoomStep: Float = 0.14f,
    private val requireModifierKey: Long? = null,
    private val enabled: () -> Boolean = { true },
) : CameraControl {
    private val pressed = mutableSetOf<Long>()

    override fun onInput(scene: Scene, event: EngineInputEvent) {
        if (!enabled()) return
        when (event) {
            is KeyDown -> pressed += event.keyCode
            is KeyUp -> pressed -= event.keyCode
            is Drag -> {
                if (!panEnabled) return
                if (requireModifierKey != null && requireModifierKey !in pressed) return
                val camera = scene.camera
                // dragDelta already comes in world units from EngineCanvas
                camera.panBy(Offset(-event.dragDelta.x, -event.dragDelta.y))
            }
            is Scroll -> {
                if (!zoomEnabled) return
                val camera = scene.camera
                val amount = (-event.delta.y).coerceIn(-4f, 4f)
                if (abs(amount) < 0.0001f) return
                camera.zoomByFactor(
                    factor = exp(amount * zoomStep),
                    anchorWorld = event.position,
                )
            }
            is PinchZoom -> {
                if (!zoomEnabled) return
                val camera = scene.camera
                camera.zoomByFactor(
                    factor = event.zoomFactor.coerceIn(0.85f, 1.15f),
                    anchorWorld = event.position,
                )
            }
            else -> Unit
        }
    }
}
