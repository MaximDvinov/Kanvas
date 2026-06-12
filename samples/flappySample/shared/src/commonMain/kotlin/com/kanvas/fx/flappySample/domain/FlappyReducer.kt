package com.kanvas.fx.flappySample.domain

import kotlin.math.max
import kotlin.math.min

enum class FlappyAction {
    Flap,
    Restart,
}

data class PipeState(
    val id: Int,
    val x: Float,
    val gapCenterY: Float,
    val scored: Boolean = false,
)

data class FlappyState(
    val phase: FlappyPhase = FlappyPhase.Ready,
    val birdY: Float,
    val birdVelocityY: Float = 0f,
    val score: Int = 0,
    val bestScore: Int = 0,
    val timeFromLastPipe: Float = 0f,
    val pipes: List<PipeState> = emptyList(),
) {
    companion object {
        fun initial(config: FlappyConfig): FlappyState = FlappyState(birdY = config.birdStartY)
    }
}

data class FlappyStepInput(
    val deltaTimeSeconds: Float,
    val spawnPipeId: Int,
    val spawnPipeGapCenterY: Float,
)

data class FlappyStepResult(
    val state: FlappyState,
    val pipeSpawned: Boolean,
)

object FlappyReducer {
    fun reduceAction(
        state: FlappyState,
        action: FlappyAction,
        config: FlappyConfig,
    ): FlappyState {
        return when (action) {
            FlappyAction.Restart -> FlappyState.initial(config).copy(bestScore = state.bestScore)
            FlappyAction.Flap -> when (state.phase) {
                FlappyPhase.Ready -> state.copy(
                    phase = FlappyPhase.Running,
                    birdVelocityY = config.flapImpulse,
                )
                FlappyPhase.Running -> state.copy(birdVelocityY = config.flapImpulse)
                FlappyPhase.GameOver -> FlappyState.initial(config).copy(bestScore = state.bestScore)
            }
        }
    }

    fun step(
        state: FlappyState,
        config: FlappyConfig,
        input: FlappyStepInput,
    ): FlappyStepResult {
        val dt = input.deltaTimeSeconds.coerceAtLeast(0f)
        val next = state
        if (next.phase != FlappyPhase.Running) {
            return FlappyStepResult(state = next, pipeSpawned = false)
        }

        val vy = next.birdVelocityY + config.gravity * dt
        val birdY = next.birdY + vy * dt

        var phase = next.phase
        var score = next.score
        var bestScore = next.bestScore
        var spawn = false

        if (birdY <= 0f || birdY + config.birdHeight >= config.worldHeight - config.groundHeight) {
            phase = FlappyPhase.GameOver
        }

        var timer = next.timeFromLastPipe + dt
        val mutablePipes = next.pipes.toMutableList()
        if (timer >= config.pipeSpawnInterval) {
            timer = 0f
            spawn = true
            mutablePipes += PipeState(
                id = input.spawnPipeId,
                x = config.worldWidth + 40f,
                gapCenterY = input.spawnPipeGapCenterY,
            )
        }

        val move = config.scrollSpeed * dt
        val updatedPipes = ArrayList<PipeState>(mutablePipes.size)
        val birdLeft = config.birdStartX
        val birdRight = birdLeft + config.birdWidth
        val birdTop = birdY
        val birdBottom = birdY + config.birdHeight

        for (pipe in mutablePipes) {
            val x = pipe.x - move
            val topLeft = x
            val topRight = x + config.pipeWidth
            val topTop = pipe.gapCenterY - config.pipeGapHeight * 0.5f - config.pipeHeight
            val topBottom = pipe.gapCenterY - config.pipeGapHeight * 0.5f
            val bottomLeft = x
            val bottomRight = x + config.pipeWidth
            val bottomTop = pipe.gapCenterY + config.pipeGapHeight * 0.5f
            val bottomBottom = bottomTop + config.pipeHeight

            val hitTop = intersectsAabb(birdLeft, birdTop, birdRight, birdBottom, topLeft, topTop, topRight, topBottom)
            val hitBottom = intersectsAabb(birdLeft, birdTop, birdRight, birdBottom, bottomLeft, bottomTop, bottomRight, bottomBottom)
            if (hitTop || hitBottom) {
                phase = FlappyPhase.GameOver
            }

            var scored = pipe.scored
            if (!scored && x + config.pipeWidth < config.birdStartX) {
                scored = true
                score += 1
                bestScore = max(bestScore, score)
            }

            val offscreen = x + config.pipeWidth < -8f
            if (!offscreen) {
                updatedPipes += pipe.copy(x = x, scored = scored)
            }
        }

        return FlappyStepResult(
            state = next.copy(
                phase = phase,
                birdY = min(max(birdY, -config.birdHeight), config.worldHeight),
                birdVelocityY = vy,
                score = score,
                bestScore = bestScore,
                timeFromLastPipe = timer,
                pipes = updatedPipes,
            ),
            pipeSpawned = spawn,
        )
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
}
