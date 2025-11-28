package com.kanvas.fx.gravity

import androidx.compose.ui.geometry.Offset
import com.kanvas.fx.core.FrameContext
import com.kanvas.fx.core.SceneSystem
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Barnes-Hut gravity solver for 2D n-body simulation.
 */
class BarnesHutGravitySystem(
    private val bodiesProvider: () -> List<GravityBody>,
    private val gravityConstant: Float = 3_800f,
    private val softening: Float = 28f,
    private val theta: Float = 0.65f,
) : SceneSystem {
    override fun update(frame: FrameContext) {
        val bodies = bodiesProvider()
        if (bodies.size < 2) return

        val bounds = computeBounds(bodies)
        val tree = QuadNode(bounds)
        for (body in bodies) tree.insert(body)
        tree.aggregate()

        val count = bodies.size
        val accX = FloatArray(count)
        val accY = FloatArray(count)
        for (index in 0 until count) {
            val acc = tree.accelerationFor(bodies[index], theta, gravityConstant, softening)
            accX[index] = acc.x
            accY[index] = acc.y
        }

        for (index in 0 until count) {
            val body = bodies[index]
            body.velocity = Offset(
                x = body.velocity.x + accX[index] * frame.deltaTimeSeconds,
                y = body.velocity.y + accY[index] * frame.deltaTimeSeconds,
            )
        }
        for (body in bodies) {
            body.position = Offset(
                x = body.position.x + body.velocity.x * frame.deltaTimeSeconds,
                y = body.position.y + body.velocity.y * frame.deltaTimeSeconds,
            )
        }
    }

}

private data class Rect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    fun contains(p: Offset): Boolean = p.x in minX..maxX && p.y in minY..maxY
}

private fun computeBounds(bodies: List<GravityBody>): Rect {
    var minX = bodies[0].position.x
    var minY = bodies[0].position.y
    var maxX = minX
    var maxY = minY
    for (b in bodies) {
        minX = minOf(minX, b.position.x)
        minY = minOf(minY, b.position.y)
        maxX = maxOf(maxX, b.position.x)
        maxY = maxOf(maxY, b.position.y)
    }
    val size = max(maxX - minX, max(maxY - minY, 64f))
    val cx = (minX + maxX) * 0.5f
    val cy = (minY + maxY) * 0.5f
    val half = size * 0.55f
    return Rect(cx - half, cy - half, cx + half, cy + half)
}

private class QuadNode(
    private val rect: Rect,
    private val depth: Int = 0,
) {
    private val leafBodies = mutableListOf<GravityBody>()
    private var nw: QuadNode? = null
    private var ne: QuadNode? = null
    private var sw: QuadNode? = null
    private var se: QuadNode? = null

    var totalMass = 0f
        private set
    var comX = 0f
        private set
    var comY = 0f
        private set

    fun insert(b: GravityBody) {
        if (!rect.contains(b.position)) return
        if (nw == null) {
            if (leafBodies.isEmpty()) {
                leafBodies += b
                return
            }
            if (depth >= MAX_DEPTH || rect.width <= MIN_CELL_SIZE) {
                leafBodies += b
                return
            }
            split()
            val snapshot = leafBodies.toList()
            leafBodies.clear()
            snapshot.forEach { existing -> childFor(existing.position)?.insert(existing) }
            childFor(b.position)?.insert(b)
            return
        }
        childFor(b.position)?.insert(b)
    }

    fun aggregate() {
        if (nw == null) {
            if (leafBodies.isEmpty()) {
                totalMass = 0f
                comX = 0f
                comY = 0f
            } else {
                totalMass = 0f
                comX = 0f
                comY = 0f
                for (body in leafBodies) {
                    totalMass += body.mass
                    comX += body.position.x * body.mass
                    comY += body.position.y * body.mass
                }
                if (totalMass > 0f) {
                    comX /= totalMass
                    comY /= totalMass
                }
            }
            return
        }
        totalMass = 0f
        comX = 0f
        comY = 0f
        for (child in listOfNotNull(nw, ne, sw, se)) {
            child.aggregate()
            totalMass += child.totalMass
            comX += child.comX * child.totalMass
            comY += child.comY * child.totalMass
        }
        if (totalMass > 0f) {
            comX /= totalMass
            comY /= totalMass
        }
    }

    fun accelerationFor(target: GravityBody, theta: Float, g: Float, softening: Float): Offset {
        if (totalMass <= 0f) return Offset.Zero
        val dx = comX - target.position.x
        val dy = comY - target.position.y
        val distSq = (dx * dx + dy * dy).coerceAtLeast(softening * softening)
        val dist = sqrt(distSq)
        if (nw == null) {
            var ax = 0f
            var ay = 0f
            for (body in leafBodies) {
                if (body.id == target.id) continue
                val bdx = body.position.x - target.position.x
                val bdy = body.position.y - target.position.y
                val bDistSq = (bdx * bdx + bdy * bdy).coerceAtLeast(softening * softening)
                val bDist = sqrt(bDistSq)
                val force = g * body.mass / bDistSq
                ax += force * bdx / bDist
                ay += force * bdy / bDist
            }
            return Offset(ax, ay)
        }
        if ((rect.width / dist) < theta) {
            val force = g * totalMass / distSq
            return Offset(force * dx / dist, force * dy / dist)
        }
        var ax = 0f
        var ay = 0f
        for (child in listOfNotNull(nw, ne, sw, se)) {
            val a = child.accelerationFor(target, theta, g, softening)
            ax += a.x
            ay += a.y
        }
        return Offset(ax, ay)
    }

    private fun split() {
        val midX = rect.centerX
        val midY = rect.centerY
        nw = QuadNode(Rect(rect.minX, rect.minY, midX, midY), depth + 1)
        ne = QuadNode(Rect(midX, rect.minY, rect.maxX, midY), depth + 1)
        sw = QuadNode(Rect(rect.minX, midY, midX, rect.maxY), depth + 1)
        se = QuadNode(Rect(midX, midY, rect.maxX, rect.maxY), depth + 1)
    }

    private fun childFor(position: Offset): QuadNode? {
        return when {
            position.x <= rect.centerX && position.y <= rect.centerY -> nw
            position.x > rect.centerX && position.y <= rect.centerY -> ne
            position.x <= rect.centerX && position.y > rect.centerY -> sw
            else -> se
        }
    }

    private companion object {
        const val MAX_DEPTH = 32
        const val MIN_CELL_SIZE = 1f
    }
}
