package com.kanvas.fx.flappySample.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import com.kanvas.fx.core.AnimationClip
import com.kanvas.fx.core.AnimationPlayerComponent
import com.kanvas.fx.core.Drag
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.EngineInputEvent
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.KeyDown
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.SpriteAnimationSystem
import com.kanvas.fx.core.SpriteFrame
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.core.Tap
import com.kanvas.fx.core.TransformComponent
import com.kanvas.fx.core.animatedSprite
import com.kanvas.fx.core.playAnimation
import com.kanvas.fx.core.setAnimationClips
import com.kanvas.fx.dsl.engine
import com.kanvas.fx.flappySample.domain.FlappyConfig
import com.kanvas.fx.flappySample.domain.FlappyPhase
import com.kanvas.fx.flappySample.domain.FlappyUiState
import com.kanvas.fx.render.AssetRegistry
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private data class PipePair(
    val id: Int,
    var x: Float,
    val gapCenterY: Float,
    var scored: Boolean = false,
)

class FlappyGameController {
    var state: FlappyUiState = FlappyUiState()
        private set

    private var pendingFlap: Boolean = false
    private var pendingRestart: Boolean = false

    fun requestFlap() {
        pendingFlap = true
    }

    fun requestRestart() {
        pendingRestart = true
    }

    internal fun consumeFlap(): Boolean = pendingFlap.also { pendingFlap = false }

    internal fun consumeRestart(): Boolean = pendingRestart.also { pendingRestart = false }

    internal fun updateState(next: FlappyUiState) {
        state = next
    }
}

data class FlappyRuntime(
    val engine: Engine,
    val assets: AssetRegistry,
    val controller: FlappyGameController,
    val config: FlappyConfig,
)

private class FlappyWorldSystem(
    private val scene: Scene,
    private val controller: FlappyGameController,
    private val config: FlappyConfig,
) : SceneSystem {
    private val random = Random(42)
    private var phase: FlappyPhase = FlappyPhase.Ready
    private var birdY: Float = config.birdStartY
    private var birdVelocityY: Float = 0f
    private var score: Int = 0
    private var bestScore: Int = 0
    private var birdTiltDegrees: Float = -6f
    private var timeFromLastPipe: Float = 0f
    private var nextPipeId: Int = 1
    private val pipes = mutableListOf<PipePair>()

    private val background = scene.objects.create("background") {
        addComponent(
            RenderComponent(zIndex = -100) {
                texture(
                    textureId = "bg-day",
                    topLeft = Offset(0f, 0f),
                    size = Size(config.worldWidth, config.worldHeight - config.groundHeight),
                    preserveAspect = false,
                )
            },
        )
    }

    private val groundA = scene.objects.create("ground-a") {
        addComponent(TransformComponent(position = Offset(0f, 0f)))
        addComponent(
            RenderComponent(zIndex = 100) { entity ->
                val x = entity.requireComponent<TransformComponent>().position.x
                texture(
                    textureId = "ground",
                    topLeft = Offset(x, config.worldHeight - config.groundHeight),
                    size = Size(config.worldWidth, config.groundHeight),
                    preserveAspect = false,
                )
            },
        )
    }

    private val groundB = scene.objects.create("ground-b") {
        addComponent(TransformComponent(position = Offset(config.worldWidth, 0f)))
        addComponent(
            RenderComponent(zIndex = 100) { entity ->
                val x = entity.requireComponent<TransformComponent>().position.x
                texture(
                    textureId = "ground",
                    topLeft = Offset(x, config.worldHeight - config.groundHeight),
                    size = Size(config.worldWidth, config.groundHeight),
                    preserveAspect = false,
                )
            },
        )
    }

    private val bird = scene.objects.create("bird") {
        addComponent(TransformComponent(position = Offset(config.birdStartX, config.birdStartY)))
        setAnimationClips(
            listOf(
                AnimationClip(
                    id = "flap",
                    fps = 10f,
                    loop = true,
                    frames = listOf(
                        SpriteFrame("bird-up"),
                        SpriteFrame("bird-mid"),
                        SpriteFrame("bird-down"),
                    ),
                ),
            ),
        )
        addComponent(AnimationPlayerComponent(currentClipId = "flap"))
        playAnimation("flap")
        addComponent(
            RenderComponent(zIndex = 20) { entity ->
                val transform = entity.requireComponent<TransformComponent>()
                animatedSprite(
                    entity = entity,
                    topLeft = transform.position,
                    size = Size(config.birdWidth, config.birdHeight),
                    preserveAspect = true,
                    rotationDegrees = birdTiltDegrees,
                )
            },
        )
    }

    init {
        emitState()
    }

    private fun emitState() {
        controller.updateState(
            FlappyUiState(
                phase = phase,
                score = score,
                bestScore = bestScore,
            ),
        )
    }

    private fun resetGame() {
        phase = FlappyPhase.Ready
        score = 0
        birdY = config.birdStartY
        birdVelocityY = 0f
        birdTiltDegrees = -6f
        timeFromLastPipe = 0f
        pipes.toList().forEach { removePipe(it.id) }
        pipes.clear()
        bird.position = Offset(config.birdStartX, birdY)
        emitState()
    }

    private fun spawnPipePair() {
        val id = nextPipeId++
        val gapCenterY = random.nextFloat() * (config.maxPipeGapCenterY - config.minPipeGapCenterY) + config.minPipeGapCenterY
        val x = config.worldWidth + 40f

        scene.objects.create("pipe-top-$id") {
            addComponent(
                TransformComponent(
                    position = Offset(
                        x,
                        gapCenterY - config.pipeGapHeight * 0.5f - config.pipeHeight,
                    ),
                ),
            )
            addComponent(
                RenderComponent(zIndex = 10) { entity ->
                    val transform = entity.requireComponent<TransformComponent>()
                    texture(
                        textureId = "pipe-top",
                        topLeft = transform.position,
                        size = Size(config.pipeWidth, config.pipeHeight),
                        preserveAspect = false,
                    )
                },
            )
        }

        scene.objects.create("pipe-bottom-$id") {
            addComponent(
                TransformComponent(
                    position = Offset(x, gapCenterY + config.pipeGapHeight * 0.5f),
                ),
            )
            addComponent(
                RenderComponent(zIndex = 10) { entity ->
                    val transform = entity.requireComponent<TransformComponent>()
                    texture(
                        textureId = "pipe-bottom",
                        topLeft = transform.position,
                        size = Size(config.pipeWidth, config.pipeHeight),
                        preserveAspect = false,
                    )
                },
            )
        }

        pipes += PipePair(
            id = id,
            x = x,
            gapCenterY = gapCenterY,
        )
    }

    private fun removePipe(id: Int) {
        scene.objects.remove("pipe-top-$id")
        scene.objects.remove("pipe-bottom-$id")
    }

    private fun intersectsAabb(
        aLeft: Float,
        aTop: Float,
        aRight: Float,
        aBottom: Float,
        bLeft: Float,
        bTop: Float,
        bRight: Float,
        bBottom: Float,
    ): Boolean = !(aRight < bLeft || aLeft > bRight || aBottom < bTop || aTop > bBottom)

    fun onInput(event: EngineInputEvent) {
        when (event) {
            is Tap -> controller.requestFlap()
            is Drag -> controller.requestFlap()
            is KeyDown -> {
                when (event.keyCode) {
                    Key.Spacebar.keyCode,
                    Key.W.keyCode,
                    Key.DirectionUp.keyCode,
                    -> controller.requestFlap()

                    Key.R.keyCode -> controller.requestRestart()
                }
            }

            else -> Unit
        }
    }

    override fun update(frame: FrameContext) {
        if (controller.consumeRestart()) {
            resetGame()
        }

        val flap = controller.consumeFlap()
        if (phase == FlappyPhase.Ready && flap) {
            phase = FlappyPhase.Running
            birdVelocityY = config.flapImpulse
        } else if (phase == FlappyPhase.Running && flap) {
            birdVelocityY = config.flapImpulse
        } else if (phase == FlappyPhase.GameOver && flap) {
            resetGame()
            return
        }

        if (phase == FlappyPhase.Running) {
            birdVelocityY += config.gravity * frame.deltaTimeSeconds
            birdY += birdVelocityY * frame.deltaTimeSeconds

            val birdTop = birdY
            val birdBottom = birdY + config.birdHeight
            val birdLeft = config.birdStartX
            val birdRight = birdLeft + config.birdWidth

            if (birdTop <= 0f || birdBottom >= config.worldHeight - config.groundHeight) {
                phase = FlappyPhase.GameOver
            }

            timeFromLastPipe += frame.deltaTimeSeconds
            if (timeFromLastPipe >= config.pipeSpawnInterval) {
                spawnPipePair()
                timeFromLastPipe = 0f
            }

            val move = config.scrollSpeed * frame.deltaTimeSeconds
            pipes.forEach { pipe ->
                pipe.x -= move
                scene.objects.get("pipe-top-${pipe.id}")?.position =
                    Offset(pipe.x, pipe.gapCenterY - config.pipeGapHeight * 0.5f - config.pipeHeight)
                scene.objects.get("pipe-bottom-${pipe.id}")?.position =
                    Offset(pipe.x, pipe.gapCenterY + config.pipeGapHeight * 0.5f)

                val topLeft = pipe.x
                val topRight = pipe.x + config.pipeWidth
                val topTop = pipe.gapCenterY - config.pipeGapHeight * 0.5f - config.pipeHeight
                val topBottom = pipe.gapCenterY - config.pipeGapHeight * 0.5f

                val bottomLeft = pipe.x
                val bottomRight = pipe.x + config.pipeWidth
                val bottomTop = pipe.gapCenterY + config.pipeGapHeight * 0.5f
                val bottomBottom = bottomTop + config.pipeHeight

                val hitTop = intersectsAabb(birdLeft, birdTop, birdRight, birdBottom, topLeft, topTop, topRight, topBottom)
                val hitBottom = intersectsAabb(birdLeft, birdTop, birdRight, birdBottom, bottomLeft, bottomTop, bottomRight, bottomBottom)
                if (hitTop || hitBottom) {
                    phase = FlappyPhase.GameOver
                }

                if (!pipe.scored && pipe.x + config.pipeWidth < config.birdStartX) {
                    pipe.scored = true
                    score += 1
                    bestScore = max(bestScore, score)
                }
            }

            pipes.removeAll { pipe ->
                val off = pipe.x + config.pipeWidth < -8f
                if (off) removePipe(pipe.id)
                off
            }
        }

        val groundMove = if (phase == FlappyPhase.Running) config.scrollSpeed * frame.deltaTimeSeconds else 0f
        val gxA = groundA.position.x - groundMove
        val gxB = groundB.position.x - groundMove
        groundA.position = Offset(if (gxA <= -config.worldWidth) gxB + config.worldWidth else gxA, 0f)
        groundB.position = Offset(if (gxB <= -config.worldWidth) groundA.position.x + config.worldWidth else gxB, 0f)

        if (phase == FlappyPhase.Ready) {
            val bob = sin(frame.elapsedSeconds * 3f) * 6f
            bird.position = Offset(config.birdStartX, config.birdStartY + bob)
            birdTiltDegrees = -8f
        } else {
            bird.position = Offset(config.birdStartX, birdY)
            val targetTilt = (birdVelocityY * 0.12f).coerceIn(-35f, 25f)
            val lerp = (10f * frame.deltaTimeSeconds).coerceIn(0f, 1f)
            birdTiltDegrees += (targetTilt - birdTiltDegrees) * lerp
        }

        emitState()
    }
}

fun createFlappyRuntime(config: FlappyConfig = FlappyConfig()): FlappyRuntime {
    val controller = FlappyGameController()
    val sceneName = "flappy-main"

    val engine = engine {
        scene(sceneName) {
            camera {
                position(config.worldWidth * 0.5f, config.worldHeight * 0.5f)
                zoom(1f)
                zoomRange(0.1f, 10f)
            }
        }
    }

    val scene = engine.requireScene(sceneName)
    val worldSystem = FlappyWorldSystem(scene, controller, config)
    scene.onInput = { event -> worldSystem.onInput(event) }
    scene.addSystem(
        com.kanvas.fx.core.SceneSystemSpec(
            id = "flappy-world",
            phase = SystemPhase.PostPhysics,
            system = worldSystem,
        ),
    )
    scene.addSystem(
        com.kanvas.fx.core.SceneSystemSpec(
            id = "flappy-animation",
            phase = SystemPhase.RenderPrep,
            system = SpriteAnimationSystem(),
        ),
    )

    val assets = AssetRegistry().apply {
        registerTexture("bg-day", "/textures/flappy/background-day.png")
        registerTexture("ground", "/textures/flappy/base.png")
        registerTexture("pipe-bottom", "/textures/flappy/pipe-green.png")
        registerTexture("pipe-top", "/textures/flappy/pipe-green-top.png")
        registerTexture("bird-up", "/textures/flappy/bluebird-upflap.png")
        registerTexture("bird-mid", "/textures/flappy/bluebird-midflap.png")
        registerTexture("bird-down", "/textures/flappy/bluebird-downflap.png")
    }

    return FlappyRuntime(
        engine = engine,
        assets = assets,
        controller = controller,
        config = config,
    )
}
