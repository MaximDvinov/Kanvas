package com.kanvas.fx.core

/**
 * Ordered execution phases for scene systems.
 */
enum class SystemPhase {
    /** Systems executed before main physics step. */
    PrePhysics,
    /** Systems that perform physics simulation. */
    Physics,
    /** Systems executed after physics, typically gameplay logic. */
    PostPhysics,
    /** Systems for render preparation and visual bookkeeping. */
    RenderPrep,
}

/**
 * Per-frame simulation unit executed by [Scene].
 */
fun interface SceneSystem {
    /**
     * Executes one update step.
     */
    fun update(frame: FrameContext)
}

/**
 * Runtime system registration descriptor.
 */
data class SceneSystemSpec(
    /** Stable system identifier used for runtime toggling. */
    val id: String,
    /** System implementation. */
    val system: SceneSystem,
    /** Execution phase in the scene pipeline. */
    val phase: SystemPhase = SystemPhase.PostPhysics,
    /** Ordering value inside a phase. Lower runs first. */
    val order: Int = 0,
    /** Runtime enable flag. */
    var enabled: Boolean = true,
)
