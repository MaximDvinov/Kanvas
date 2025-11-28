package com.kanvas.fx.core

/**
 * One sprite frame.
 *
 * @property textureId asset id used by renderer.
 * @property sourceX source left in pixels inside texture atlas.
 * @property sourceY source top in pixels inside texture atlas.
 * @property sourceWidth source width in pixels, or null to use full texture width.
 * @property sourceHeight source height in pixels, or null to use full texture height.
 */
data class SpriteFrame(
    val textureId: String,
    val sourceX: Int = 0,
    val sourceY: Int = 0,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
)

/**
 * Sprite animation clip.
 */
data class AnimationClip(
    val id: String,
    val frames: List<SpriteFrame>,
    val fps: Float = 12f,
    val loop: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Animation clip id must not be blank." }
        require(frames.isNotEmpty()) { "Animation clip '$id' must contain at least one frame." }
        require(fps.isFinite() && fps > 0f) { "Animation clip '$id' fps must be finite and > 0." }
    }
}

/**
 * Animation library attached to entity.
 */
class AnimationLibraryComponent(
    val clips: MutableMap<String, AnimationClip> = linkedMapOf(),
) : EntityComponent

/**
 * Runtime animation player state attached to entity.
 */
data class AnimationPlayerComponent(
    var currentClipId: String,
    var frameIndex: Int = 0,
    var elapsedInFrameSeconds: Float = 0f,
    var speed: Float = 1f,
    var paused: Boolean = false,
    var holdOnLastFrameWhenDone: Boolean = true,
    val queuedClipIds: MutableList<String> = mutableListOf(),
    var justFinishedClipId: String? = null,
) : EntityComponent {
    init {
        require(currentClipId.isNotBlank()) { "Animation player currentClipId must not be blank." }
        require(speed.isFinite() && speed >= 0f) { "Animation player speed must be finite and >= 0." }
    }
}

/**
 * Deterministic sprite animation system.
 *
 * Should typically run in [SystemPhase.PostPhysics] or [SystemPhase.RenderPrep].
 */
class SpriteAnimationSystem : SceneSystem {
    override fun update(frame: FrameContext) {
        val entities = frame.scene.entitiesWith(AnimationPlayerComponent::class)
        for (entity in entities) {
            val player = entity.componentOrNull<AnimationPlayerComponent>() ?: continue
            val library = entity.componentOrNull<AnimationLibraryComponent>() ?: continue
            val clip = library.clips[player.currentClipId] ?: continue
            player.justFinishedClipId = null
            if (player.paused) continue

            val effectiveDelta = frame.deltaTimeSeconds * player.speed.coerceAtLeast(0f)
            if (effectiveDelta <= 0f) continue

            val frameDuration = 1f / clip.fps
            player.elapsedInFrameSeconds += effectiveDelta
            while (player.elapsedInFrameSeconds >= frameDuration) {
                player.elapsedInFrameSeconds -= frameDuration
                val nextIndex = player.frameIndex + 1
                if (nextIndex < clip.frames.size) {
                    player.frameIndex = nextIndex
                    continue
                }

                // Current clip reached the end.
                player.justFinishedClipId = clip.id
                val queued = player.queuedClipIds.firstOrNull()
                if (queued != null && library.clips.containsKey(queued)) {
                    player.queuedClipIds.removeAt(0)
                    player.currentClipId = queued
                    player.frameIndex = 0
                    player.elapsedInFrameSeconds = 0f
                    break
                }

                if (clip.loop) {
                    player.frameIndex = 0
                } else {
                    player.frameIndex = (clip.frames.size - 1).coerceAtLeast(0)
                    if (player.holdOnLastFrameWhenDone) {
                        player.paused = true
                    }
                    break
                }
            }
        }
    }
}

fun Entity.addAnimationClip(clip: AnimationClip): Entity {
    val library = componentOrNull<AnimationLibraryComponent>() ?: AnimationLibraryComponent()
    library.clips[clip.id] = clip
    addComponent(library)
    return this
}

fun Entity.setAnimationClips(clips: Iterable<AnimationClip>): Entity {
    val library = AnimationLibraryComponent()
    clips.forEach { library.clips[it.id] = it }
    addComponent(library)
    return this
}

fun Entity.playAnimation(
    clipId: String,
    restartIfAlreadyPlaying: Boolean = false,
    clearQueue: Boolean = true,
): Entity {
    require(clipId.isNotBlank()) { "Animation clip id must not be blank." }
    val player = componentOrNull<AnimationPlayerComponent>()
    if (player == null) {
        addComponent(AnimationPlayerComponent(currentClipId = clipId))
        return this
    }
    if (player.currentClipId == clipId && !restartIfAlreadyPlaying) {
        player.paused = false
        if (clearQueue) player.queuedClipIds.clear()
        addComponent(player)
        return this
    }
    player.currentClipId = clipId
    player.frameIndex = 0
    player.elapsedInFrameSeconds = 0f
    player.paused = false
    if (clearQueue) player.queuedClipIds.clear()
    addComponent(player)
    return this
}

fun Entity.queueAnimation(clipId: String): Entity {
    require(clipId.isNotBlank()) { "Animation clip id must not be blank." }
    val player = componentOrNull<AnimationPlayerComponent>()
    if (player == null) {
        addComponent(AnimationPlayerComponent(currentClipId = clipId))
        return this
    }
    player.queuedClipIds += clipId
    addComponent(player)
    return this
}

fun Entity.pauseAnimation(): Entity {
    val player = componentOrNull<AnimationPlayerComponent>() ?: return this
    player.paused = true
    addComponent(player)
    return this
}

fun Entity.resumeAnimation(): Entity {
    val player = componentOrNull<AnimationPlayerComponent>() ?: return this
    player.paused = false
    addComponent(player)
    return this
}

fun Entity.currentAnimationFrameOrNull(): SpriteFrame? {
    val player = componentOrNull<AnimationPlayerComponent>() ?: return null
    val library = componentOrNull<AnimationLibraryComponent>() ?: return null
    val clip = library.clips[player.currentClipId] ?: return null
    val index = player.frameIndex.coerceIn(0, clip.frames.lastIndex)
    return clip.frames[index]
}
