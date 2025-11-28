package com.kanvas.fx.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.core.Engine
import com.kanvas.fx.dsl.engine
import com.kanvas.fx.physics.PhysicsWorld2D
import com.kanvas.fx.physics.physics2d
import com.kanvas.fx.render.AssetRegistry
import com.kanvas.fx.worldkit.ClassicObjects2D
import com.kanvas.fx.core.RenderComponent

fun platformerDemoMain() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kanvas Demo: Platformer",
    ) {
        MaterialTheme {
            PlatformerDemoApp()
        }
    }
}

object PlatformerDemoLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        platformerDemoMain()
    }
}

@Composable
private fun PlatformerDemoApp() {
    var playerText by remember { mutableStateOf("Player: (0, 0)") }
    val engine = remember { buildPlatformerDemoEngine { playerText = it } }
    val assets = remember { buildPlatformerAssets() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0E1220)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A223A)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Controls: A/D move, Space jump, W/A/S/D + Q/E camera", color = Color.White)
        }
        Text(playerText, color = Color(0xFFC7D2F2))
        EngineCanvas(
            engine = engine,
            assets = assets,
            modifier = Modifier.fillMaxSize().background(Color(0xFF131A2A)),
        )
    }
}

private fun buildPlatformerDemoEngine(
    onPlayerPosition: (String) -> Unit,
): Engine {
    val world = PhysicsWorld2D(gravity = Offset(0f, 900f))
    var playerRef: com.kanvas.fx.worldkit.PlayerCharacter2D? = null
    return engine {
        scene("platformer", setCurrent = true) {
            camera {
                zoom(1f)
                zoomRange(0.4f, 3.5f)
                desktopDefaults()
            }

            systems {
                physics2d(world)
            }

            onStart { engine ->
                val scene = engine.currentScene ?: return@onStart
                val objects = ClassicObjects2D(scene, world)
                scene.spawn(
                    com.kanvas.fx.core.Entity("bg").apply {
                        addComponent(
                            RenderComponent(zIndex = 0) {
                                texture(
                                    textureId = "bg",
                                    topLeft = Offset(-1200f, -900f),
                                    size = androidx.compose.ui.geometry.Size(2400f, 1800f),
                                )
                            },
                        )
                    },
                )
                val ground = objects.floor("ground", center = Offset(0f, 300f), width = 1200f, thickness = 40f)
                ground.entity.requireComponent<RenderComponent>().renderer = {
                    texture("ground-tile", Offset(-600f, 280f), androidx.compose.ui.geometry.Size(1200f, 40f))
                }
                val leftWall = objects.wall("left-wall", center = Offset(-560f, 0f), height = 800f)
                leftWall.entity.requireComponent<RenderComponent>().renderer = {
                    texture("wall-tile", Offset(-576f, -400f), androidx.compose.ui.geometry.Size(32f, 800f))
                }
                val rightWall = objects.wall("right-wall", center = Offset(560f, 0f), height = 800f)
                rightWall.entity.requireComponent<RenderComponent>().renderer = {
                    texture("wall-tile", Offset(544f, -400f), androidx.compose.ui.geometry.Size(32f, 800f))
                }
                val p1 = objects.platform("p1", center = Offset(-200f, 170f), width = 240f)
                p1.entity.requireComponent<RenderComponent>().renderer = {
                    texture("platform-tile", Offset(-320f, 161f), androidx.compose.ui.geometry.Size(240f, 18f))
                }
                val p2 = objects.platform("p2", center = Offset(140f, 90f), width = 200f)
                p2.entity.requireComponent<RenderComponent>().renderer = {
                    texture("platform-tile", Offset(40f, 81f), androidx.compose.ui.geometry.Size(200f, 18f))
                }
                val p3 = objects.platform("p3", center = Offset(310f, 10f), width = 140f)
                p3.entity.requireComponent<RenderComponent>().renderer = {
                    texture("platform-tile", Offset(240f, 1f), androidx.compose.ui.geometry.Size(140f, 18f))
                }
                val c1 = objects.crate("crate-1", center = Offset(40f, 220f))
                c1.entity.requireComponent<RenderComponent>().renderer = {
                    texture("crate", c1.body.position - Offset(14f, 14f), androidx.compose.ui.geometry.Size(28f, 28f))
                }
                val c2 = objects.crate("crate-2", center = Offset(90f, 220f))
                c2.entity.requireComponent<RenderComponent>().renderer = {
                    texture("crate", c2.body.position - Offset(14f, 14f), androidx.compose.ui.geometry.Size(28f, 28f))
                }
                val c3 = objects.crate("crate-3", center = Offset(140f, 220f))
                c3.entity.requireComponent<RenderComponent>().renderer = {
                    texture("crate", c3.body.position - Offset(14f, 14f), androidx.compose.ui.geometry.Size(28f, 28f))
                }
                playerRef = objects.player("hero", center = Offset(-420f, 220f))
                playerRef?.entity?.requireComponent<RenderComponent>()?.renderer = {
                    val body = playerRef?.body
                    if (body != null) {
                        texture("player", body.position - Offset(15f, 22f), androidx.compose.ui.geometry.Size(30f, 44f))
                    }
                }
            }

            onUpdate {
                val player = playerRef ?: return@onUpdate
                val p = player.body.position
                onPlayerPosition("Player: (${p.x.toInt()}, ${p.y.toInt()})")
                it.scene.camera.position = Offset(p.x, p.y - 80f)
            }
        }
    }
}

private fun buildPlatformerAssets(): AssetRegistry = AssetRegistry().apply {
    enableDesktopTextureAutoResolve()
    registerTexture("bg", "/textures/platformer/backgrounds/set1_background.png")
    registerTexture("ground-tile", "/textures/platformer/tiles/tileGreen_17.png")
    registerTexture("wall-tile", "/textures/platformer/tiles/tileGreen_05.png")
    registerTexture("platform-tile", "/textures/platformer/tiles/tileGreen_17.png")
    registerTexture("crate", "/textures/platformer/objects/blockBrown.png")
    registerTexture("player", "/textures/platformer/players/playerBlue_stand.png")
}
