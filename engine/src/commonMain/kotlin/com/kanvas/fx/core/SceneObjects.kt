package com.kanvas.fx.core

import androidx.compose.ui.geometry.Offset

/**
 * High-level scene object API for external object management.
 */
class SceneObjects internal constructor(
    private val scene: Scene,
) {
    /**
     * Creates new object, applies [configure], and spawns it into scene.
     */
    fun create(
        id: String,
        configure: (Entity.() -> Unit)? = null,
    ): ObjectHandle {
        val objectEntity = Entity(id)
        configure?.invoke(objectEntity)
        scene.spawn(objectEntity)
        return ObjectHandle(scene, id)
    }

    /**
     * Adds already created [objectEntity] into scene.
     */
    fun add(objectEntity: Entity): ObjectHandle {
        scene.spawn(objectEntity)
        return ObjectHandle(scene, objectEntity.id)
    }

    /**
     * Finds object by id and returns control handle.
     */
    fun get(id: String): ObjectHandle? =
        if (scene.entity(id) != null || scene.snapshotEntities().any { it.id == id }) ObjectHandle(scene, id) else null

    /**
     * Requires object by id and returns handle.
     */
    fun require(id: String): ObjectHandle =
        get(id) ?: error("Object '$id' is not found in scene '${scene.name}'.")

    /**
     * Removes object by id if it exists.
     */
    fun remove(id: String): Boolean = scene.removeEntityIfExists(id)

    /**
     * Removes object by handle.
     */
    fun remove(handle: ObjectHandle): Boolean = remove(handle.id)

    /**
     * Returns handles for objects with [tag].
     */
    fun withTag(tag: String): List<ObjectHandle> = scene.entitiesTagged(tag).map { ObjectHandle(scene, it.id) }

    /**
     * Returns handles snapshot for all active scene objects.
     */
    fun all(): List<ObjectHandle> = scene.snapshotEntities().map { ObjectHandle(scene, it.id) }
}

/**
 * External mutable object handle.
 *
 * Handle remains valid while object exists in scene.
 */
class ObjectHandle internal constructor(
    private val scene: Scene,
    val id: String,
) {
    /**
     * Returns raw object entity or null when it no longer exists.
     */
    fun entityOrNull(): Entity? = scene.entity(id)

    /**
     * Returns raw object entity or throws when not found.
     */
    fun requireEntity(): Entity =
        entityOrNull() ?: error("Object '$id' is not found in scene '${scene.name}'.")

    var enabled: Boolean
        get() = requireEntity().enabled
        set(value) {
            requireEntity().enabled = value
        }

    var position: Offset
        get() = requireEntity().componentOrNull<TransformComponent>()?.position ?: Offset.Zero
        set(value) {
            val entity = requireEntity()
            val transform = entity.componentOrNull<TransformComponent>() ?: TransformComponent()
            transform.position = value
            entity.addComponent(transform)
        }

    fun moveBy(delta: Offset) {
        position += delta
    }

    fun setRenderOrder(zIndex: Int) {
        val entity = requireEntity()
        val render = entity.componentOrNull<RenderComponent>() ?: RenderComponent()
        render.zIndex = zIndex
        entity.addComponent(render)
    }

    fun addTag(tag: String) {
        val entity = requireEntity()
        val tags = entity.componentOrNull<TagsComponent>() ?: TagsComponent()
        tags.tags.add(tag)
        entity.addComponent(tags)
    }

    fun removeTag(tag: String) {
        val entity = requireEntity()
        val tags = entity.componentOrNull<TagsComponent>() ?: return
        tags.tags.remove(tag)
        if (tags.tags.isEmpty()) {
            entity.removeComponent(TagsComponent::class)
            return
        }
        entity.addComponent(tags)
    }

    fun playAnimation(
        clipId: String,
        restartIfAlreadyPlaying: Boolean = false,
        clearQueue: Boolean = true,
    ) {
        requireEntity().playAnimation(
            clipId = clipId,
            restartIfAlreadyPlaying = restartIfAlreadyPlaying,
            clearQueue = clearQueue,
        )
    }

    fun queueAnimation(clipId: String) {
        requireEntity().queueAnimation(clipId)
    }

    fun pauseAnimation() {
        requireEntity().pauseAnimation()
    }

    fun resumeAnimation() {
        requireEntity().resumeAnimation()
    }

    fun addAnimationClip(clip: AnimationClip) {
        requireEntity().addAnimationClip(clip)
    }

    fun remove(): Boolean = scene.removeEntityIfExists(id)
}
