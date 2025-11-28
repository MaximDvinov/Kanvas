package com.kanvas.fx.planetSample.planets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanvas.fx.compose.EngineCanvas

@Composable
fun GravityDemoApp() {
    var massSliderValue by remember { mutableFloatStateOf(massToSlider(40f)) }
    var fps by remember { mutableIntStateOf(0) }
    var selectedScenePreset by remember { mutableStateOf(ScenePreset.SolarSystem) }
    var asteroidPresetSize by remember { mutableIntStateOf(ASTEROID_COUNT) }
    var selectedSpawnPreset by remember { mutableStateOf(SpawnPreset.SinglePlanet) }
    var selectedToolMode by remember { mutableStateOf(ToolMode.Spawn) }
    var interactionMode by remember { mutableStateOf(InteractionMode.Add) }
    var trailsEnabled by remember { mutableStateOf(true) }
    var orbitPreviewEnabled by remember { mutableStateOf(true) }
    var launchVectorEnabled by remember { mutableStateOf(true) }
    var texturesEnabled by remember { mutableStateOf(defaultTexturesEnabled()) }
    var isPaused by remember { mutableStateOf(false) }
    var pausedStepBudget by remember { mutableIntStateOf(0) }
    var restartToken by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf(SandboxGameState()) }
    val planets = remember { mutableStateListOf<PlanetState>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var focusRequestKey by remember { mutableIntStateOf(0) }
    var activePanel by remember { mutableStateOf<GravityPanel?>(null) }
    var hoveredBodyId by remember { mutableStateOf<String?>(null) }
    var selectedBodyId by remember { mutableStateOf<String?>(null) }
    var hoveredTelemetry by remember { mutableStateOf<ObjectTelemetry?>(null) }
    var selectedTelemetry by remember { mutableStateOf<ObjectTelemetry?>(null) }
    val selectedMass = sliderToMass(massSliderValue)

    val engine = remember(selectedScenePreset, asteroidPresetSize, restartToken) {
        planets.clear()
        buildGravityEngine(
            planets = planets,
            interactionMode = { interactionMode },
            selectedMass = { sliderToMass(massSliderValue) },
            toolMode = { selectedToolMode },
            spawnPreset = { selectedSpawnPreset },
            trailsEnabled = { trailsEnabled },
            orbitPreviewEnabled = { orbitPreviewEnabled },
            launchVectorEnabled = { launchVectorEnabled },
            texturesEnabled = { texturesEnabled },
            isPaused = { isPaused && pausedStepBudget == 0 },
            isPauseRequested = { isPaused },
            selectedBodyId = { selectedBodyId },
            hoveredBodyId = { hoveredBodyId },
            asteroidPresetSize = { asteroidPresetSize },
            preset = selectedScenePreset,
            onDragState = { start, current ->
                dragStart = start
                dragCurrent = current
            },
            onGameState = { gameState = it },
            onHoverBody = { hoveredBodyId = it },
            onSelectBody = { selectedBodyId = it },
            onPausedStepConsumed = {
                if (pausedStepBudget > 0) pausedStepBudget--
            },
        )
    }

    LaunchedEffect(gameState.elapsedSeconds, hoveredBodyId, selectedBodyId, planets.size) {
        hoveredTelemetry =
            hoveredBodyId?.let { id -> planets.firstOrNull { it.id == id } }?.toTelemetry()
        selectedTelemetry =
            selectedBodyId?.let { id -> planets.firstOrNull { it.id == id } }?.toTelemetry()
    }
    val assets = remember { buildPlanetAssets() }
    val refocus = {
        focusRequestKey++
        Unit
    }

    LaunchedEffect(Unit) {
        var windowStart = withFrameNanos { it }
        var frames = 0
        while (true) {
            val now = withFrameNanos { it }
            frames++
            if (now - windowStart >= 1_000_000_000L) {
                fps = frames
                frames = 0
                windowStart = now
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050914))) {
        EngineCanvas(
            engine = engine,
            assets = assets,
            focusRequestKey = focusRequestKey,
            modifier = Modifier.fillMaxSize().background(Color(0xFF090E1F)),
        )

        Column(
            Modifier.padding(12.dp).systemBarsPadding().align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopHud(
                bodies = gameState.bodies,
                score = gameState.score,
                elapsedSeconds = gameState.elapsedSeconds,
                fps = fps,
                scene = selectedScenePreset,
                modifier = Modifier,
            )

            selectedTelemetry?.let {
                SelectedObjectCard(
                    telemetry = it,
                    modifier = Modifier,
                )
            }

            hoveredTelemetry?.let {
                ObjectTooltip(
                    telemetry = it,
                    modifier = Modifier,
                )
            }
        }


        QuickControls(
            interactionMode = interactionMode,
            onInteractionModeToggle = {
                interactionMode = if (interactionMode == InteractionMode.Add) {
                    InteractionMode.Camera
                } else {
                    InteractionMode.Add
                }
                refocus()
            },
            isPaused = isPaused,
            onPauseToggle = {
                isPaused = !isPaused
                refocus()
            },
            onStep = {
                if (isPaused) pausedStepBudget++
                refocus()
            },
            onReset = {
                restartToken++
                refocus()
            },
            onScene = {
                activePanel = GravityPanel.Scene
                refocus()
            },
            onRender = {
                activePanel = GravityPanel.Render
                refocus()
            },
            onSettings = {
                activePanel = GravityPanel.Settings
                refocus()
            },
            modifier = Modifier.systemBarsPadding().align(Alignment.TopEnd).padding(12.dp),
        )


        Row(
            Modifier.fillMaxWidth().systemBarsPadding().height(IntrinsicSize.Min)
                .align(Alignment.BottomStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpawnDeck(
                assets = assets,
                selectedSpawnPreset = selectedSpawnPreset,
                selectedMass = selectedMass,
                massSliderValue = massSliderValue,
                onPresetSelected = {
                    selectedSpawnPreset = it
                    if (it == SpawnPreset.Star && selectedMass < MIN_STAR_MASS) {
                        massSliderValue = massToSlider(MIN_STAR_MASS)
                    }
                    refocus()
                },
                onMassChanged = { massSliderValue = it },
                onMassChangeFinished = refocus,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            ToolRail(
                selectedToolMode = selectedToolMode,
                onToolSelected = {
                    if (interactionMode != InteractionMode.Camera) {
                        selectedToolMode = it
                        refocus()
                    }
                },
                modifier = Modifier.fillMaxHeight(),
            )

        }

    }

    when (activePanel) {
        GravityPanel.Scene -> SandboxDialog(
            title = "Scene",
            onClose = {
                activePanel = null
                refocus()
            },
        ) {
            ScenePreset.entries.forEach { preset ->
                PanelRow(
                    title = preset.title,
                    value = if (preset == selectedScenePreset) "Active" else "Load",
                    selected = preset == selectedScenePreset,
                    onClick = {
                        selectedScenePreset = preset
                        activePanel = null
                        refocus()
                    },
                )
            }
            if (selectedScenePreset == ScenePreset.AsteroidField) {
                HorizontalDivider(color = Color(0x33D8E9FF))
                PanelRow(
                    title = "Asteroids",
                    value = asteroidPresetSize.toString(),
                    selected = false,
                )
                val asteroidSizeOptions = listOf(400, 800, 1200, 2000, 3200)
                asteroidSizeOptions.forEach { count ->
                    PanelRow(
                        title = "${count} bodies",
                        value = if (count == asteroidPresetSize) "Active" else "Apply",
                        selected = count == asteroidPresetSize,
                        onClick = {
                            asteroidPresetSize = count
                            restartToken++
                            activePanel = null
                            refocus()
                        },
                    )
                }
            }
        }

        GravityPanel.Render -> SandboxDialog(
            title = "Render",
            onClose = {
                activePanel = null
                refocus()
            },
        ) {
            SettingSwitch("Trails", trailsEnabled) {
                trailsEnabled = it
                refocus()
            }
            SettingSwitch("Trajectory prediction", orbitPreviewEnabled) {
                orbitPreviewEnabled = it
                refocus()
            }
            SettingSwitch("Launch vector", launchVectorEnabled) {
                launchVectorEnabled = it
                refocus()
            }
            SettingSwitch("Textures", texturesEnabled) {
                texturesEnabled = it
                refocus()
            }
        }

        GravityPanel.Settings -> SandboxDialog(
            title = "Simulation",
            onClose = {
                activePanel = null
                refocus()
            },
        ) {
            PanelRow(
                "Mode",
                "${interactionMode.title} / ${selectedToolMode.title} / ${selectedSpawnPreset.title}",
                selected = false
            )
            PanelRow("Mass", selectedMass.toInt().toString(), selected = false)
            PanelRow("Scene", selectedScenePreset.title, selected = false)
            HorizontalDivider(color = Color(0x33D8E9FF))
            PanelRow(
                title = "Step once",
                value = if (isPaused) "Ready" else "Pause first",
                selected = isPaused,
                onClick = {
                    if (isPaused) pausedStepBudget++
                    refocus()
                },
            )
        }

        null -> Unit
    }

    if (dragStart != null && dragCurrent != null) {
        // Keep drag state observable for compose during active gesture.
    }
}

@Composable
private fun TopHud(
    bodies: Int,
    score: Int,
    elapsedSeconds: Float,
    fps: Int,
    scene: ScenePreset,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xBA0E1526),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudMetric("$bodies", "Bodies")
//            HudMetric("$score", "Score", accent = Color(0xFF7FE4FF))
            HudMetric("${formatOneDecimal(elapsedSeconds)}s", "Time")
            HudMetric(scene.title, "Scene", accent = Color(0xFFFFD27A))
//            HudMetric("$fps", "FPS")
        }
    }
}

@Composable
private fun ToolRail(
    selectedToolMode: ToolMode,
    onToolSelected: (ToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanelRow(modifier = modifier) {
        ToolMode.entries.forEach { mode ->
            val icon = when (mode) {
                ToolMode.Spawn -> "+"
                ToolMode.Impulse -> ">"
                ToolMode.Delete -> "x"
            }

            CompactChip(
                label = mode.title,
                selected = mode == selectedToolMode,
                onClick = { onToolSelected(mode) },
            )
        }
    }
}

@Composable
private fun QuickControls(
    interactionMode: InteractionMode,
    onInteractionModeToggle: () -> Unit,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onStep: () -> Unit,
    onReset: () -> Unit,
    onScene: () -> Unit,
    onRender: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanelColumn(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction(
                if (interactionMode == InteractionMode.Camera) "Camera" else "Add",
                selected = interactionMode == InteractionMode.Camera,
                onClick = onInteractionModeToggle,
            )
            MiniAction(
                if (isPaused) "Play" else "Pause", selected = isPaused, onClick = onPauseToggle
            )
            MiniAction("Step", selected = false, onClick = onStep)
            MiniAction("Reset", selected = false, onClick = onReset)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction("Scene", selected = false, onClick = onScene)
            MiniAction("Render", selected = false, onClick = onRender)
            MiniAction("More", selected = false, onClick = onSettings)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpawnDeck(
    assets: com.kanvas.fx.render.AssetRegistry,
    selectedSpawnPreset: SpawnPreset,
    selectedMass: Float,
    massSliderValue: Float,
    onPresetSelected: (SpawnPreset) -> Unit,
    onMassChanged: (Float) -> Unit,
    onMassChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xD00F182C),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
    ) {
        val previewKind = kindForBody(selectedMass)
        val display = displayMass(selectedMass)

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(100.dp)
                    .background(Color(0xFF19364A), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3C7FA0), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    visualClassLabel(previewKind, selectedMass),
                    color = Color(0xFFBFEFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                Slider(
                    value = massSliderValue,
                    onValueChange = onMassChanged,
                    valueRange = 0f..1f,
                    onValueChangeFinished = onMassChangeFinished,
                    thumb = {
                        ClassBadge(
                            modifier = Modifier.width(100.dp),
                            visualClassLabel(previewKind, selectedMass),
                            display
                        )
                    },
                    colors = SliderDefaults.colors(activeTrackColor = Color(0xB234539C)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GlassPanelColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xB20F182C),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun GlassPanelRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color(0xB20F182C),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SquareAction(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .background(if (selected) Color(0xFF1FA7C9) else Color(0xFF24314A), CircleShape)
                .border(1.dp, if (selected) Color(0xFFBFF5FF) else Color(0xFF40516D), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Text(label, color = Color(0xFFC8D8EA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.background(
            if (selected) Color(0xFF1FA7C9) else Color(0xFF223049), RoundedCornerShape(12.dp)
        ).border(
            1.dp,
            if (selected) Color(0xFFAEEFFF) else Color(0xFF3A4C6A),
            RoundedCornerShape(12.dp)
        ).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0xFFEAF6FF), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun CompactChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.background(
            if (selected) Color(0xFFE2A84D) else Color(0xFF24314A), RoundedCornerShape(14.dp)
        ).border(
            1.dp,
            if (selected) Color(0xFFFFE1A3) else Color(0xFF40516D),
            RoundedCornerShape(14.dp)
        ).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF10131C) else Color(0xFFEAF6FF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClassBadge(modifier: Modifier = Modifier, label: String, mass: String) {
    Column(
        modifier = modifier.background(Color(0xFF19364A), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF3C7FA0), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(mass, color = Color(0xFFBFEFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
//        Text(label, color = Color(0xC1BFEFFF), fontSize = 8.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun HudValue(text: String, color: Color = Color.White) {
    Text(text = text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

@Composable
private fun HudMetric(
    value: String,
    label: String,
    accent: Color = Color.White,
) {
    Column(
        modifier = Modifier.widthIn(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, color = Color(0xFF8FA3BE), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun ObjectTooltip(
    telemetry: ObjectTelemetry,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC13243C),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Hover: ${telemetry.kind}",
                color = Color(0xFFEAF6FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "m=${displayMass(telemetry.mass)}  r=${telemetry.radius.toInt()}",
                color = Color(0xFFB9CCE5),
                fontSize = 10.sp
            )
            Text("v=${telemetry.speed.toInt()}", color = Color(0xFF9DE2FF), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SelectedObjectCard(
    telemetry: ObjectTelemetry,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xE0142238),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Selected Object",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text("Type: ${telemetry.kind}", color = Color(0xFFC8D8EA), fontSize = 11.sp)
            Text(
                "Mass: ${displayMass(telemetry.mass)}", color = Color(0xFFC8D8EA), fontSize = 11.sp
            )
            Text("Size: ${telemetry.radius.toInt()}", color = Color(0xFFC8D8EA), fontSize = 11.sp)
            Text(
                "Speed: ${formatOneDecimal(telemetry.speed)}",
                color = Color(0xFF9DE2FF),
                fontSize = 11.sp
            )
        }
    }
}

private fun speedOf(planet: PlanetState): Float {
    val vx = planet.body.velocity.x
    val vy = planet.body.velocity.y
    return kotlin.math.sqrt(vx * vx + vy * vy)
}

private fun PlanetState.toTelemetry(): ObjectTelemetry = ObjectTelemetry(
    id = id,
    kind = kind,
    mass = body.mass,
    radius = radius,
    speed = speedOf(this),
)

@Composable
private fun PanelRow(
    title: String,
    value: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(
            if (selected) Color(0xFF1D6E83) else Color(0xFF1C2940), RoundedCornerShape(12.dp)
        ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color(0xFFEAF6FF), fontWeight = FontWeight.SemiBold)
        Text(value, color = if (selected) Color(0xFFBFF5FF) else Color(0xFF9FB0C8))
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1C2940), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0xFFEAF6FF), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SandboxDialog(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xF0142036),
        title = {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier.width(360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = Color(0xFF9DE2FF))
            }
        },
    )
}

private enum class GravityPanel {
    Scene, Render, Settings,
}
