package com.kanvas.fx.worldkit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import com.kanvas.fx.core.EngineInputEvent
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.EntityBehavior
import com.kanvas.fx.core.KeyDown
import com.kanvas.fx.core.KeyUp
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.physics.Collider2D
import com.kanvas.fx.physics.PhysicsBody2D
import com.kanvas.fx.physics.PhysicsBodyComponent
import com.kanvas.fx.physics.PhysicsWorld2D

/**
 * Reusable archetype kit for common 2D platformer objects.
 *
 * The kit creates scene entities and binds them to [PhysicsBody2D] instances in [PhysicsWorld2D].
 */
class ClassicObjects2D(
    private val scene: Scene,
    private val world: PhysicsWorld2D,
) {
    /**
     * Creates static floor entity.
     */
    fun floor(
        id: String,
        center: Offset,
        width: Float,
        thickness: Float = 32f,
        color: Color = Color(0xFF5B6378),
    ): PhysicsGameObject2D = staticRect(
        id = id,
        center = center,
        size = Size(width, thickness),
        kind = "floor",
        color = color,
    )

    /**
     * Creates static wall entity.
     */
    fun wall(
        id: String,
        center: Offset,
        width: Float = 32f,
        height: Float,
        color: Color = Color(0xFF5B6378),
    ): PhysicsGameObject2D = staticRect(
        id = id,
        center = center,
        size = Size(width, height),
        kind = "wall",
        color = color,
    )

    /**
     * Creates static platform entity.
     */
    fun platform(
        id: String,
        center: Offset,
        width: Float,
        height: Float = 18f,
        color: Color = Color(0xFF75819C),
    ): PhysicsGameObject2D = staticRect(
        id = id,
        center = center,
        size = Size(width, height),
        kind = "platform",
        color = color,
    )

    /**
     * Creates dynamic rectangular crate entity.
     */
    fun crate(
        id: String,
        center: Offset,
        size: Size = Size(28f, 28f),
        mass: Float = 1.2f,
        restitution: Float = 0.01f,
        color: Color = Color(0xFFAA7A46),
    ): PhysicsGameObject2D {
        val body = PhysicsBody2D(
            id = "$id-body",
            position = center,
            collider = Collider2D.Aabb(size.width * 0.5f, size.height * 0.5f),
            kind = "crate",
            mass = mass,
            restitution = restitution,
        )
        val entity = physicsEntity(id, body, zIndex = 10) {
            rect(
                topLeft = body.position - Offset(size.width * 0.5f, size.height * 0.5f),
                size = size,
                color = color,
            )
        }
        return PhysicsGameObject2D(body, entity)
    }

    /**
     * Creates polygon physics entity.
     */
    fun polygonBody(
        id: String,
        center: Offset,
        points: List<Offset>,
        isStatic: Boolean = false,
        mass: Float = 1f,
        restitution: Float = 0.1f,
        color: Color = Color(0xFF9B8DF7),
    ): PhysicsGameObject2D {
        val body = PhysicsBody2D(
            id = "$id-body",
            position = center,
            collider = Collider2D.Polygon(points),
            kind = "polygon",
            mass = mass,
            restitution = restitution,
            isStatic = isStatic,
        )
        val entity = physicsEntity(id, body, zIndex = if (isStatic) 6 else 12) {
            polygon(
                points = points.map { it + body.position },
                color = color,
            )
        }
        return PhysicsGameObject2D(body, entity)
    }

    /**
     * Creates controllable player entity with default platformer behavior.
     */
    fun player(
        id: String,
        center: Offset,
        size: Size = Size(30f, 44f),
        mass: Float = 1f,
        moveSpeed: Float = 220f,
        jumpImpulse: Float = 500f,
        color: Color = Color(0xFF4CC3FF),
        controls: PlayerControls = PlayerControls(),
    ): PlayerCharacter2D {
        val body = PhysicsBody2D(
            id = "$id-body",
            position = center,
            collider = Collider2D.Aabb(size.width * 0.5f, size.height * 0.5f),
            kind = "player",
            mass = mass,
            restitution = 0f,
        )
        val controller = PlayerCharacter2D(
            body = body,
            world = world,
            moveSpeed = moveSpeed,
            jumpImpulse = jumpImpulse,
            controls = controls,
        )
        val entity = physicsEntity(id, body, zIndex = 20) {
            rect(
                topLeft = body.position - Offset(size.width * 0.5f, size.height * 0.5f),
                size = size,
                color = color,
            )
        }.apply {
            addBehavior(
                object : EntityBehavior {
                    override fun onInput(entity: Entity, event: EngineInputEvent) {
                        controller.onInput(event)
                    }

                    override fun onUpdate(entity: Entity, frame: com.kanvas.fx.core.FrameContext) {
                        controller.onFrame(frame.deltaTimeSeconds)
                    }
                },
            )
        }
        return controller.copyWithObject(entity)
    }

    /**
     * Extension point for custom entity + body pair.
     */
    fun custom(
        body: PhysicsBody2D,
        entityId: String,
        configureEntity: Entity.() -> Unit,
    ): PhysicsGameObject2D {
        val entity = Entity(entityId).apply {
            addComponent(PhysicsBodyComponent(body))
            addBehavior(SyncBodyBehavior)
            configureEntity()
        }
        attach(body, entity)
        return PhysicsGameObject2D(body, entity)
    }

    private fun staticRect(
        id: String,
        center: Offset,
        size: Size,
        kind: String,
        color: Color,
    ): PhysicsGameObject2D {
        val body = PhysicsBody2D(
            id = "$id-body",
            position = center,
            collider = Collider2D.Aabb(size.width * 0.5f, size.height * 0.5f),
            kind = kind,
            isStatic = true,
        )
        val entity = physicsEntity(id, body, zIndex = 5) {
            rect(
                topLeft = center - Offset(size.width * 0.5f, size.height * 0.5f),
                size = size,
                color = color,
            )
        }
        return PhysicsGameObject2D(body, entity)
    }

    private fun physicsEntity(
        id: String,
        body: PhysicsBody2D,
        zIndex: Int,
        render: com.kanvas.fx.render.Renderer2D.(Entity) -> Unit,
    ): Entity {
        val entity = Entity(id).apply {
            addComponent(PhysicsBodyComponent(body))
            addComponent(RenderComponent(zIndex = zIndex, renderer = render))
            addBehavior(SyncBodyBehavior)
        }
        attach(body, entity)
        return entity
    }

    private fun attach(body: PhysicsBody2D, entity: Entity) {
        world.addBody(body)
        scene.spawn(entity)
    }

    private object SyncBodyBehavior : EntityBehavior {
        override fun onUpdate(entity: Entity, frame: com.kanvas.fx.core.FrameContext) {
            // Physics body is source of truth; render lambdas read body directly.
        }
    }
}

/**
 * Pair of physics body and scene entity produced by [ClassicObjects2D].
 */
data class PhysicsGameObject2D(
    val body: PhysicsBody2D,
    val entity: Entity,
)

/**
 * Keyboard controls mapping for [PlayerCharacter2D].
 */
data class PlayerControls(
    val leftKeyCode: Long = Key.A.keyCode,
    val rightKeyCode: Long = Key.D.keyCode,
    val jumpKeyCode: Long = Key.Spacebar.keyCode,
)

/**
 * Runtime controller for player movement/jump behavior.
 */
class PlayerCharacter2D internal constructor(
    val body: PhysicsBody2D,
    private val world: PhysicsWorld2D,
    private val moveSpeed: Float,
    private val jumpImpulse: Float,
    private val controls: PlayerControls,
    val entity: Entity? = null,
) {
    private val pressed = mutableSetOf<Long>()
    private var requestedJump = false

    /**
     * Feeds input events into controller state.
     */
    fun onInput(event: EngineInputEvent) {
        when (event) {
            is KeyDown -> {
                pressed += event.keyCode
                if (event.keyCode == controls.jumpKeyCode) requestedJump = true
            }
            is KeyUp -> pressed -= event.keyCode
            else -> Unit
        }
    }

    /**
     * Applies one movement update step.
     */
    fun onFrame(deltaSeconds: Float) {
        var horizontal = 0f
        if (controls.leftKeyCode in pressed) horizontal -= 1f
        if (controls.rightKeyCode in pressed) horizontal += 1f
        body.velocity = Offset(horizontal * moveSpeed, body.velocity.y)

        if (requestedJump && isGrounded()) {
            body.velocity = Offset(body.velocity.x, body.velocity.y - jumpImpulse)
        }
        requestedJump = false
    }

    private fun isGrounded(): Boolean {
        val collisions = world.collisionsForBody(body.id)
        for (collision in collisions) {
            val normal = if (collision.bodyA.id == body.id) collision.normal else -collision.normal
            if (normal.y > 0.35f) return true
        }
        return false
    }

    internal fun copyWithObject(entity: Entity): PlayerCharacter2D =
        PlayerCharacter2D(body, world, moveSpeed, jumpImpulse, controls, entity)
}
