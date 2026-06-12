package com.kanvas.fx.sample.planets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import com.kanvas.fx.core.Drag
import com.kanvas.fx.core.DragEnd
import com.kanvas.fx.core.DragStart
import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.KeyDown
import com.kanvas.fx.core.KeyUp
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.Scroll
import com.kanvas.fx.core.Tap
import com.kanvas.fx.dsl.engine
import com.kanvas.fx.gravity.GravityBody
import com.kanvas.fx.gravity.gravityBarnesHut
import com.kanvas.fx.render.EffectKind
import com.kanvas.fx.render.EffectPass
import com.kanvas.fx.render.EffectPhase
import com.kanvas.fx.render.EffectStack
import com.kanvas.fx.render.Light2D
import com.kanvas.fx.render.LightingConfig
import com.kanvas.fx.render.RenderMaterial
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

fun buildGravityEngine(
    planets: MutableList<PlanetState>,
    selectedMass: () -> Float,
    toolMode: () -> ToolMode,
    spawnPreset: () -> SpawnPreset,
    trailsEnabled: () -> Boolean,
    orbitPreviewEnabled: () -> Boolean,
    launchVectorEnabled: () -> Boolean,
    texturesEnabled: () -> Boolean,
    isPaused: () -> Boolean,
    isPauseRequested: () -> Boolean,
    selectedBodyId: () -> String?,
    hoveredBodyId: () -> String?,
    asteroidPresetSize: () -> Int,
    preset: ScenePreset,
    onDragState: (Offset?, Offset?) -> Unit,
    onGameState: (SandboxGameState) -> Unit,
    onHoverBody: (String?) -> Unit,
    onSelectBody: (String?) -> Unit,
    onPausedStepConsumed: () -> Unit,
): Engine {
    var activeDragStart: Offset? = null
    var activeDragCurrent: Offset? = null
    var runtimeScene: Scene? = null
    var impulseTarget: PlanetState? = null
    var score = 0
    var lastSelectedBodyId: String? = null
    var cachedSceneLights: List<Light2D> = emptyList()
    val cachedLightingById = HashMap<String, LightingState>()

    return engine {
        scene("space", setCurrent = true) {
            camera {
                zoom(CAMERA_DEFAULT_ZOOM)
                zoomRange(min = CAMERA_MIN_ZOOM, max = CAMERA_MAX_ZOOM)
                keyboardDefaults()
            }

            systems {
                gravityBarnesHut(
                    bodiesProvider = { if (isPaused()) emptyList() else planets.map { it.body } },
                    gravityConstant = SOLVER_GRAVITY_CONSTANT,
                    softening = PREVIEW_SOFTENING,
                    theta = 0.65f,
                )
                add(
                    SceneSystem { frameContext ->
                        val selectedId = selectedBodyId()
                        selectedId?.let {
                            planets.firstOrNull { it.id == selectedId }?.let { tracked ->
                                frameContext.scene.camera.position = tracked.body.position
                            }
                        }
                        if (selectedId != lastSelectedBodyId) {
                            lastSelectedBodyId?.let { previousId ->
                                planets.firstOrNull { it.id == previousId }?.let { previous ->
                                    previous.trailPoints.resetTo(previous.body.position)
                                    previous.selectedTrailPoints.clear()
                                }
                            }
                            selectedId?.let { nextId ->
                                planets.firstOrNull { it.id == nextId }?.let { next ->
                                    next.selectedTrailPoints.clear()
                                    next.selectedTrailPoints.addAll(next.trailPoints.snapshot())
                                    next.selectedTrailPoints.add(next.body.position)
                                }
                            }
                            lastSelectedBodyId = selectedId
                        }
                        val physicsEnabled = !isPaused()
                        if (physicsEnabled) {
                            val sampleStep = adaptiveTrailSampleStep(planets.size)
                            if (frameContext.frameIndex % sampleStep == 0L) {
                                val trailsAllowed =
                                    trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS
                                planets.forEach { planet ->
                                    if (trailsAllowed || planet.id == selectedId) {
                                        planet.trailPoints.add(planet.body.position)
                                    }
                                    if (planet.id == selectedId) {
                                        planet.selectedTrailPoints.add(planet.body.position)
                                    }
                                }
                            }
                            score += mergeCollidingPlanets(
                                planets = planets,
                                removeObject = { id -> frameContext.scene.removeEntity(id) },
                            )
                            removeFarBodies(
                                planets = planets,
                                removeObject = { id -> frameContext.scene.removeEntity(id) },
                                selectedBodyId = selectedBodyId,
                                hoveredBodyId = hoveredBodyId,
                                onSelectBody = onSelectBody,
                                onHoverBody = onHoverBody,
                            )
                            if (isPauseRequested()) {
                                onPausedStepConsumed()
                            }
                        }

                        val stars = planets
                            .asSequence()
                            .filter { it.kind == BodyKind.Star }
                            .toList()
                        cachedSceneLights = stars.map {
                            Light2D(
                                id = it.id,
                                x = it.body.position.x,
                                y = it.body.position.y,
                                radius = (900f + sqrt(it.body.mass) * 6f),
                                color = it.color,
                                intensity = 1f,
                            )
                        }
                        cachedLightingById.clear()
                        if (planets.size <= MAX_FULL_LIGHTING_BODIES) {
                            planets.forEach { body ->
                                cachedLightingById[body.id] = lightingForBody(body, planets)
                            }
                        } else {
                            planets.forEach { body ->
                                cachedLightingById[body.id] = if (body.kind == BodyKind.Star) {
                                    LightingState(1f, body.color)
                                } else {
                                    LightingState(0f, Color.White)
                                }
                            }
                        }

                        onGameState(
                            SandboxGameState(
                                score = score,
                                elapsedSeconds = frameContext.elapsedSeconds,
                                bodies = planets.size,
                                toolMode = toolMode(),
                                spawnPreset = spawnPreset(),
                            ),
                        )
                    },
                )
            }

            onStart { engine ->
                runtimeScene = engine.currentScene
                runtimeScene?.spawn(
                    Entity("drag-preview").apply {
                        addComponent(RenderComponent(zIndex = 200) {
                            val start = activeDragStart
                            val current = activeDragCurrent
                            if (start != null && current != null) {
                                if (orbitPreviewEnabled() && toolMode() == ToolMode.Spawn) {
                                    val previewVelocity = (current - start) * VELOCITY_SCALE
                                    val previewOrbit = predictOrbitPoints(
                                        start = start,
                                        initialVelocity = previewVelocity,
                                        planets = planets,
                                    )
                                    trail(
                                        points = previewOrbit,
                                        color = Color(0xFF6EE7FF).copy(alpha = 0.75f),
                                        strokeWidth = 1.6f,
                                        style = style { glow(alpha = 0.35f, widthMultiplier = 2f) },
                                    )
                                }
                                if (launchVectorEnabled() && toolMode() != ToolMode.Delete) {
                                    vector(
                                        start = start,
                                        end = current,
                                        color = Color(0xFF9A79FF),
                                        arrowSize = 14f,
                                        strokeWidth = 2f,
                                        style = style {
                                            linearGradient(
                                                Color(0xFF3BCBFF),
                                                Color(0xFF9A79FF),
                                                start = start,
                                                end = current,
                                            )
                                            glow(alpha = 0.45f, widthMultiplier = 2.4f)
                                        },
                                    )
                                }
                            }
                            if (orbitPreviewEnabled()) {
                                val selected =
                                    selectedBodyId()?.let { id -> planets.firstOrNull { it.id == id } }
                                if (selected != null) {
                                    val predicted = predictOrbitPoints(
                                        start = selected.body.position,
                                        initialVelocity = selected.body.velocity,
                                        planets = planets.filterNot { it.id == selected.id },
                                    )
                                    trail(
                                        points = predicted,
                                        color = Color(0x99BEE8FF),
                                        strokeWidth = 1.4f,
                                        style = style {
                                            glow(
                                                alpha = 0.2f,
                                                widthMultiplier = 1.5f
                                            )
                                        },
                                    )
                                }
                            }
                        })
                    }
                )

                runtimeScene?.spawn(Entity("World").apply {
                    addComponent(RenderComponent {
                        circle(
                            Offset.Zero,
                            radius = PLANET_DESPAWN_RADIUS,
                            color = Color.Transparent,
                            style { this.stroke(Color.Red, 10f) }
                        )
                    })
                })


                preset.bodies(asteroidPresetSize()).forEach { body ->
                    runtimeScene?.let { scene ->
                        planets += createPlanet(
                            scene = scene,
                            planets = planets,
                            planetId = body.planetId,
                            bodyId = body.bodyId,
                            position = body.position,
                            velocity = body.velocity,
                            mass = body.mass,
                            forcedTextureId = body.textureId,
                            hoveredBodyId = hoveredBodyId,
                            selectedBodyId = selectedBodyId,
                            trailVisible = { trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS },
                            texturesEnabled = texturesEnabled,
                            sceneLightsProvider = { cachedSceneLights },
                            lightingProvider = { p ->
                                cachedLightingById[p.id] ?: LightingState(
                                    0f,
                                    Color.White
                                )
                            },
                        )
                    }
                }
            }

            onInput { event ->
                when (event) {
                    is KeyDown -> {
                        when (event.keyCode) {
                            Key.Escape.keyCode -> onSelectBody(null)
                            Key.W.keyCode,
                            Key.A.keyCode,
                            Key.S.keyCode,
                            Key.D.keyCode,
                            Key.Q.keyCode,
                            Key.E.keyCode,
                                -> onSelectBody(null)
                        }
                    }

                    is KeyUp -> {
                        Unit
                    }

                    is DragStart -> {
                        onSelectBody(null)
                        activeDragStart = event.position
                        activeDragCurrent = event.position
                        impulseTarget = if (toolMode() == ToolMode.Impulse) findClosestBody(
                            event.position,
                            planets
                        ) else null
                        onDragState(activeDragStart, activeDragCurrent)
                    }

                    is Drag -> {
                        activeDragCurrent = event.position
                        onHoverBody(findClosestBody(event.position, planets)?.id)
                        onDragState(activeDragStart, activeDragCurrent)
                    }

                    is DragEnd -> {
                        val start = activeDragStart
                        val end = activeDragCurrent
                        val scene = runtimeScene
                        if (start != null && end != null && scene != null) {
                            when (toolMode()) {
                                ToolMode.Spawn -> spawnFromPreset(
                                    scene = scene,
                                    planets = planets,
                                    preset = spawnPreset(),
                                    position = start,
                                    dragVelocity = (end - start) * VELOCITY_SCALE,
                                    selectedMass = selectedMass,
                                    hoveredBodyId = hoveredBodyId,
                                    selectedBodyId = selectedBodyId,
                                    trailsEnabled = trailsEnabled,
                                    texturesEnabled = texturesEnabled,
                                    sceneLightsProvider = { cachedSceneLights },
                                    lightingProvider = { p ->
                                        cachedLightingById[p.id] ?: LightingState(0f, Color.White)
                                    },
                                )

                                ToolMode.Impulse -> {
                                    impulseTarget?.let { target ->
                                        val impulse = (end - start) * IMPULSE_DRAG_SCALE
                                        target.body.velocity = target.body.velocity + impulse
                                    }
                                }

                                ToolMode.Delete -> {
                                    findClosestBody(start, planets)?.let { target ->
                                        if (selectedBodyId() == target.id) onSelectBody(null)
                                        planets.remove(target)
                                        scene.removeEntity(target.id)
                                        score = (score - 20).coerceAtLeast(0)
                                    }
                                }
                            }
                        }
                        impulseTarget = null
                        activeDragStart = null
                        activeDragCurrent = null
                        onDragState(null, null)
                    }

                    is Tap -> {
                        val scene = runtimeScene ?: return@onInput
                        val tappedBody = findClosestBody(event.position, planets)
                        if (tappedBody != null && toolMode() != ToolMode.Delete) {
                            if (selectedBodyId() == tappedBody.id) {
                                onSelectBody(null)
                            } else {
                                onSelectBody(tappedBody.id)
                            }
                            onHoverBody(tappedBody.id)
                            return@onInput
                        }
                        if (toolMode() != ToolMode.Delete && toolMode() != ToolMode.Spawn) {
                            onSelectBody(null)
                        }
                        when (toolMode()) {
                            ToolMode.Spawn -> spawnFromPreset(
                                scene = scene,
                                planets = planets,
                                preset = spawnPreset(),
                                position = event.position,
                                dragVelocity = Offset.Zero,
                                selectedMass = selectedMass,
                                hoveredBodyId = hoveredBodyId,
                                selectedBodyId = selectedBodyId,
                                trailsEnabled = trailsEnabled,
                                texturesEnabled = texturesEnabled,
                                sceneLightsProvider = { cachedSceneLights },
                                lightingProvider = { p ->
                                    cachedLightingById[p.id] ?: LightingState(
                                        0f,
                                        Color.White
                                    )
                                },
                            )

                            ToolMode.Delete -> {
                                findClosestBody(event.position, planets)?.let { target ->
                                    if (selectedBodyId() == target.id) onSelectBody(null)
                                    planets.remove(target)
                                    scene.removeEntity(target.id)
                                    score = (score - 20).coerceAtLeast(0)
                                }
                            }

                            ToolMode.Impulse -> Unit
                        }
                    }

                    is com.kanvas.fx.core.PointerMove -> {
                        onHoverBody(findClosestBody(event.position, planets)?.id)
                    }

                    is Scroll -> {
                        onSelectBody(null)
                    }

                    else -> Unit
                }
            }
        }
    }
}

private fun removeFarBodies(
    planets: MutableList<PlanetState>,
    removeObject: (String) -> Unit,
    selectedBodyId: () -> String?,
    hoveredBodyId: () -> String?,
    onSelectBody: (String?) -> Unit,
    onHoverBody: (String?) -> Unit,
) {
    val maxRadiusSq = PLANET_DESPAWN_RADIUS * PLANET_DESPAWN_RADIUS
    val iterator = planets.iterator()
    while (iterator.hasNext()) {
        val planet = iterator.next()
        val x = planet.body.position.x
        val y = planet.body.position.y
        if (x * x + y * y <= maxRadiusSq) continue
        if (selectedBodyId() == planet.id) onSelectBody(null)
        if (hoveredBodyId() == planet.id) onHoverBody(null)
        iterator.remove()
        removeObject(planet.id)
    }
}

private fun spawnFromPreset(
    scene: Scene,
    planets: MutableList<PlanetState>,
    preset: SpawnPreset,
    position: Offset,
    dragVelocity: Offset,
    selectedMass: () -> Float,
    hoveredBodyId: () -> String?,
    selectedBodyId: () -> String?,
    trailsEnabled: () -> Boolean,
    texturesEnabled: () -> Boolean,
    sceneLightsProvider: () -> List<Light2D>,
    lightingProvider: (PlanetState) -> LightingState,
) {
    when (preset) {
        SpawnPreset.SinglePlanet -> {
            planets += createPlanet(
                scene = scene,
                planets = planets,
                planetId = "planet-${planets.size}-${Random.nextInt(1_000_000)}",
                bodyId = "planet-body-${planets.size}-${Random.nextInt(1_000_000)}",
                position = position,
                velocity = dragVelocity,
                mass = selectedMass(),
                hoveredBodyId = hoveredBodyId,
                selectedBodyId = selectedBodyId,
                trailVisible = { trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS },
                texturesEnabled = texturesEnabled,
                sceneLightsProvider = sceneLightsProvider,
                lightingProvider = lightingProvider,
            )
        }

        SpawnPreset.AsteroidCluster -> {
            val cfg = AsteroidClusterConfig()
            repeat(cfg.count) {
                val angle = Random.nextFloat() * 6.28318f
                val radius = Random.nextFloat() * cfg.radius
                val offset =
                    Offset(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius)
                val tangent = Offset(-offset.y, offset.x)
                val tangentLen =
                    sqrt(tangent.x * tangent.x + tangent.y * tangent.y).coerceAtLeast(0.001f)
                val radialFactor = (1f - radius / cfg.radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val tangentialSpeed = cfg.maxTangentialVelocity * (0.2f + 0.8f * radialFactor)
                // One-direction orbit for the whole asteroid preset (counter-clockwise).
                val tangentialVelocity = tangent / tangentLen * tangentialSpeed
                planets += createPlanet(
                    scene = scene,
                    planets = planets,
                    planetId = "asteroid-${planets.size}-${Random.nextInt(1_000_000)}",
                    bodyId = "asteroid-body-${planets.size}-${Random.nextInt(1_000_000)}",
                    position = position + offset,
                    velocity = dragVelocity + tangentialVelocity,
                    mass = Random.nextFloat() * (cfg.maxMass - cfg.minMass) + cfg.minMass,
                    hoveredBodyId = hoveredBodyId,
                    selectedBodyId = selectedBodyId,
                    trailVisible = { trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS },
                    texturesEnabled = texturesEnabled,
                    sceneLightsProvider = sceneLightsProvider,
                    lightingProvider = lightingProvider,
                )
            }
        }

        SpawnPreset.BinaryPair -> {
            planets += createPlanet(
                scene = scene,
                planets = planets,
                planetId = "binary-a-${planets.size}",
                bodyId = "binary-a-body-${planets.size}",
                position = position + Offset(-BINARY_PAIR_SEPARATION, 0f),
                velocity = dragVelocity + Offset(0f, -BINARY_PAIR_TANGENTIAL_SPEED),
                mass = selectedMass() * BINARY_PAIR_MASS_FACTOR,
                hoveredBodyId = hoveredBodyId,
                selectedBodyId = selectedBodyId,
                trailVisible = { trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS },
                texturesEnabled = texturesEnabled,
                sceneLightsProvider = sceneLightsProvider,
                lightingProvider = lightingProvider,
            )
            planets += createPlanet(
                scene = scene,
                planets = planets,
                planetId = "binary-b-${planets.size}",
                bodyId = "binary-b-body-${planets.size}",
                position = position + Offset(BINARY_PAIR_SEPARATION, 0f),
                velocity = dragVelocity + Offset(0f, BINARY_PAIR_TANGENTIAL_SPEED),
                mass = selectedMass() * BINARY_PAIR_MASS_FACTOR,
                hoveredBodyId = hoveredBodyId,
                selectedBodyId = selectedBodyId,
                trailVisible = { trailsEnabled() && planets.size <= MAX_PLANETS_WITH_TRAILS },
                texturesEnabled = texturesEnabled,
                sceneLightsProvider = sceneLightsProvider,
                lightingProvider = lightingProvider,
            )
        }

        SpawnPreset.Star -> {
            val cfg = StarConfig()
            planets += createPlanet(
                scene = scene,
                planets = planets,
                planetId = "star-${planets.size}-${Random.nextInt(1_000_000)}",
                bodyId = "star-body-${planets.size}-${Random.nextInt(1_000_000)}",
                position = position,
                velocity = dragVelocity * STAR_SPAWN_VELOCITY_FACTOR,
                mass = max(selectedMass(), cfg.mass),
                hoveredBodyId = hoveredBodyId,
                selectedBodyId = selectedBodyId,
                trailVisible = { false },
                texturesEnabled = texturesEnabled,
                sceneLightsProvider = sceneLightsProvider,
                lightingProvider = lightingProvider,
            )
        }

        SpawnPreset.BlackHoleSmall -> {
            val cfg = BlackHoleConfig()
            planets += createPlanet(
                scene = scene,
                planets = planets,
                planetId = "black-hole-${planets.size}",
                bodyId = "black-hole-body-${planets.size}",
                position = position,
                velocity = dragVelocity * BLACK_HOLE_SPAWN_VELOCITY_FACTOR,
                mass = cfg.mass,
                kindOverride = BodyKind.BlackHole,
                eventHorizonRadius = cfg.eventHorizonRadius,
                hoveredBodyId = hoveredBodyId,
                selectedBodyId = selectedBodyId,
                trailVisible = { false },
                texturesEnabled = texturesEnabled,
                sceneLightsProvider = sceneLightsProvider,
                lightingProvider = lightingProvider,
            )
        }
    }
}

private fun createPlanet(
    scene: Scene,
    planets: List<PlanetState>,
    planetId: String,
    bodyId: String,
    position: Offset,
    velocity: Offset,
    mass: Float,
    kindOverride: BodyKind? = null,
    eventHorizonRadius: Float = 0f,
    forcedTextureId: String? = null,
    hoveredBodyId: () -> String? = { null },
    selectedBodyId: () -> String? = { null },
    trailVisible: () -> Boolean,
    texturesEnabled: () -> Boolean,
    sceneLightsProvider: () -> List<Light2D> = { emptyList() },
    lightingProvider: (PlanetState) -> LightingState = { LightingState(0f, Color.White) },
): PlanetState {
    val kind = kindOverride ?: kindForBody(mass = mass, eventHorizonRadius = eventHorizonRadius)
    val effectiveEventHorizonRadius = if (kind == BodyKind.BlackHole) {
        if (eventHorizonRadius > 0f) eventHorizonRadius else eventHorizonRadiusForMass(mass)
    } else {
        0f
    }
    val visualProfile = visualProfileForBody(kind, mass)
    val texture = forcedTextureId ?: visualProfile.textureIds.random()
    val color = defaultColorForKind(kind)

    val planet = PlanetState(
        id = planetId,
        textureId = texture,
        body = GravityBody(
            id = bodyId,
            position = position,
            velocity = velocity,
            mass = mass,
        ),
        kind = kind,
        visualClass = visualProfile.visualClass,
        eventHorizonRadius = effectiveEventHorizonRadius,
        radius = visualRadiusForBody(kind, mass, effectiveEventHorizonRadius),
        color = color,
        trailPoints = TrailBuffer(TRAIL_MAX_POINTS).apply { add(position) },
        selectedTrailPoints = mutableListOf(position),
        isTrailVisible = trailVisible,
    )

    scene.spawn(
        Entity(planet.id).apply {
            addComponent(
                RenderComponent(
                    zIndex = if (kind == BodyKind.BlackHole) 50 else 10,
                    material = materialForBody(kind, texturesEnabled()),
                ) { entity ->
                    val currentKind = planet.kind
                    val isHovered = planet.id == hoveredBodyId()
                    val isSelected = planet.id == selectedBodyId()
                    entity.requireComponent<RenderComponent>().material =
                        materialForBody(currentKind, texturesEnabled())
                    setSceneLights(sceneLightsProvider())
                    val lighting = lightingProvider(planet)
                    if (planet.isTrailVisible() || isSelected) {
                        val points = if (isSelected) {
                            sampledTrailForRender(planet.selectedTrailPoints)
                        } else {
                            planet.trailPoints.snapshot()
                        }
                        if (points.size > 1) {
                            trail(
                                points = points,
                                color = planet.color,
                                strokeWidth = if (currentKind == BodyKind.Asteroid) 1.3f else 2f,
                                style = style { glow(alpha = 0.09f, widthMultiplier = 1.3f) },
                            )
                        }
                    }

                    if (isHovered || isSelected) {
                        val selection = DEFAULT_SELECTION_STYLE
                        val ringRadius =
                            planet.radius * if (isSelected) selection.selectedScale else selection.hoverScale
                        circle(
                            center = planet.body.position,
                            radius = ringRadius,
                            color = Color.Transparent,
                            style = style {
                                stroke(
                                    color = if (isSelected) Color(0xFFE7F6FF) else Color(0x99CFE8FF),
                                    width = if (isSelected) selection.selectedStrokeWidth else selection.hoverStrokeWidth,
                                )
                                glow(
                                    color = if (isSelected) Color(0xFF86DCFF) else Color(0xFF9DD8FF),
                                    alpha = if (isSelected) 0.22f else 0.12f,
                                    widthMultiplier = if (isSelected) 1.9f else 1.35f,
                                )
                            },
                        )
                        if (isSelected) {
                            circle(
                                center = planet.body.position,
                                radius = ringRadius * selection.outerSelectedScale,
                                color = Color.Transparent,
                                style = style {
                                    stroke(
                                        color = Color(0xAA6ED8FF),
                                        width = selection.outerSelectedStrokeWidth
                                    )
                                },
                            )
                        }
                    }

                    if (currentKind == BodyKind.BlackHole) {
                        if (texturesEnabled()) {
                            val diskRadius =
                                planet.eventHorizonRadius * 1.25f * textureVisualScale(planet.textureId)
                            texture(
                                textureId = planet.textureId,
                                topLeft = planet.body.position - Offset(diskRadius, diskRadius),
                                size = Size(diskRadius * 2f, diskRadius * 2f),
                                preserveAspect = true,
                            )
                        }
                        circle(
                            center = planet.body.position,
                            radius = planet.eventHorizonRadius * 1.8f,
                            color = Color(0xCC7E4BFF),
                            style = style { glow(alpha = 0f, widthMultiplier = 2.3f) },
                        )
                        circle(
                            center = planet.body.position,
                            radius = planet.eventHorizonRadius,
                            color = Color.Black,
                        )
                    } else if (currentKind == BodyKind.Star) {
                        if (texturesEnabled()) {
                            val coreRadius =
                                planet.radius * 0.82f * textureVisualScale(planet.textureId)
                            texture(
                                textureId = planet.textureId,
                                topLeft = planet.body.position - Offset(coreRadius, coreRadius),
                                size = Size(coreRadius * 2f, coreRadius * 2f),
                                preserveAspect = true,
                            )
                        } else {
                            circle(
                                center = planet.body.position,
                                radius = planet.radius,
                                color = planet.color,
                            )
                        }
                    } else if (texturesEnabled()) {
                        val visualRadius = planet.radius * textureVisualScale(planet.textureId)
                        texture(
                            textureId = planet.textureId,
                            topLeft = planet.body.position - Offset(visualRadius, visualRadius),
                            size = Size(visualRadius * 2f, visualRadius * 2f),
                            preserveAspect = true,
                            style = style { },
                        )
                        if (currentKind != BodyKind.Asteroid && lighting.intensity > 0.02f) {
                            circle(
                                center = planet.body.position,
                                radius = planet.radius * (1.04f + lighting.intensity * 0.1f),
                                color = lighting.color.copy(alpha = 0.12f + lighting.intensity * 0.24f),
                                style = style {
                                    glow(
                                        alpha = 0.12f + lighting.intensity * 0.26f,
                                        widthMultiplier = 1.2f + lighting.intensity * 1.5f,
                                    )
                                },
                            )
                        } else if (currentKind == BodyKind.Asteroid && lighting.intensity > 0.16f) {
                            circle(
                                center = planet.body.position,
                                radius = planet.radius * 1.05f,
                                color = lighting.color.copy(alpha = 0.08f),
                            )
                        }
                    } else {
                        circle(
                            center = planet.body.position,
                            radius = planet.radius,
                            color = planet.color,
                            style = style {
                                stroke(
                                    lighting.color.copy(alpha = 0.2f + lighting.intensity * 0.45f),
                                    width = 1.1f + lighting.intensity * 1.4f,
                                )
                            },
                        )
                    }
                })
        },
    )

    return planet
}

private fun findClosestBody(position: Offset, planets: List<PlanetState>): PlanetState? {
    var minDistance = Float.MAX_VALUE
    var closest: PlanetState? = null
    planets.forEach { planet ->
        val dx = planet.body.position.x - position.x
        val dy = planet.body.position.y - position.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < minDistance && distance < max(
                planet.radius * OBJECT_PICK_RADIUS_FACTOR,
                OBJECT_PICK_BASE_RADIUS
            )
        ) {
            minDistance = distance
            closest = planet
        }
    }
    return closest
}

private fun mergeCollidingPlanets(
    planets: MutableList<PlanetState>,
    removeObject: (String) -> Unit,
): Int {
    var scoreDelta = 0
    if (planets.size < 2) return scoreDelta

    var mergesLeft = MAX_MERGES_PER_TICK
    while (mergesLeft > 0 && planets.size > 1) {
        val grid = HashMap<Long, MutableList<Int>>(planets.size * 2)
        for (i in planets.indices) {
            val p = planets[i]
            val gx = (p.body.position.x / COLLISION_GRID_CELL_SIZE).toInt()
            val gy = (p.body.position.y / COLLISION_GRID_CELL_SIZE).toInt()
            val key = collisionCellKey(gx, gy)
            grid.getOrPut(key) { mutableListOf() }.add(i)
        }

        var mergedThisPass = false
        val consumed = BooleanArray(planets.size)
        val removals = ArrayList<Int>()

        outer@ for (i in planets.indices) {
            if (consumed[i]) continue
            val a = planets[i]
            val gx = (a.body.position.x / COLLISION_GRID_CELL_SIZE).toInt()
            val gy = (a.body.position.y / COLLISION_GRID_CELL_SIZE).toInt()
            for (ox in -1..1) {
                for (oy in -1..1) {
                    val candidates = grid[collisionCellKey(gx + ox, gy + oy)] ?: continue
                    for (j in candidates) {
                        if (j <= i || consumed[j]) continue
                        val b = planets[j]
                        val dx = b.body.position.x - a.body.position.x
                        val dy = b.body.position.y - a.body.position.y
                        val collisionDistance = a.radius + b.radius
                        if (dx * dx + dy * dy > collisionDistance * collisionDistance) continue

                        val totalMass = a.body.mass + b.body.mass
                        val mergedPosition =
                            (a.body.position * a.body.mass + b.body.position * b.body.mass) / totalMass
                        val mergedVelocity =
                            (a.body.velocity * a.body.mass + b.body.velocity * b.body.mass) / totalMass
                        val mergedKind = kindForBody(totalMass)
                        val mergedProfile = visualProfileForBody(mergedKind, totalMass)
                        val mergedRadius = mergedVisualRadius(
                            kind = mergedKind,
                            mass = totalMass,
                            radiusA = a.radius,
                            radiusB = b.radius,
                        )
                        val mergedColor = blendByMass(
                            colorA = a.color,
                            massA = a.body.mass,
                            colorB = b.color,
                            massB = b.body.mass,
                        )

                        a.body.mass = totalMass
                        a.body.position = mergedPosition
                        a.body.velocity = mergedVelocity
                        a.color = mergedColor
                        a.radius = mergedRadius
                        recalculatePlanetVisuals(
                            planet = a,
                            updateTexture = mergedProfile.visualClass != a.visualClass,
                        )
                        b.trailPoints.snapshot().forEach { a.trailPoints.add(it) }
                        a.selectedTrailPoints.addAll(b.selectedTrailPoints)

                        consumed[j] = true
                        removals += j
                        scoreDelta += COLLISION_SCORE_PER_MERGE
                        mergesLeft--
                        mergedThisPass = true
                        if (mergesLeft <= 0) break@outer
                    }
                }
            }
        }

        if (removals.isEmpty()) break
        removals.sortedDescending().forEach { index ->
            val removed = planets.removeAt(index)
            removeObject(removed.id)
        }
        if (!mergedThisPass) break
    }
    return scoreDelta
}

private fun collisionCellKey(x: Int, y: Int): Long =
    (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

private fun mergedVisualRadius(
    kind: BodyKind,
    mass: Float,
    radiusA: Float,
    radiusB: Float,
): Float {
    val massRadius = visualRadiusForBody(kind, mass)
    val areaRadius = sqrt(radiusA * radiusA + radiusB * radiusB)
    val maxRadius = when (kind) {
        BodyKind.Asteroid -> 14f
        BodyKind.Star -> 360f
        BodyKind.BlackHole -> 260f
        else -> 120f
    }
    return max(massRadius, areaRadius).coerceAtMost(maxRadius)
}

private fun recalculatePlanetVisuals(
    planet: PlanetState,
    kindOverride: BodyKind? = null,
    updateTexture: Boolean = true,
) {
    val previousKind = planet.kind
    val nextKind = kindOverride ?: kindForBody(
        mass = planet.body.mass,
        eventHorizonRadius = planet.eventHorizonRadius,
    )
    val profile = visualProfileForBody(nextKind, planet.body.mass)
    planet.eventHorizonRadius = if (nextKind == BodyKind.BlackHole) {
        eventHorizonRadiusForMass(planet.body.mass)
    } else {
        0f
    }

    planet.kind = nextKind
    planet.visualClass = profile.visualClass
    planet.radius = visualRadiusForBody(nextKind, planet.body.mass, planet.eventHorizonRadius)
    if (updateTexture) {
        planet.textureId = profile.textureIds.random()
    }

    planet.color = when (nextKind) {
        BodyKind.Planet -> if (previousKind == BodyKind.Planet) planet.color else defaultColorForKind(
            nextKind
        )

        else -> defaultColorForKind(nextKind)
    }
}

private fun defaultColorForKind(kind: BodyKind): Color = when (kind) {
    BodyKind.Planet -> Color.hsv(Random.nextFloat() * 360f, 0.7f, 0.95f)
    BodyKind.Asteroid -> Color(0xFF9AA3B7)
    BodyKind.Star -> Color(0xFFFFD27A)
    BodyKind.BlackHole -> Color(0xFF0A0A13)
}

private fun materialForBody(
    kind: BodyKind,
    texturesEnabled: Boolean,
): RenderMaterial {
    val emission = when (kind) {
        BodyKind.Star -> listOf(
            EffectPass(
                kind = EffectKind.Glow,
                phase = EffectPhase.AfterBase,
                paddingPx = 64f,
                intensity = 1.45f,
                color = Color(0xFFFFDDA0),
            ),
            EffectPass(
                kind = EffectKind.BloomLite,
                phase = EffectPhase.AfterBase,
                paddingPx = 86f,
                intensity = 1.05f,
                color = Color(0xFFFFE8B8),
            ),
        )

        BodyKind.BlackHole -> listOf(
            EffectPass(
                kind = EffectKind.BloomLite,
                phase = EffectPhase.AfterBase,
                paddingPx = 74f,
                intensity = 1.2f,
                color = Color(0xFF8E6BFF),
            ),
            EffectPass(
                kind = EffectKind.Glow,
                phase = EffectPhase.AfterBase,
                paddingPx = 48f,
                intensity = 0.85f,
                color = Color(0xFF6A43E9),
            ),
        )

        BodyKind.Planet -> if (texturesEnabled) {
            emptyList()
        } else {
            emptyList()
        }

        else -> emptyList()
    }
    return RenderMaterial(
        id = "body-$kind",
        effects = EffectStack(
            emissionPass = emission,
        ),
        lighting = LightingConfig(
            enabled = kind != BodyKind.Star && kind != BodyKind.BlackHole,
            ambientIntensity = 0.08f,
            maxLightsPerObject = 4,
        ),
    )
}
