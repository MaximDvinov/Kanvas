package com.kanvas.fx.flappySample.domain

enum class FlappyPhase {
    Ready,
    Running,
    GameOver,
}

data class FlappyUiState(
    val phase: FlappyPhase = FlappyPhase.Ready,
    val score: Int = 0,
    val bestScore: Int = 0,
)

data class FlappyConfig(
    val worldWidth: Float = 288f,
    val worldHeight: Float = 512f,
    val gravity: Float = 980f,
    val flapImpulse: Float = -310f,
    val scrollSpeed: Float = 110f,
    val pipeSpawnInterval: Float = 1.55f,
    val pipeGapHeight: Float = 132f,
    val pipeWidth: Float = 52f,
    val pipeHeight: Float = 320f,
    val birdWidth: Float = 34f,
    val birdHeight: Float = 24f,
    val birdStartX: Float = 72f,
    val birdStartY: Float = 220f,
    val groundHeight: Float = 112f,
    val minPipeGapCenterY: Float = 140f,
    val maxPipeGapCenterY: Float = 350f,
)
