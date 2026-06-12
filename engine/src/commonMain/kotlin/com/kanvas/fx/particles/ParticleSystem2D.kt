package com.kanvas.fx.particles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.render.RenderStyle
import com.kanvas.fx.render.Renderer2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Stable and allocation-friendly 2D particle system with fixed-capacity storage.
 *
 * The system stores particle data in primitive arrays and recycles slots when capacity is
 * reached. Register it as a scene system to update particles, then call [render] from an
 * entity render callback or a custom render pass.
 *
 * ```kotlin
 * val particles = ParticleSystem2D(capacity = 4096)
 * particles.registerEmitter(
 *     "engine",
 *     ParticleEmitter2D(origin = { Offset(120f, 200f) }),
 * )
 * scene.addSystem(SceneSystemSpec("particles", particles))
 * ```
 */
class ParticleSystem2D(
    capacity: Int = 2_048,
    private val maxDeltaTimeSeconds: Float = 1f / 20f,
) : SceneSystem {
    private val maxParticles = capacity.coerceAtLeast(1)
    private val emitters = linkedMapOf<String, ParticleEmitter2D>()

    private val x = FloatArray(maxParticles)
    private val y = FloatArray(maxParticles)
    private val vx = FloatArray(maxParticles)
    private val vy = FloatArray(maxParticles)
    private val age = FloatArray(maxParticles)
    private val lifetime = FloatArray(maxParticles)
    private val size = FloatArray(maxParticles)
    private val startAlpha = FloatArray(maxParticles)
    private val endAlpha = FloatArray(maxParticles)
    private val r = FloatArray(maxParticles)
    private val g = FloatArray(maxParticles)
    private val b = FloatArray(maxParticles)

    private var aliveCount = 0
    private var recycleCursor = 0
    private var seed = 0x6D2B79F5

    /** Number of particles currently alive. */
    val activeParticles: Int get() = aliveCount
    /** Maximum particles stored before older slots are recycled. */
    val capacityLimit: Int get() = maxParticles

    /**
     * Registers or replaces a continuous emitter by id.
     */
    fun registerEmitter(id: String, emitter: ParticleEmitter2D) {
        require(id.isNotBlank()) { "Emitter id must not be blank." }
        emitters[id] = emitter
    }

    /**
     * Removes an emitter.
     *
     * @return true when an emitter with [id] existed.
     */
    fun removeEmitter(id: String): Boolean = emitters.remove(id) != null

    /**
     * Removes all continuous emitters without clearing already alive particles.
     */
    fun clearEmitters() {
        emitters.clear()
    }

    /**
     * Removes all alive particles while keeping registered emitters.
     */
    fun clearParticles() {
        aliveCount = 0
        recycleCursor = 0
    }

    override fun update(frame: FrameContext) {
        val dt = frame.deltaTimeSeconds.coerceIn(0f, maxDeltaTimeSeconds)
        if (dt <= 0f) return
        updateAlive(dt)
        emitters.values.forEach { emitFrom(it, dt) }
    }

    /**
     * Renders all alive particles as circles.
     *
     * Particles are rendered in storage order. Use separate systems when strict layering
     * between particle groups is required.
     */
    fun render(
        renderer: Renderer2D,
        style: RenderStyle = RenderStyle(),
    ) {
        var i = 0
        while (i < aliveCount) {
            val progress = (age[i] / lifetime[i]).coerceIn(0f, 1f)
            val alpha = startAlpha[i] + (endAlpha[i] - startAlpha[i]) * progress
            renderer.circle(
                center = Offset(x[i], y[i]),
                radius = size[i],
                color = Color(r[i], g[i], b[i], alpha.coerceIn(0f, 1f)),
                style = style,
            )
            i++
        }
    }

    /**
     * Emits one-shot burst immediately, independent from continuous emitters.
     */
    fun emitBurst(
        origin: Offset,
        count: Int,
        color: Color,
        minSpeed: Float = 40f,
        maxSpeed: Float = 180f,
        minLifetimeSeconds: Float = 0.25f,
        maxLifetimeSeconds: Float = 0.9f,
        minSize: Float = 1.2f,
        maxSize: Float = 3.6f,
        startAlpha: Float = 0.95f,
        endAlpha: Float = 0f,
    ) {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return
        var i = 0
        while (i < safeCount) {
            val index = if (aliveCount < maxParticles) {
                aliveCount++
                aliveCount - 1
            } else {
                val idx = recycleCursor
                recycleCursor = (recycleCursor + 1) % maxParticles
                idx
            }
            val angleRad = nextFloat() * PI.toFloat() * 2f
            val speedValue = lerp(minSpeed, maxSpeed, nextFloat())
            x[index] = origin.x
            y[index] = origin.y
            vx[index] = cos(angleRad) * speedValue
            vy[index] = sin(angleRad) * speedValue
            age[index] = 0f
            lifetime[index] = lerp(minLifetimeSeconds, maxLifetimeSeconds, nextFloat()).coerceAtLeast(0.0001f)
            size[index] = lerp(minSize, maxSize, nextFloat()).coerceAtLeast(0.05f)
            this.startAlpha[index] = startAlpha.coerceIn(0f, 1f)
            this.endAlpha[index] = endAlpha.coerceIn(0f, 1f)
            r[index] = color.red
            g[index] = color.green
            b[index] = color.blue
            i++
        }
    }

    private fun updateAlive(dt: Float) {
        var i = 0
        while (i < aliveCount) {
            age[i] += dt
            if (age[i] >= lifetime[i]) {
                removeAt(i)
                continue
            }
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    private fun emitFrom(emitter: ParticleEmitter2D, dt: Float) {
        if (!emitter.enabled) return
        val pos = emitter.origin()
        val toEmit = emitter.consumeSpawnCount(dt)
        var i = 0
        while (i < toEmit) {
            spawnParticle(pos, emitter)
            i++
        }
    }

    private fun spawnParticle(position: Offset, emitter: ParticleEmitter2D) {
        val index = if (aliveCount < maxParticles) {
            aliveCount++
            aliveCount - 1
        } else {
            val idx = recycleCursor
            recycleCursor = (recycleCursor + 1) % maxParticles
            idx
        }

        val coneOffsetDeg = lerp(-emitter.spreadDegrees * 0.5f, emitter.spreadDegrees * 0.5f, nextFloat())
        val angleRad = ((emitter.directionDegrees + coneOffsetDeg) * PI.toFloat()) / 180f
        val speedValue = lerp(emitter.minSpeed, emitter.maxSpeed, nextFloat())

        x[index] = position.x
        y[index] = position.y
        vx[index] = cos(angleRad) * speedValue
        vy[index] = sin(angleRad) * speedValue
        age[index] = 0f
        lifetime[index] = lerp(emitter.minLifetimeSeconds, emitter.maxLifetimeSeconds, nextFloat()).coerceAtLeast(0.0001f)
        size[index] = lerp(emitter.minSize, emitter.maxSize, nextFloat()).coerceAtLeast(0.05f)
        startAlpha[index] = emitter.startAlpha.coerceIn(0f, 1f)
        endAlpha[index] = emitter.endAlpha.coerceIn(0f, 1f)
        r[index] = emitter.color.red
        g[index] = emitter.color.green
        b[index] = emitter.color.blue
    }

    private fun removeAt(index: Int) {
        val last = aliveCount - 1
        if (index != last) {
            x[index] = x[last]
            y[index] = y[last]
            vx[index] = vx[last]
            vy[index] = vy[last]
            age[index] = age[last]
            lifetime[index] = lifetime[last]
            size[index] = size[last]
            startAlpha[index] = startAlpha[last]
            endAlpha[index] = endAlpha[last]
            r[index] = r[last]
            g[index] = g[last]
            b[index] = b[last]
        }
        aliveCount = last
    }

    private fun nextFloat(): Float {
        seed = seed * 1664525 + 1013904223
        val bits = seed ushr 8
        return bits.toFloat() / 0x00FFFFFF.toFloat()
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}

/**
 * Emitter configuration and internal spawn accumulator.
 *
 * [origin] is evaluated each frame, so emitters can follow moving entities.
 *
 * ```kotlin
 * val smoke = ParticleEmitter2D(
 *     particlesPerSecond = 25f,
 *     directionDegrees = -90f,
 *     spreadDegrees = 30f,
 *     color = Color.Gray,
 *     origin = { player.requireComponent<TransformComponent>().position },
 * )
 * ```
 */
class ParticleEmitter2D(
    var enabled: Boolean = true,
    var particlesPerSecond: Float = 60f,
    var directionDegrees: Float = -90f,
    var spreadDegrees: Float = 45f,
    var minSpeed: Float = 30f,
    var maxSpeed: Float = 100f,
    var minLifetimeSeconds: Float = 0.4f,
    var maxLifetimeSeconds: Float = 1.0f,
    var minSize: Float = 0.8f,
    var maxSize: Float = 2.4f,
    var startAlpha: Float = 0.95f,
    var endAlpha: Float = 0f,
    var color: Color = Color.White,
    var origin: () -> Offset,
) {
    private var spawnRemainder = 0f

    init {
        validate()
    }

    internal fun consumeSpawnCount(dt: Float): Int {
        if (particlesPerSecond <= 0f) return 0
        val total = spawnRemainder + particlesPerSecond * dt
        val whole = floor(total).toInt().coerceAtLeast(0)
        spawnRemainder = total - whole.toFloat()
        return whole
    }

    private fun validate() {
        require(particlesPerSecond.isFinite() && particlesPerSecond >= 0f) { "particlesPerSecond must be finite and >= 0." }
        require(spreadDegrees.isFinite() && spreadDegrees >= 0f) { "spreadDegrees must be finite and >= 0." }
        require(minSpeed.isFinite() && maxSpeed.isFinite() && maxSpeed >= minSpeed) { "maxSpeed must be >= minSpeed and finite." }
        require(minLifetimeSeconds.isFinite() && maxLifetimeSeconds.isFinite() && minLifetimeSeconds > 0f && maxLifetimeSeconds >= minLifetimeSeconds) {
            "Lifetime range must be finite, > 0 and max >= min."
        }
        require(minSize.isFinite() && maxSize.isFinite() && minSize > 0f && maxSize >= minSize) { "Size range must be finite, > 0 and max >= min." }
    }
}
