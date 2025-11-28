package com.kanvas.fx.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipRect
import com.kanvas.fx.core.Drag
import com.kanvas.fx.core.DragEnd
import com.kanvas.fx.core.DragStart
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.PointerDown
import com.kanvas.fx.core.PointerMove
import com.kanvas.fx.core.PointerUp
import com.kanvas.fx.core.Tap
import com.kanvas.fx.core.KeyDown
import com.kanvas.fx.core.KeyUp
import com.kanvas.fx.core.PinchZoom
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.render.Renderer2D
import kotlin.math.abs

/**
 * Compose rendering surface that runs the frame loop, forwards input, and renders the current scene.
 */
@Composable
fun EngineCanvas(
    engine: Engine,
    modifier: Modifier = Modifier,
    assets: AssetRegistry = remember { AssetRegistry() },
    focusRequestKey: Int = 0,
) {
    var frameInvalidationTick by remember(engine) { mutableLongStateOf(0L) }
    var suppressNextTap by remember(engine) { mutableStateOf(false) }
    var multiTouchTransformActive by remember(engine) { mutableStateOf(false) }
    var dragInProgress by remember(engine) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(engine) {
        var lastFrameNanos: Long? = null
        while (true) {
            withFrameNanos { frameNanos ->
                val previous = lastFrameNanos
                lastFrameNanos = frameNanos
                val deltaSeconds = if (previous == null) 0f else ((frameNanos - previous).coerceAtLeast(0L) / 1_000_000_000f)
                engine.tick(deltaSeconds)
            }
            frameInvalidationTick++
        }
    }

    LaunchedEffect(focusRequestKey) {
        focusRequester.requestFocus()
    }

    Canvas(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> engine.dispatchInput(KeyDown(event.key.keyCode))
                    KeyEventType.KeyUp -> engine.dispatchInput(KeyUp(event.key.keyCode))
                    else -> Unit
                }
                false
            }
            .pointerInput(engine, suppressNextTap) {
                detectTapGestures(
                    onTap = { position ->
                        val scene = engine.currentScene
                        val worldPosition = if (scene == null) position else screenToWorld(
                            screen = position,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            cameraX = scene.camera.position.x,
                            cameraY = scene.camera.position.y,
                            zoom = scene.camera.zoom,
                        )
                        if (suppressNextTap) {
                            suppressNextTap = false
                            return@detectTapGestures
                        }
                        engine.dispatchInput(PointerDown(pointerId = 0L, position = worldPosition))
                        engine.dispatchInput(Tap(pointerId = 0L, position = worldPosition))
                        engine.dispatchInput(PointerUp(pointerId = 0L, position = worldPosition))
                    },
                )
            }
            .pointerInput(engine) {
                var lastPosition = Offset.Zero
                var lastWorldPosition = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        suppressNextTap = true
                        dragInProgress = true
                        lastPosition = start
                        val scene = engine.currentScene
                        val worldStart = if (scene == null) start else screenToWorld(
                            screen = start,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            cameraX = scene.camera.position.x,
                            cameraY = scene.camera.position.y,
                            zoom = scene.camera.zoom,
                        )
                        lastWorldPosition = worldStart
                        engine.dispatchInput(PointerDown(pointerId = 0L, position = worldStart))
                        engine.dispatchInput(DragStart(pointerId = 0L, position = worldStart))
                    },
                    onDrag = { change, dragAmount ->
                        if (multiTouchTransformActive) return@detectDragGestures
                        lastPosition = change.position
                        val scene = engine.currentScene
                        val worldPosition = if (scene == null) change.position else screenToWorld(
                            screen = change.position,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            cameraX = scene.camera.position.x,
                            cameraY = scene.camera.position.y,
                            zoom = scene.camera.zoom,
                        )
                        val worldDelta = if (scene == null) dragAmount else Offset(
                            x = dragAmount.x / scene.camera.zoom,
                            y = dragAmount.y / scene.camera.zoom,
                        )
                        lastWorldPosition = worldPosition
                        engine.dispatchInput(PointerMove(pointerId = 0L, position = worldPosition))
                        engine.dispatchInput(
                            Drag(
                                pointerId = 0L,
                                position = worldPosition,
                                dragDelta = worldDelta,
                            ),
                        )
                    },
                    onDragEnd = {
                        dragInProgress = false
                        engine.dispatchInput(DragEnd(pointerId = 0L, position = lastWorldPosition))
                        engine.dispatchInput(PointerUp(pointerId = 0L, position = lastWorldPosition))
                    },
                    onDragCancel = {
                        dragInProgress = false
                        engine.dispatchInput(DragEnd(pointerId = 0L, position = null))
                        suppressNextTap = false
                    },
                )
            }
            .pointerInput(engine) {
                awaitPointerEventScope {
                    var lastPinchDistance: Float? = null
                    var lastDispatchedMove: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val firstChange = event.changes.firstOrNull() ?: continue
                        val screenPosition = firstChange.position
                        val scene = engine.currentScene
                        val worldPosition = if (scene == null) screenPosition else screenToWorld(
                            screen = screenPosition,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            cameraX = scene.camera.position.x,
                            cameraY = scene.camera.position.y,
                            zoom = scene.camera.zoom,
                        )

                        val scrollDelta = event.changes.firstOrNull { it.scrollDelta != Offset.Zero }?.scrollDelta
                        if (scrollDelta != null) {
                            engine.dispatchInput(
                                com.kanvas.fx.core.Scroll(
                                    position = worldPosition,
                                    delta = scrollDelta,
                                ),
                            )
                        }

                        val pressed = event.changes.filter { it.pressed }
                        multiTouchTransformActive = pressed.size >= 2
                        if (pressed.size >= 2) {
                            val p1 = pressed[0].position
                            val p2 = pressed[1].position
                            val pinchDistance = (p1 - p2).getDistance()
                            val previousDistance = lastPinchDistance
                            if (previousDistance != null && previousDistance > 0f) {
                                val zoomRatio = pinchDistance / previousDistance
                                if (abs(zoomRatio - 1f) > 0.01f) {
                                    val pinchCentroid = Offset(
                                        x = (p1.x + p2.x) * 0.5f,
                                        y = (p1.y + p2.y) * 0.5f,
                                    )
                                    val pinchWorld = if (scene == null) pinchCentroid else screenToWorld(
                                        screen = pinchCentroid,
                                        viewportWidth = size.width.toFloat(),
                                        viewportHeight = size.height.toFloat(),
                                        cameraX = scene.camera.position.x,
                                        cameraY = scene.camera.position.y,
                                        zoom = scene.camera.zoom,
                                    )
                                    engine.dispatchInput(PinchZoom(position = pinchWorld, zoomFactor = zoomRatio))
                                }
                            }
                            lastPinchDistance = pinchDistance
                        } else {
                            lastPinchDistance = null
                        }

                        val shouldDispatchMove = !dragInProgress && !multiTouchTransformActive
                        if (shouldDispatchMove && worldPosition != lastDispatchedMove) {
                            engine.dispatchInput(PointerMove(pointerId = 0L, position = worldPosition))
                            lastDispatchedMove = worldPosition
                        }
                    }
                }
            },
    ) {
        frameInvalidationTick
        val scene = engine.currentScene ?: return@Canvas
        val renderer = Renderer2D(
            drawScope = this,
            assets = assets,
            camera = scene.camera,
        )
        clipRect {
            engine.renderCurrentScene(renderer)
        }
    }
}

private fun screenToWorld(
    screen: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
): Offset {
    val centerX = viewportWidth * 0.5f
    val centerY = viewportHeight * 0.5f
    return Offset(
        x = cameraX + (screen.x - centerX) / zoom,
        y = cameraY + (screen.y - centerY) / zoom,
    )
}
