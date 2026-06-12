package com.kanvas.fx.physics

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.EntityComponent
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.SceneSystem
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collider shape set supported by the basic 2D physics world.
 *
 * Colliders are defined in body-local coordinates and transformed by
 * [PhysicsBody2D.position]. They do not currently store rotation.
 */
sealed interface Collider2D {
    /** Circle collider centered on the body position. */
    data class Circle(val radius: Float) : Collider2D
    /** Axis-aligned box collider centered on the body position. */
    data class Aabb(val halfWidth: Float, val halfHeight: Float) : Collider2D
    /**
     * Convex polygon points in local body space.
     *
     * Points should be ordered around the polygon perimeter.
     */
    data class Polygon(val points: List<Offset>) : Collider2D
}

/**
 * Mutable rigid body state used by [PhysicsWorld2D].
 *
 * Dynamic bodies are integrated by gravity and collisions. Static bodies have zero inverse
 * mass and are not moved by integration or impulses.
 *
 * ```kotlin
 * val ball = PhysicsBody2D(
 *     id = "ball",
 *     position = Offset(100f, 40f),
 *     collider = Collider2D.Circle(radius = 16f),
 *     mass = 1f,
 *     restitution = 0.5f,
 * )
 * ```
 */
data class PhysicsBody2D(
    val id: String,
    var position: Offset,
    var velocity: Offset = Offset.Zero,
    val collider: Collider2D,
    var kind: String = "generic",
    var userData: Any? = null,
    var mass: Float = 1f,
    var restitution: Float = 0.2f,
    var isStatic: Boolean = false,
) {
    /**
     * Precomputed inverse mass for impulse calculations.
     *
     * Returns `0` for static or non-positive mass bodies.
     */
    val inverseMass: Float
        get() = if (isStatic || mass <= 0f) 0f else 1f / mass
}

/**
 * Entity component that binds an entity to [PhysicsBody2D].
 *
 * Systems can use this component to synchronize [TransformComponent] and physics body
 * positions.
 */
data class PhysicsBodyComponent(
    val body: PhysicsBody2D,
) : EntityComponent

/**
 * Collision contact data produced by [PhysicsWorld2D].
 *
 * [normal] points from [bodyA] toward [bodyB]. [penetration] is the overlap depth in
 * world units.
 */
data class Collision2D(
    val bodyA: PhysicsBody2D,
    val bodyB: PhysicsBody2D,
    val normal: Offset,
    val penetration: Float,
)

/**
 * Candidate-pair provider used before narrow-phase collision tests.
 */
interface BroadphaseStrategy {
    /**
     * Returns body index pairs that may overlap.
     */
    fun candidatePairs(bodies: List<PhysicsBody2D>): Sequence<Pair<Int, Int>>
}

/**
 * Brute-force broadphase that checks every pair.
 *
 * Use this for small body counts or deterministic tests.
 */
object NoBroadphase : BroadphaseStrategy {
    override fun candidatePairs(bodies: List<PhysicsBody2D>): Sequence<Pair<Int, Int>> = sequence {
        for (i in 0 until bodies.lastIndex) {
            for (j in i + 1 until bodies.size) yield(i to j)
        }
    }
}

/**
 * Uniform-grid broadphase for scenes with many spatially distributed bodies.
 *
 * [cellSize] should roughly match the average collider diameter. Too-small cells increase
 * bookkeeping; too-large cells approach brute-force behavior.
 */
class UniformGridBroadphase(
    private val cellSize: Float = 256f,
) : BroadphaseStrategy {
    override fun candidatePairs(bodies: List<PhysicsBody2D>): Sequence<Pair<Int, Int>> = sequence {
        if (bodies.isEmpty()) return@sequence
        val cell = cellSize.coerceAtLeast(1f)
        val cells = linkedMapOf<Pair<Int, Int>, MutableList<Int>>()
        for ((index, body) in bodies.withIndex()) {
            val bounds = bodyBounds(body)
            val minX = fastFloor(bounds.first / cell)
            val maxX = fastFloor(bounds.third / cell)
            val minY = fastFloor(bounds.second / cell)
            val maxY = fastFloor(bounds.fourth / cell)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    cells.getOrPut(x to y) { mutableListOf() }.add(index)
                }
            }
        }
        val emitted = hashSetOf<Long>()
        for (bucket in cells.values) {
            if (bucket.size < 2) continue
            for (i in 0 until bucket.lastIndex) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    val lo = minOf(a, b)
                    val hi = maxOf(a, b)
                    val key = (lo.toLong() shl 32) or hi.toLong()
                    if (emitted.add(key)) yield(lo to hi)
                }
            }
        }
    }
}

/**
 * Basic mutable 2D physics world.
 *
 * Includes:
 * - gravity integration;
 * - broadphase candidate generation;
 * - narrow-phase collision detection;
 * - impulse + positional correction resolution;
 * - enter/stay/exit collision callbacks.
 *
 * Typical DSL wiring:
 * ```kotlin
 * val world = physicsWorld2dUniformGrid()
 * scene("level") {
 *     systems { physics2d(world) }
 * }
 * ```
 *
 * Manual stepping:
 * ```kotlin
 * world.addBody(PhysicsBody2D("floor", Offset(0f, 320f), collider = Collider2D.Aabb(400f, 16f), isStatic = true))
 * world.step(1f / 60f)
 * val contacts = world.collisionsForBody("player")
 * ```
 */
class PhysicsWorld2D(
    var gravity: Offset = Offset(0f, 400f),
    private val iterations: Int = 4,
    private val broadphaseStrategy: BroadphaseStrategy = NoBroadphase,
) {
    private val bodies = linkedMapOf<String, PhysicsBody2D>()

    /** Called once when a body pair starts colliding. */
    var onCollisionEnter: (Collision2D) -> Unit = {}
    /** Called on every step while a body pair remains colliding. */
    var onCollisionStay: (Collision2D) -> Unit = {}
    /** Called once when a previously colliding pair separates. */
    var onCollisionExit: (String, String) -> Unit = { _, _ -> }

    private var previousPairs = emptySet<Pair<String, String>>()
    private var latestCollisions: List<Collision2D> = emptyList()

    /**
     * Adds or replaces a body by id.
     *
     * Replacing a body keeps the world map stable but resets any external references to
     * the previous body instance.
     */
    fun addBody(body: PhysicsBody2D) {
        bodies[body.id] = body
    }

    /**
     * Removes body by id.
     */
    fun removeBody(id: String) {
        bodies.remove(id)
    }

    /**
     * Returns body by id.
     */
    fun body(id: String): PhysicsBody2D? = bodies[id]

    /**
     * Immutable body-list snapshot.
     *
     * The returned list is detached from the world map, but the body objects themselves are
     * mutable runtime state.
     */
    fun snapshotBodies(): List<PhysicsBody2D> = bodies.values.toList()

    /**
     * Returns collisions from latest simulation step for body [id].
     */
    fun collisionsForBody(id: String): List<Collision2D> =
        latestCollisions.filter { it.bodyA.id == id || it.bodyB.id == id }

    /**
     * Advances world state by [deltaTimeSeconds].
     *
     * Steps:
     * 1. integrate velocity/position;
     * 2. detect collisions from broadphase candidates;
     * 3. resolve impulses for configured iteration count;
     * 4. apply positional correction;
     * 5. emit enter/stay/exit callbacks.
     *
     * Negative deltas are not expected. Fixed-step engines should pass the fixed
     * [FrameContext.deltaTimeSeconds].
     */
    fun step(deltaTimeSeconds: Float) {
        if (bodies.size < 2) {
            integrate(deltaTimeSeconds)
            return
        }

        integrate(deltaTimeSeconds)

        val values = bodies.values.toList()
        val collisions = mutableListOf<Collision2D>()
        for ((i, j) in broadphaseStrategy.candidatePairs(values)) {
            val a = values[i]
            val b = values[j]
            if (a.isStatic && b.isStatic) continue
            detectCollision(a, b)?.let(collisions::add)
        }

        repeat(iterations.coerceAtLeast(1)) {
            collisions.forEach(::resolveCollision)
        }
        collisions.forEach(::positionalCorrection)
        latestCollisions = collisions

        val currentPairs = collisions.map { canonicalPair(it.bodyA.id, it.bodyB.id) }.toSet()
        val enterPairs = currentPairs - previousPairs
        val stayPairs = currentPairs intersect previousPairs
        val exitPairs = previousPairs - currentPairs

        collisions.forEach { c ->
            val pair = canonicalPair(c.bodyA.id, c.bodyB.id)
            when {
                pair in enterPairs -> onCollisionEnter(c)
                pair in stayPairs -> onCollisionStay(c)
            }
        }
        exitPairs.forEach { (a, b) -> onCollisionExit(a, b) }
        previousPairs = currentPairs
    }

    private fun integrate(dt: Float) {
        for (body in bodies.values) {
            if (body.isStatic) continue
            body.velocity = body.velocity + gravity * dt
            body.position = body.position + body.velocity * dt
        }
    }

    private fun detectCollision(a: PhysicsBody2D, b: PhysicsBody2D): Collision2D? {
        return when (val ac = a.collider) {
            is Collider2D.Circle -> when (val bc = b.collider) {
                is Collider2D.Circle -> collideCircleCircle(a, ac, b, bc)
                is Collider2D.Aabb -> collideCircleAabb(a, ac, b, bc)
                is Collider2D.Polygon -> collideCirclePolygon(a, ac, b, bc)
            }
            is Collider2D.Aabb -> when (val bc = b.collider) {
                is Collider2D.Circle -> collideCircleAabb(b, bc, a, ac)?.let {
                    it.copy(bodyA = a, bodyB = b, normal = -it.normal)
                }
                is Collider2D.Aabb -> collideAabbAabb(a, ac, b, bc)
                is Collider2D.Polygon -> collidePolygonPolygon(
                    a = a,
                    aPoints = aabbAsWorldPolygon(a, ac),
                    b = b,
                    bPoints = toWorldPoints(b, bc.points),
                )
            }
            is Collider2D.Polygon -> when (val bc = b.collider) {
                is Collider2D.Circle -> collideCirclePolygon(b, bc, a, ac)?.let {
                    it.copy(bodyA = a, bodyB = b, normal = -it.normal)
                }
                is Collider2D.Aabb -> collidePolygonPolygon(
                    a = a,
                    aPoints = toWorldPoints(a, ac.points),
                    b = b,
                    bPoints = aabbAsWorldPolygon(b, bc),
                )
                is Collider2D.Polygon -> collidePolygonPolygon(
                    a = a,
                    aPoints = toWorldPoints(a, ac.points),
                    b = b,
                    bPoints = toWorldPoints(b, bc.points),
                )
            }
        }
    }

    private fun collideCircleCircle(
        a: PhysicsBody2D,
        ac: Collider2D.Circle,
        b: PhysicsBody2D,
        bc: Collider2D.Circle,
    ): Collision2D? {
        val delta = b.position - a.position
        val distSq = delta.x * delta.x + delta.y * delta.y
        val r = ac.radius + bc.radius
        if (distSq >= r * r) return null
        val dist = sqrt(distSq.coerceAtLeast(0.0001f))
        val normal = if (dist > 0f) delta / dist else Offset(1f, 0f)
        return Collision2D(a, b, normal, r - dist)
    }

    private fun collideAabbAabb(
        a: PhysicsBody2D,
        ac: Collider2D.Aabb,
        b: PhysicsBody2D,
        bc: Collider2D.Aabb,
    ): Collision2D? {
        val dx = b.position.x - a.position.x
        val px = (ac.halfWidth + bc.halfWidth) - abs(dx)
        if (px <= 0f) return null
        val dy = b.position.y - a.position.y
        val py = (ac.halfHeight + bc.halfHeight) - abs(dy)
        if (py <= 0f) return null

        return if (px < py) {
            val normal = if (dx >= 0f) Offset(1f, 0f) else Offset(-1f, 0f)
            Collision2D(a, b, normal, px)
        } else {
            val normal = if (dy >= 0f) Offset(0f, 1f) else Offset(0f, -1f)
            Collision2D(a, b, normal, py)
        }
    }

    private fun collideCircleAabb(
        circleBody: PhysicsBody2D,
        circle: Collider2D.Circle,
        boxBody: PhysicsBody2D,
        box: Collider2D.Aabb,
    ): Collision2D? {
        val cx = circleBody.position.x
        val cy = circleBody.position.y
        val bx = boxBody.position.x
        val by = boxBody.position.y
        val clampedX = cx.coerceIn(bx - box.halfWidth, bx + box.halfWidth)
        val clampedY = cy.coerceIn(by - box.halfHeight, by + box.halfHeight)
        val dx = clampedX - cx
        val dy = clampedY - cy
        val distSq = dx * dx + dy * dy
        if (distSq > circle.radius * circle.radius) return null
        val dist = sqrt(distSq.coerceAtLeast(0.0001f))
        val normal = if (dist > 0f) Offset(dx / dist, dy / dist) else Offset(0f, -1f)
        return Collision2D(circleBody, boxBody, normal, circle.radius - dist)
    }

    private fun collideCirclePolygon(
        circleBody: PhysicsBody2D,
        circle: Collider2D.Circle,
        polyBody: PhysicsBody2D,
        polygon: Collider2D.Polygon,
    ): Collision2D? {
        val points = toWorldPoints(polyBody, polygon.points)
        if (points.size < 3) return null

        var minDistSq = Float.MAX_VALUE
        var closest = points.first()
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val c = closestPointOnSegment(circleBody.position, a, b)
            val d = c - circleBody.position
            val distSq = d.x * d.x + d.y * d.y
            if (distSq < minDistSq) {
                minDistSq = distSq
                closest = c
            }
        }

        val inside = pointInPolygon(circleBody.position, points)
        if (!inside && minDistSq > circle.radius * circle.radius) return null

        val toCircle = circleBody.position - closest
        val dist = sqrt((toCircle.x * toCircle.x + toCircle.y * toCircle.y).coerceAtLeast(0.0001f))
        val normal = if (dist > 0f) toCircle / dist else Offset(0f, -1f)
        val penetration = if (inside) circle.radius + dist else circle.radius - dist
        return Collision2D(circleBody, polyBody, normal, penetration.coerceAtLeast(0.0001f))
    }

    private fun collidePolygonPolygon(
        a: PhysicsBody2D,
        aPoints: List<Offset>,
        b: PhysicsBody2D,
        bPoints: List<Offset>,
    ): Collision2D? {
        if (aPoints.size < 3 || bPoints.size < 3) return null
        var minOverlap = Float.MAX_VALUE
        var minAxis = Offset.Zero

        fun testAxes(points: List<Offset>, other: List<Offset>): Boolean {
            for (i in points.indices) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                val edge = p2 - p1
                val axis = normalize(Offset(-edge.y, edge.x))
                val projA = project(points, axis)
                val projB = project(other, axis)
                val overlap = minOf(projA.second, projB.second) - maxOf(projA.first, projB.first)
                if (overlap <= 0f) return false
                if (overlap < minOverlap) {
                    minOverlap = overlap
                    minAxis = axis
                }
            }
            return true
        }

        if (!testAxes(aPoints, bPoints)) return null
        if (!testAxes(bPoints, aPoints)) return null

        val dir = b.position - a.position
        if (dot(dir, minAxis) < 0f) minAxis = -minAxis
        return Collision2D(a, b, minAxis, minOverlap)
    }

    private fun aabbAsWorldPolygon(body: PhysicsBody2D, aabb: Collider2D.Aabb): List<Offset> {
        val cx = body.position.x
        val cy = body.position.y
        val hw = aabb.halfWidth
        val hh = aabb.halfHeight
        return listOf(
            Offset(cx - hw, cy - hh),
            Offset(cx + hw, cy - hh),
            Offset(cx + hw, cy + hh),
            Offset(cx - hw, cy + hh),
        )
    }

    private fun toWorldPoints(body: PhysicsBody2D, localPoints: List<Offset>): List<Offset> =
        localPoints.map { it + body.position }

    private fun resolveCollision(c: Collision2D) {
        val a = c.bodyA
        val b = c.bodyB
        val rv = b.velocity - a.velocity
        val velocityAlongNormal = dot(rv, c.normal)
        if (velocityAlongNormal > 0f) return

        val invMassSum = a.inverseMass + b.inverseMass
        if (invMassSum <= 0f) return
        val restitution = minOf(a.restitution, b.restitution)
        val impulseScalar = -(1f + restitution) * velocityAlongNormal / invMassSum
        val impulse = c.normal * impulseScalar

        if (!a.isStatic) a.velocity -= impulse * a.inverseMass
        if (!b.isStatic) b.velocity += impulse * b.inverseMass
    }

    private fun positionalCorrection(c: Collision2D) {
        val a = c.bodyA
        val b = c.bodyB
        val invMassSum = a.inverseMass + b.inverseMass
        if (invMassSum <= 0f) return
        val percent = 0.6f
        val slop = 0.01f
        val magnitude = ((c.penetration - slop).coerceAtLeast(0f) / invMassSum) * percent
        val correction = c.normal * magnitude
        if (!a.isStatic) a.position -= correction * a.inverseMass
        if (!b.isStatic) b.position += correction * b.inverseMass
    }

    private fun canonicalPair(a: String, b: String): Pair<String, String> = if (a < b) a to b else b to a
}

class PhysicsSystem2D(
    private val world: PhysicsWorld2D,
) : SceneSystem {
    /**
     * Delegates scene frame update to [PhysicsWorld2D.step].
     */
    override fun update(frame: FrameContext) {
        world.step(frame.deltaTimeSeconds)
    }
}

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)
private operator fun Offset.div(value: Float): Offset = Offset(x / value, y / value)
private fun dot(a: Offset, b: Offset): Float = a.x * b.x + a.y * b.y
private fun normalize(v: Offset): Offset {
    val len = sqrt((v.x * v.x + v.y * v.y).coerceAtLeast(0.0001f))
    return Offset(v.x / len, v.y / len)
}
private fun project(points: List<Offset>, axis: Offset): Pair<Float, Float> {
    var min = dot(points.first(), axis)
    var max = min
    for (i in 1 until points.size) {
        val p = dot(points[i], axis)
        if (p < min) min = p
        if (p > max) max = p
    }
    return min to max
}
private fun closestPointOnSegment(p: Offset, a: Offset, b: Offset): Offset {
    val ab = b - a
    val abLenSq = dot(ab, ab).coerceAtLeast(0.0001f)
    val t = (dot(p - a, ab) / abLenSq).coerceIn(0f, 1f)
    return a + ab * t
}
private fun pointInPolygon(p: Offset, points: List<Offset>): Boolean {
    var sign = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        if (cross == 0f) continue
        if (sign == 0f) sign = cross
        else if (sign * cross < 0f) return false
    }
    return true
}

private fun bodyBounds(body: PhysicsBody2D): Quad {
    return when (val collider = body.collider) {
        is Collider2D.Circle -> Quad(
            first = body.position.x - collider.radius,
            second = body.position.y - collider.radius,
            third = body.position.x + collider.radius,
            fourth = body.position.y + collider.radius,
        )
        is Collider2D.Aabb -> Quad(
            first = body.position.x - collider.halfWidth,
            second = body.position.y - collider.halfHeight,
            third = body.position.x + collider.halfWidth,
            fourth = body.position.y + collider.halfHeight,
        )
        is Collider2D.Polygon -> {
            val world = collider.points.map { it + body.position }
            val minX = world.minOf { it.x }
            val minY = world.minOf { it.y }
            val maxX = world.maxOf { it.x }
            val maxY = world.maxOf { it.y }
            Quad(minX, minY, maxX, maxY)
        }
    }
}

private fun fastFloor(value: Float): Int = kotlin.math.floor(value).toInt()

private data class Quad(
    val first: Float,
    val second: Float,
    val third: Float,
    val fourth: Float,
)
