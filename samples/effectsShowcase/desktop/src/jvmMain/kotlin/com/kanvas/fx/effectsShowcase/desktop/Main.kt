package com.kanvas.fx.effectsShowcase.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.core.AnimationClip
import com.kanvas.fx.core.AnimationLibraryComponent
import com.kanvas.fx.core.AnimationPlayerComponent
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.SpriteAnimationSystem
import com.kanvas.fx.core.SpriteFrame
import com.kanvas.fx.core.SystemPhase
import com.kanvas.fx.core.TransformComponent
import com.kanvas.fx.core.animatedSprite
import com.kanvas.fx.dsl.engine
import com.kanvas.fx.render.AssetRegistry
import kotlin.math.cos
import kotlin.math.sin

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kanvas Effects Showcase") {
        EffectsShowcaseApp()
    }
}

@Composable
private fun EffectsShowcaseApp() {
    val engine = remember { buildEffectsShowcaseEngine() }
    val assets = remember { buildEffectsShowcaseAssets() }
    Box(Modifier.fillMaxSize().background(Color(0xFF080A10))) {
        EngineCanvas(engine = engine, modifier = Modifier.fillMaxSize(), assets = assets)
    }
}

private fun buildEffectsShowcaseAssets() = AssetRegistry().apply {
    registerTexture("bird-up", "textures/flappy/bluebird-upflap.png")
    registerTexture("bird-mid", "textures/flappy/bluebird-midflap.png")
    registerTexture("bird-down", "textures/flappy/bluebird-downflap.png")
    registerTexture("pipe", "textures/flappy/pipe-green.png")
    registerTexture("star", "textures/stars/star.png")
    registerTexture("planet", "textures/planets/planet03.png")
}

private fun buildEffectsShowcaseEngine() = engine {
    scene("effects", setCurrent = true) {
        camera {
            position(0f, 0f)
            zoom(1f)
            desktopDefaults()
        }
        renderQuality {
            effectsEnabled(true)
            glowQuality(1.2f)
        }
        systems {
            add(
                id = "motion",
                phase = SystemPhase.RenderPrep,
                system = SceneSystem { frame ->
                    frame.scene.entitiesWith(TransformComponent::class).forEach { entity ->
                        if (!entity.id.startsWith("moving-")) return@forEach
                        val transform = entity.requireComponent<TransformComponent>()
                        val phaseOffset = entity.id.removePrefix("moving-").toFloatOrNull() ?: 0f
                        transform.position = Offset(
                            x = -430f + phaseOffset * 145f + sin(frame.elapsedSeconds * 1.4f + phaseOffset) * 26f,
                            y = 285f + cos(frame.elapsedSeconds * 1.1f + phaseOffset) * 18f,
                        )
                    }
                },
            )
            add(
                id = "sprite-animation",
                phase = SystemPhase.RenderPrep,
                system = SpriteAnimationSystem(),
            )
        }
        entities {
            entity("effects-grid") {
                alwaysVisible()
                render {
                    rect(
                        topLeft = Offset(-520f, -280f),
                        size = Size(220f, 120f),
                        color = Color(0xFF0F172A),
                        style = style {
                            linearGradient(
                                Color(0xFF38BDF8),
                                Color(0xFFA78BFA),
                                start = Offset(-520f, -280f),
                                end = Offset(-300f, -160f),
                            )
                            shadow(color = Color(0xFF38BDF8), alpha = 0.35f, radius = 18f, offset = Offset(10f, 12f))
                            outline(color = Color.White, alpha = 0.55f, width = 2f)
                        },
                    )
                    circle(
                        center = Offset(-160f, -220f),
                        radius = 72f,
                        color = Color.White,
                        style = style {
                            radialGradient(
                                listOf(Color.White, Color(0xFFFFD166), Color(0xFFFF3B6B)),
                                center = Offset(-160f, -220f),
                                radius = 88f,
                            )
                            glow(color = Color(0xFFFFD166), alpha = 0.55f, radius = 26f, passes = 7)
                            bloom(color = Color(0xFFFF3B6B), alpha = 0.28f, radius = 42f)
                        },
                    )
                    circle(
                        center = Offset(160f, -220f),
                        radius = 72f,
                        color = Color.White,
                        style = style {
                            sweepGradient(
                                listOf(Color(0xFF22D3EE), Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFF22D3EE)),
                                center = Offset(160f, -220f),
                            )
                            outline(color = Color(0xFFE2E8F0), width = 3f)
                            shadow(color = Color.Black, alpha = 0.5f, radius = 24f, offset = Offset(12f, 18f))
                        },
                    )
                    rect(
                        topLeft = Offset(320f, -280f),
                        size = Size(220f, 120f),
                        color = Color(0xFF2DD4BF),
                        style = style {
                            blur(18f)
                            opacity(0.7f)
                            outline(color = Color(0xFF99F6E4), width = 4f)
                        },
                    )
                    line(
                        start = Offset(-520f, 20f),
                        end = Offset(-300f, 120f),
                        color = Color(0xFF60A5FA),
                        strokeWidth = 8f,
                        style = style {
                            glow(color = Color(0xFF60A5FA), alpha = 0.65f, radius = 20f, passes = 6)
                            bloom(color = Color(0xFF818CF8), alpha = 0.32f, radius = 36f)
                        },
                    )
                    polygon(
                        points = listOf(
                            Offset(-170f, 0f),
                            Offset(-60f, 70f),
                            Offset(-95f, 190f),
                            Offset(-245f, 190f),
                            Offset(-280f, 70f),
                        ),
                        color = Color(0xFFF97316),
                        style = style {
                            shadow(color = Color.Black, alpha = 0.45f, radius = 18f, offset = Offset(14f, 16f))
                            innerShadow(color = Color.Black, alpha = 0.35f, width = 8f)
                            outline(color = Color(0xFFFFEDD5), width = 3f)
                        },
                    )
                    rect(
                        topLeft = Offset(40f, 20f),
                        size = Size(220f, 150f),
                        color = Color(0xFF64748B),
                        style = style {
                            colorFilter(Color(0xFFEF4444), alpha = 0.55f)
                            shadow(color = Color(0xFFEF4444), alpha = 0.28f, radius = 24f, offset = Offset(0f, 18f))
                            outline(color = Color(0xFFFCA5A5), width = 2f)
                        },
                    )
                    circle(
                        center = Offset(440f, 90f),
                        radius = 82f,
                        color = Color(0xFF8B5CF6),
                        style = style {
                            opacity(0.62f)
                            blur(28f)
                            bloom(color = Color(0xFFA78BFA), alpha = 0.42f, radius = 52f, passes = 8)
                        },
                    )
                }
            }
            entity("texture-combo") {
                alwaysVisible()
                render {
                    texture(
                        textureId = "planet",
                        topLeft = Offset(-555f, 205f),
                        size = Size(120f, 120f),
                        preserveAspect = true,
                        style = style {
                            shadow(color = Color.Black, alpha = 0.42f, radius = 18f, offset = Offset(10f, 14f))
                            bloom(color = Color(0xFF38BDF8), alpha = 0.3f, radius = 28f)
                            outline(color = Color(0xFFBAE6FD), alpha = 0.6f, width = 2f)
                        },
                    )
                    texture(
                        textureId = "star",
                        topLeft = Offset(435f, 215f),
                        size = Size(120f, 120f),
                        preserveAspect = true,
                        style = style {
                            glow(color = Color(0xFFFFF3B0), alpha = 0.52f, radius = 32f)
                            bloom(color = Color(0xFFFF7A18), alpha = 0.35f, radius = 48f)
                        },
                    )
                    texture(
                        textureId = "pipe",
                        topLeft = Offset(250f, 210f),
                        size = Size(82f, 150f),
                        preserveAspect = true,
                        style = style {
                            colorFilter(Color(0xFF22C55E), alpha = 0.28f)
                            shadow(color = Color(0xFF052E16), alpha = 0.5f, radius = 16f, offset = Offset(9f, 10f))
                            innerShadow(color = Color.Black, alpha = 0.22f, width = 4f)
                        },
                    )
                }
            }
            entity("animated-bird") {
                alwaysVisible()
                transform { position(-20f, 250f) }
                component(AnimationPlayerComponent(currentClipId = "flap", speed = 1.5f))
                component(
                    AnimationLibraryComponent(
                        linkedMapOf(
                            "flap" to AnimationClip(
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
                    ),
                )
                component(
                    RenderComponent(zIndex = 10) { entity ->
                        val t = entity.requireComponent<TransformComponent>()
                        animatedSprite(
                            entity = entity,
                            topLeft = t.position,
                            size = Size(84f, 60f),
                            preserveAspect = true,
                            rotationDegrees = -7f,
                            style = style {
                                shadow(color = Color.Black, alpha = 0.45f, radius = 14f, offset = Offset(8f, 10f))
                                glow(color = Color(0xFF60A5FA), alpha = 0.35f, radius = 20f)
                                outline(color = Color.White, alpha = 0.65f, width = 2f)
                            },
                        )
                    },
                )
            }
            repeat(6) { index ->
                entity("moving-$index") {
                    alwaysVisible()
                    transform { position(-430f + index * 145f, 285f) }
                    render(zIndex = 20) { entity ->
                        val p = entity.requireComponent<TransformComponent>().position
                        circle(
                            center = p,
                            radius = 22f + index * 2f,
                            color = listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF22C55E),
                                Color(0xFFFACC15),
                                Color(0xFFFB7185),
                                Color(0xFFA78BFA),
                                Color(0xFF2DD4BF),
                            )[index],
                            style = style {
                                blur(6f + index)
                                bloom(alpha = 0.18f, radius = 18f + index * 3f)
                                shadow(color = Color.Black, alpha = 0.28f, radius = 10f, offset = Offset(4f, 6f))
                            },
                        )
                    }
                }
            }
        }
    }
}
