package com.kanvas.fx.game

import com.kanvas.fx.core.Engine
import com.kanvas.fx.core.EngineInputEvent
import com.kanvas.fx.core.Entity
import com.kanvas.fx.core.InputComponent
import com.kanvas.fx.core.MetadataComponent
import com.kanvas.fx.core.RenderComponent
import com.kanvas.fx.core.Scene
import com.kanvas.fx.core.SceneSystem
import com.kanvas.fx.core.SceneSystemSpec
import com.kanvas.fx.core.TagsComponent
import com.kanvas.fx.core.TransformComponent

private const val GAME_META_KEY = "__game_host_meta"
private val managedEntityIdsByScene = linkedMapOf<Scene, MutableSet<String>>()

fun reconcileGameGraph(
    engine: Engine,
    graph: GameGraph,
): GameRuntimeContext {
    val runtime = GameRuntimeContext(engine)
    graph.scenes.forEach { spec ->
        ensureSceneExists(engine, spec)
    }

    graph.scenes.forEach { spec ->
        val scene = engine.requireScene(spec.name)
        applySceneSettings(scene, spec)
        reconcileSystems(scene, spec)
        reconcileEntities(scene, spec)
        reconcileSceneInput(scene, spec)
    }

    val targetScene = graph.currentScene
    if (targetScene != null) {
        engine.switchSceneOrNull(targetScene)
    }

    return runtime
}

private fun ensureSceneExists(engine: Engine, spec: GameSceneNode) {
    if (engine.sceneOrNull(spec.name) == null) {
        engine.addScene(Scene(spec.name), setCurrent = spec.setCurrent)
    }
}

private fun applySceneSettings(scene: Scene, spec: GameSceneNode) {
    spec.timeScale?.let { scene.timeScale = it }
    spec.camera?.let { camera ->
        camera.position?.let { scene.camera.position = it }
        camera.minZoom?.let { scene.camera.minZoom = it }
        camera.maxZoom?.let { scene.camera.maxZoom = it }
        camera.zoom?.let { scene.camera.setZoomLevel(it) }
    }
    scene.onStart = spec.onEnter ?: {}
    scene.onStop = spec.onExit ?: {}
}

private fun reconcileSystems(scene: Scene, spec: GameSceneNode) {
    val managedIds = spec.systems.map { managedSystemId(it.id) }.toSet()
    val existingManaged = scene.systemsSnapshot().map { it.id }.filter { it.startsWith("__game_system:") }
    existingManaged.filter { it !in managedIds }.forEach(scene::removeSystem)

    spec.systems.forEach { systemNode ->
        scene.upsertSystem(
            SceneSystemSpec(
                id = managedSystemId(systemNode.id),
                system = SceneSystem { frame -> systemNode.update(frame) },
                phase = systemNode.phase,
                order = systemNode.order,
                enabled = systemNode.enabled,
            ),
        )
    }
}

private fun reconcileEntities(scene: Scene, spec: GameSceneNode) {
    val desiredIds = spec.entities.map { it.key }.toSet()
    val trackedManaged = managedEntityIdsByScene.getOrPut(scene) { linkedSetOf() }
    val existingManaged = scene.snapshotEntities().filter { it.id in trackedManaged }

    existingManaged
        .filter { it.id !in desiredIds }
        .forEach {
            scene.removeEntityIfExists(it.id)
            trackedManaged.remove(it.id)
        }

    spec.entities.forEach { node ->
        val existing = scene.entity(node.key)
        val entity = existing ?: Entity(node.key).also(scene::spawn)
        trackedManaged += node.key
        applyEntityNode(entity, node, spec.name)
    }
}

private fun applyEntityNode(
    entity: Entity,
    node: GameEntityNode,
    sceneName: String,
) {
    entity.enabled = node.enabled
    entity.alwaysVisible = node.alwaysVisible

    val metadata = entity.componentOrNull<MetadataComponent>() ?: MetadataComponent()
    metadata.values[GAME_META_KEY] = GameNodeMetadata(managed = true, sceneName = sceneName)
    node.physics?.let { metadata.values["physics"] = it.payload }
    entity.addComponent(metadata)

    node.transform?.let {
        entity.addComponent(
            TransformComponent(
                position = it.position,
                rotationDegrees = it.rotationDegrees,
                scaleX = it.scaleX,
                scaleY = it.scaleY,
            ),
        )
    }

    if (node.tags != null) {
        entity.addComponent(TagsComponent(node.tags.values.toMutableSet()))
    }

    if (node.render != null) {
        entity.addComponent(
            RenderComponent(
                zIndex = node.render.zIndex,
                material = node.render.material,
                renderer = node.render.block,
            ),
        )
    }

    if (node.inputs.isNotEmpty()) {
        val sorted = node.inputs.sortedWith(compareBy<InputNode>({ it.phase.ordinal }, { it.priority }))
        entity.addComponent(InputComponent { _, event -> dispatchInput(sorted, event) })
    }
}

private fun reconcileSceneInput(scene: Scene, spec: GameSceneNode) {
    if (spec.inputs.isEmpty()) {
        scene.onInput = {}
        return
    }
    val sorted = spec.inputs.sortedWith(compareBy<InputNode>({ it.phase.ordinal }, { it.priority }))
    scene.onInput = { event -> dispatchInput(sorted, event) }
}

private fun dispatchInput(
    nodes: List<InputNode>,
    event: EngineInputEvent,
) {
    nodes.forEach { node ->
        when (event) {
            is com.kanvas.fx.core.Tap -> node.onTap?.invoke(event)
            is com.kanvas.fx.core.Drag -> node.onDrag?.invoke(event)
            is com.kanvas.fx.core.KeyDown -> node.onKeyDown?.invoke(event)
            is com.kanvas.fx.core.KeyUp -> node.onKeyUp?.invoke(event)
            is com.kanvas.fx.core.Scroll -> node.onScroll?.invoke(event)
            is com.kanvas.fx.core.PinchZoom -> node.onPinch?.invoke(event)
            else -> Unit
        }
        node.onAny?.invoke(event)
    }
}

private fun managedSystemId(id: String): String = "__game_system:$id"
