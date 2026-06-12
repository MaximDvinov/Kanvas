package com.kanvas.fx.sample.planets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kanvas.fx.gravity.GravityBody

data class PlanetState(
    val id: String,
    var textureId: String,
    val body: GravityBody,
    var kind: BodyKind,
    var visualClass: BodyVisualClass,
    var eventHorizonRadius: Float = 0f,
    var radius: Float,
    var color: Color,
    val trailPoints: TrailBuffer,
    val selectedTrailPoints: MutableList<Offset>,
    val isTrailVisible: () -> Boolean,
)

data class BodySeed(
    val planetId: String,
    val bodyId: String,
    val position: Offset,
    val velocity: Offset,
    val mass: Float,
    val textureId: String? = null,
)

enum class ScenePreset(val title: String) {
    Empty("Empty"),
    SolarSystem("Solar"),
    AlphaCentauri("Alpha Cen"),
    AsteroidField("Asteroids"),
}

enum class ToolMode(val title: String) {
    Spawn("Spawn"),
    Impulse("Impulse"),
    Delete("Delete"),
}

enum class SpawnPreset(val title: String) {
    SinglePlanet("Planet"),
    AsteroidCluster("Cluster"),
    BinaryPair("Binary"),
    Star("Star"),
    BlackHoleSmall("Black Hole"),
}

enum class BodyKind {
    Planet,
    Asteroid,
    Star,
    BlackHole,
}

enum class BodyVisualClass {
    Asteroid,
    RockySmall,
    Terrestrial,
    IceWorld,
    GasGiant,
    Star,
    BlackHole,
}

data class BodyVisualProfile(
    val visualClass: BodyVisualClass,
    val minMass: Float,
    val maxMass: Float,
    val minRadius: Float,
    val maxRadius: Float,
    val textureIds: List<String>,
)

data class BlackHoleConfig(
    val mass: Float = 120_000f,
    val eventHorizonRadius: Float = 30f,
)

data class StarConfig(
    val mass: Float = MIN_STAR_MASS,
)

data class AsteroidClusterConfig(
    val count: Int = 14,
    val radius: Float = 140f,
    val minMass: Float = 6f,
    val maxMass: Float = 28f,
    val maxTangentialVelocity: Float = 80f,
)

data class SandboxGameState(
    val score: Int = 0,
    val elapsedSeconds: Float = 0f,
    val bodies: Int = 0,
    val toolMode: ToolMode = ToolMode.Spawn,
    val spawnPreset: SpawnPreset = SpawnPreset.SinglePlanet,
)

data class ObjectTelemetry(
    val id: String,
    val kind: BodyKind,
    val mass: Float,
    val radius: Float,
    val speed: Float,
)

class TrailBuffer(
    private val capacity: Int,
) {
    private val points = Array<Offset?>(capacity) { null }
    private var head = 0
    private var size = 0
    private var cacheDirty = true
    private val cache = ArrayList<Offset>(capacity)

    fun add(point: Offset) {
        if (capacity <= 0) return
        points[head] = point
        head = (head + 1) % capacity
        if (size < capacity) size++
        cacheDirty = true
    }

    fun snapshot(): List<Offset> {
        if (!cacheDirty) return cache
        cache.clear()
        val start = if (size == capacity) head else 0
        repeat(size) { idx ->
            val point = points[(start + idx) % capacity]
            if (point != null) cache += point
        }
        cacheDirty = false
        return cache
    }

    fun resetTo(point: Offset?) {
        points.fill(null)
        head = 0
        size = 0
        cacheDirty = true
        if (point != null) add(point)
    }
}
