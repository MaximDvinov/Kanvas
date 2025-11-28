package com.kanvas.fx.physics

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BroadphaseTest {
    @Test
    fun uniformGridProducesCandidatePairsWithoutDuplicates() {
        val bodies = listOf(
            PhysicsBody2D("a", position = Offset(0f, 0f), collider = Collider2D.Circle(10f)),
            PhysicsBody2D("b", position = Offset(8f, 0f), collider = Collider2D.Circle(10f)),
            PhysicsBody2D("c", position = Offset(120f, 0f), collider = Collider2D.Circle(10f)),
        )
        val pairs = UniformGridBroadphase(cellSize = 32f)
            .candidatePairs(bodies)
            .toList()

        assertTrue((0 to 1) in pairs)
        assertEquals(pairs.toSet().size, pairs.size)
    }
}

