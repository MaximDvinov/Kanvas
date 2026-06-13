package com.kanvas.fx.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.core.Engine
import com.kanvas.fx.render.AssetRegistry
import kotlinx.coroutines.CoroutineScope

@Stable
class GameSceneController internal constructor(
    private val runtime: GameRuntimeContext,
) {
    fun commands(sceneName: String? = null): GameCommands = runtime.commands(sceneName)
    fun switchScene(name: String): Boolean = runtime.commands().switchScene(name)
}

@Composable
fun GameHost(
    engine: Engine,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    assets: AssetRegistry = remember { AssetRegistry() },
    currentScene: String? = null,
    content: GameHostScope.() -> Unit,
) {
    var runtime by remember(engine) { mutableStateOf(GameRuntimeContext(engine)) }
    val graph = remember(currentScene, content) {
        GameHostScope().apply(content).build(currentScene)
    }

    LaunchedEffect(engine, graph) {
        runtime = reconcileGameGraph(engine, graph)
    }

    EngineCanvas(
        engine = engine,
        modifier = modifier,
        assets = assets,
    )
}

@Composable
fun rememberSceneController(engine: Engine): GameSceneController {
    val runtime = remember(engine) { GameRuntimeContext(engine) }
    return remember(runtime) { GameSceneController(runtime) }
}

@Composable
fun rememberEntityHandle(
    engine: Engine,
    id: String,
    sceneName: String? = null,
): (() -> com.kanvas.fx.core.Entity?) {
    val runtime = remember(engine) { GameRuntimeContext(engine) }
    return remember(runtime, id, sceneName) {
        {
            val scene = sceneName?.let(runtime::sceneOrNull) ?: runtime.currentScene
            scene?.entity(id)
        }
    }
}

@Composable
fun LaunchedGameEffect(
    engine: Engine,
    key: Any? = Unit,
    block: suspend CoroutineScope.(GameRuntimeContext) -> Unit,
) {
    val runtime = remember(engine) { GameRuntimeContext(engine) }
    LaunchedEffect(engine, key) {
        block(runtime)
    }
}

@Composable
fun DisposableGameEffect(
    engine: Engine,
    key: Any? = Unit,
    onStart: (GameRuntimeContext) -> Unit = {},
    onDispose: (GameRuntimeContext) -> Unit,
) {
    val runtime = remember(engine) { GameRuntimeContext(engine) }
    DisposableEffect(engine, key) {
        onStart(runtime)
        onDispose {
            onDispose(runtime)
        }
    }
}
