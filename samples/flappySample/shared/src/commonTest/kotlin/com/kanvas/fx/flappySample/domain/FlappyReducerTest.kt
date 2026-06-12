package com.kanvas.fx.flappySample.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlappyReducerTest {
    private val config = FlappyConfig()

    @Test
    fun flapFromReadyStartsRunning() {
        val initial = FlappyReducer.reduceAction(FlappyState.initial(config), FlappyAction.Flap, config)
        val result = FlappyReducer.step(
            state = initial,
            config = config,
            input = FlappyStepInput(
                deltaTimeSeconds = 0.016f,
                spawnPipeId = 1,
                spawnPipeGapCenterY = 220f,
            ),
        )

        assertEquals(FlappyPhase.Running, result.state.phase)
        assertEquals(config.flapImpulse + config.gravity * 0.016f, result.state.birdVelocityY)
    }

    @Test
    fun restartResetsRuntimeStateAndKeepsBestScore() {
        val state = FlappyState.initial(config).copy(
            phase = FlappyPhase.GameOver,
            score = 8,
            bestScore = 12,
            birdY = 340f,
            pipes = listOf(PipeState(id = 1, x = 10f, gapCenterY = 200f)),
        )

        val restarted = FlappyReducer.reduceAction(state, FlappyAction.Restart, config)

        assertEquals(FlappyPhase.Ready, restarted.phase)
        assertEquals(0, restarted.score)
        assertEquals(12, restarted.bestScore)
        assertEquals(0, restarted.pipes.size)
    }

    @Test
    fun spawnPipeWhenTimerExceedsInterval() {
        val state = FlappyState.initial(config).copy(
            phase = FlappyPhase.Running,
            timeFromLastPipe = config.pipeSpawnInterval,
        )

        val result = FlappyReducer.step(
            state = state,
            config = config,
            input = FlappyStepInput(
                deltaTimeSeconds = 0.016f,
                spawnPipeId = 42,
                spawnPipeGapCenterY = 250f,
            ),
        )

        assertTrue(result.pipeSpawned)
        assertTrue(result.state.pipes.any { it.id == 42 })
    }
}
