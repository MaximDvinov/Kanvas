package com.kanvas.fx.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.Tap
import com.kanvas.fx.dsl.engine
import com.kanvas.fx.physics.Collider2D
import com.kanvas.fx.physics.PhysicsBody2D
import com.kanvas.fx.physics.PhysicsWorld2D
import com.kanvas.fx.physics.physics2d
import kotlin.math.abs
import kotlin.random.Random

private const val BOWL_HALF_WIDTH = 220f
private const val BOWL_HALF_HEIGHT = 340f
private const val WALL_THICKNESS = 24f
private const val FLOOR_THICKNESS = 26f
private const val SPAWN_Y = -BOWL_HALF_HEIGHT + 48f
private const val LOSE_LINE_Y = -BOWL_HALF_HEIGHT + 68f
private const val MERGE_COOLDOWN_SECONDS = 0.08f
private const val DROP_COOLDOWN_SECONDS = 0.35f
private const val LOSS_ARM_DELAY_SECONDS = 1.2f

private data class FruitSpec(
    val level: Int,
    val radius: Float,
    val color: Color,
    val scoreValue: Int,
)

private val FRUITS = listOf(
    FruitSpec(0, 16f, Color(0xFFFBE38B), 1),
    FruitSpec(1, 20f, Color(0xFFFFC971), 3),
    FruitSpec(2, 26f, Color(0xFFFFA65C), 9),
    FruitSpec(3, 32f, Color(0xFFFF7A59), 27),
    FruitSpec(4, 40f, Color(0xFFEF5D8C), 81),
    FruitSpec(5, 50f, Color(0xFFC856E3), 243),
    FruitSpec(6, 62f, Color(0xFF7C7CFF), 729),
)

private data class FruitState(
    val id: String,
    val body: PhysicsBody2D,
    var level: Int,
    var ageSeconds: Float = 0f,
)

fun collisionDemoMain() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kanvas Demo: Suika Game",
    ) {
        MaterialTheme {
            SuikaDemoApp()
        }
    }
}

object CollisionPhysicsLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        collisionDemoMain()
    }
}

@Composable
private fun SuikaDemoApp() {
    var score by remember { mutableIntStateOf(0) }
    var nextLevel by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var restartToken by remember { mutableIntStateOf(0) }

    val engine = remember(restartToken) {
        buildSuikaEngine(
            onScoreAdd = { score += it },
            onNextLevel = { nextLevel = it },
            onGameOver = { gameOver = true },
            isGameOver = { gameOver },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF12111A)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF221A33)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Suika Score: $score", color = Color.White)
            Text(
                "Next: L${nextLevel + 1}${if (gameOver) " | GAME OVER" else ""}",
                color = if (gameOver) Color(0xFFFF8E8E) else Color.White,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Tap inside bowl to drop fruit", color = Color(0xFFD9D0EC))
            Button(
                onClick = {
                    score = 0
                    nextLevel = 0
                    gameOver = false
                    restartToken++
                },
            ) {
                Text("Restart")
            }
        }
        EngineCanvas(
            engine = engine,
            modifier = Modifier.fillMaxSize().background(Color(0xFF171423)),
        )
    }
}

private fun buildSuikaEngine(
    onScoreAdd: (Int) -> Unit,
    onNextLevel: (Int) -> Unit,
    onGameOver: () -> Unit,
    isGameOver: () -> Boolean,
): Engine {
    val world = PhysicsWorld2D(
        gravity = Offset(0f, 780f),
        iterations = 7,
    )
    val fruits = linkedMapOf<String, FruitState>()
    val pendingMerges = linkedSetOf<Pair<String, String>>()
    var sceneRef: Scene? = null
    var mergeCooldown = 0f
    var dropCooldown = 0f
    var lossArmTimer = LOSS_ARM_DELAY_SECONDS
    var spawnX = 0f
    var currentNextLevel = 0

    fun weightedNextLevel(): Int {
        val roll = Random.nextFloat()
        return when {
            roll < 0.60f -> 0
            roll < 0.90f -> 1
            else -> 2
        }
    }
    currentNextLevel = weightedNextLevel()

    fun spawnFruit(level: Int, x: Float, y: Float, initialVelocity: Offset = Offset.Zero): FruitState {
        val spec = FRUITS[level]
        val id = "fruit-${level}-${Random.nextInt(1_000_000)}"
        val body = PhysicsBody2D(
            id = id,
            position = Offset(
                x = x.coerceIn(-BOWL_HALF_WIDTH + spec.radius + WALL_THICKNESS, BOWL_HALF_WIDTH - spec.radius - WALL_THICKNESS),
                y = y,
            ),
            velocity = initialVelocity,
            collider = Collider2D.Circle(spec.radius),
            mass = spec.radius * 0.7f,
            restitution = 0.05f,
            kind = "fruit",
        )
        val state = FruitState(id = id, body = body, level = level)
        fruits[id] = state
        world.addBody(body)
        sceneRef?.spawn(
            Entity("obj-$id").apply {
                addComponent(
                    RenderComponent(zIndex = 10 + level) {
                        val current = fruits[id]
                        if (current != null) {
                            val currentSpec = FRUITS[current.level]
                            circle(
                                center = current.body.position,
                                radius = currentSpec.radius,
                                color = currentSpec.color,
                                style = style {
                                    stroke(Color.White.copy(alpha = 0.28f), width = 1.5f)
                                    glow(alpha = 0.16f, widthMultiplier = 1.7f)
                                },
                            )
                        }
                    },
                )
            },
        )
        return state
    }

    fun removeFruit(id: String) {
        fruits.remove(id)
        world.removeBody(id)
        sceneRef?.removeEntity("obj-$id")
    }

    world.onCollisionEnter = { collision ->
        val a = fruits[collision.bodyA.id]
        val b = fruits[collision.bodyB.id]
        if (a != null && b != null && a.level == b.level) {
            val key = if (a.id < b.id) a.id to b.id else b.id to a.id
            pendingMerges += key
        }
    }

    world.addBody(
        PhysicsBody2D(
            id = "wall-left",
            position = Offset(-BOWL_HALF_WIDTH - WALL_THICKNESS * 0.5f, 0f),
            collider = Collider2D.Aabb(WALL_THICKNESS * 0.5f, BOWL_HALF_HEIGHT),
            isStatic = true,
        ),
    )
    world.addBody(
        PhysicsBody2D(
            id = "wall-right",
            position = Offset(BOWL_HALF_WIDTH + WALL_THICKNESS * 0.5f, 0f),
            collider = Collider2D.Aabb(WALL_THICKNESS * 0.5f, BOWL_HALF_HEIGHT),
            isStatic = true,
        ),
    )
    world.addBody(
        PhysicsBody2D(
            id = "floor",
            position = Offset(0f, BOWL_HALF_HEIGHT + FLOOR_THICKNESS * 0.5f),
            collider = Collider2D.Aabb(BOWL_HALF_WIDTH + WALL_THICKNESS, FLOOR_THICKNESS * 0.5f),
            isStatic = true,
        ),
    )

    return engine {
        scene("suika", setCurrent = true) {
            camera {
                zoom(1f)
                zoomRange(0.7f, 2.4f)
//                desktopDefaults()
            }

            systems {
                physics2d(world)
                add(
                    SceneSystem { frame ->
                        sceneRef = frame.scene
                        if (mergeCooldown > 0f) {
                            mergeCooldown = (mergeCooldown - frame.deltaTimeSeconds).coerceAtLeast(0f)
                        }
                        if (dropCooldown > 0f) {
                            dropCooldown = (dropCooldown - frame.deltaTimeSeconds).coerceAtLeast(0f)
                        }
                        if (lossArmTimer > 0f) {
                            lossArmTimer = (lossArmTimer - frame.deltaTimeSeconds).coerceAtLeast(0f)
                        }
                        fruits.values.forEach { it.ageSeconds += frame.deltaTimeSeconds }
                        if (!isGameOver() && mergeCooldown <= 0f && pendingMerges.isNotEmpty()) {
                            val (idA, idB) = pendingMerges.first()
                            pendingMerges.remove(idA to idB)
                            val a = fruits[idA]
                            val b = fruits[idB]
                            if (a != null && b != null && a.level == b.level) {
                                val next = (a.level + 1).coerceAtMost(FRUITS.lastIndex)
                                val pos = Offset(
                                    x = (a.body.position.x + b.body.position.x) * 0.5f,
                                    y = (a.body.position.y + b.body.position.y) * 0.5f,
                                )
                                val vel = Offset(
                                    x = (a.body.velocity.x + b.body.velocity.x) * 0.5f,
                                    y = (a.body.velocity.y + b.body.velocity.y) * 0.5f,
                                )
                                removeFruit(a.id)
                                removeFruit(b.id)
                                spawnFruit(next, pos.x, pos.y, vel)
                                onScoreAdd(FRUITS[next].scoreValue)
                                mergeCooldown = MERGE_COOLDOWN_SECONDS
                            }
                        }

                        if (!isGameOver()) {
                            val reachedTop = fruits.values.any { fruit ->
                                val radius = FRUITS[fruit.level].radius
                                val nearTop = fruit.body.position.y - radius < LOSE_LINE_Y
                                val lowVerticalSpeed = abs(fruit.body.velocity.y) < 90f
                                nearTop && lowVerticalSpeed && fruit.ageSeconds > 0.55f
                            }
                            if (lossArmTimer <= 0f && reachedTop) {
                                onGameOver()
                            }
                        }
                    },
                )
            }

            onStart { engine ->
                sceneRef = engine.currentScene
                sceneRef?.spawn(
                    Entity("bowl").apply {
                        addComponent(
                            RenderComponent(zIndex = 1) {
                                rect(
                                    topLeft = Offset(-BOWL_HALF_WIDTH - WALL_THICKNESS, -BOWL_HALF_HEIGHT),
                                    size = androidx.compose.ui.geometry.Size(WALL_THICKNESS, BOWL_HALF_HEIGHT * 2f + FLOOR_THICKNESS),
                                    color = Color(0xFF4D4167),
                                )
                                rect(
                                    topLeft = Offset(BOWL_HALF_WIDTH, -BOWL_HALF_HEIGHT),
                                    size = androidx.compose.ui.geometry.Size(WALL_THICKNESS, BOWL_HALF_HEIGHT * 2f + FLOOR_THICKNESS),
                                    color = Color(0xFF4D4167),
                                )
                                rect(
                                    topLeft = Offset(-BOWL_HALF_WIDTH - WALL_THICKNESS, BOWL_HALF_HEIGHT),
                                    size = androidx.compose.ui.geometry.Size(BOWL_HALF_WIDTH * 2f + WALL_THICKNESS * 2f, FLOOR_THICKNESS),
                                    color = Color(0xFF4D4167),
                                )
                                line(
                                    start = Offset(-BOWL_HALF_WIDTH, LOSE_LINE_Y),
                                    end = Offset(BOWL_HALF_WIDTH, LOSE_LINE_Y),
                                    color = Color(0x66FF5A7A),
                                    strokeWidth = 2f,
                                    style = style {
                                        glow(alpha = 0.25f, widthMultiplier = 1.8f)
                                    },
                                )
                                line(
                                    start = Offset(spawnX, -BOWL_HALF_HEIGHT + 10f),
                                    end = Offset(spawnX, BOWL_HALF_HEIGHT - 16f),
                                    color = Color(0x88D4D9FF),
                                    strokeWidth = 1.5f,
                                    style = style {
                                        glow(alpha = 0.22f, widthMultiplier = 1.8f)
                                    },
                                )
                            },
                        )
                    },
                )
                onNextLevel(currentNextLevel)
            }

            onInput { event ->
                if (isGameOver()) return@onInput
                if (event !is Tap) return@onInput
                val clampedX = event.position.x.coerceIn(
                    -BOWL_HALF_WIDTH + 24f,
                    BOWL_HALF_WIDTH - 24f,
                )
                spawnX = clampedX
                if (dropCooldown > 0f) return@onInput
                val dropLevel = currentNextLevel
                spawnFruit(
                    level = dropLevel,
                    x = clampedX,
                    y = SPAWN_Y,
                    initialVelocity = Offset.Zero,
                )
                dropCooldown = DROP_COOLDOWN_SECONDS
                currentNextLevel = weightedNextLevel()
                onNextLevel(currentNextLevel)
            }
        }
    }
}
