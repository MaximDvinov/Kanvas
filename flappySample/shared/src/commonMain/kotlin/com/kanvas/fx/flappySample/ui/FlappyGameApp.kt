package com.kanvas.fx.flappySample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.flappySample.domain.FlappyPhase
import com.kanvas.fx.flappySample.engine.createFlappyRuntime
import kotlin.math.max

@Composable
fun FlappyGameApp() {
    var restartToken by remember { mutableIntStateOf(0) }
    val runtime = remember(restartToken) { createFlappyRuntime() }
    var state by remember { mutableStateOf(runtime.controller.state) }

    LaunchedEffect(runtime) {
        while (true) {
            kotlinx.coroutines.delay(16)
            state = runtime.controller.state
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { runtime.controller.requestFlap() },
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val worldWidth = runtime.config.worldWidth
        val worldHeight = runtime.config.worldHeight
        // Cover strategy: fill the full viewport without letterboxing.
        val targetZoom = max(viewportWidthPx / worldWidth, viewportHeightPx / worldHeight).coerceAtLeast(0.1f)

        LaunchedEffect(runtime, targetZoom) {
            runtime.engine.currentScene?.camera?.zoom = targetZoom
        }

        Box(modifier = Modifier.fillMaxSize()) {
            EngineCanvas(
                engine = runtime.engine,
                assets = runtime.assets,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Score: ${state.score}",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Best: ${state.bestScore}",
                color = Color(0xFFE4C36A),
                fontSize = 16.sp,
            )
        }

        if (state.phase != FlappyPhase.Running) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val title = when (state.phase) {
                        FlappyPhase.Ready -> "FLAPPY BIRD"
                        FlappyPhase.GameOver -> "GAME OVER"
                        FlappyPhase.Running -> ""
                    }
                    val line = when (state.phase) {
                        FlappyPhase.Ready -> "Tap to start"
                        FlappyPhase.GameOver -> "Tap to restart"
                        FlappyPhase.Running -> ""
                    }
                    Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = line, color = Color(0xFFE8EEF9), fontSize = 14.sp)
                    Text(
                        text = "Space / W / Up",
                        color = Color(0xFFB9C5D9),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
