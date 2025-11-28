package com.kanvas.fx.compose

import android.app.Activity
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanvas.fx.core.Engine
import com.kanvas.fx.render.AssetRegistry

/**
 * Default Android behavior for engine lifecycle binding.
 *
 * - retains engine instance across configuration changes;
 * - starts/resumes engine when screen starts;
 * - stops engine when screen stops.
 */
data class AndroidEngineLifecycleConfig(
    val startOnStart: Boolean = true,
    val stopOnStop: Boolean = true,
    val clearOnDestroy: Boolean = true,
)

/**
 * Returns engine retained in ViewModel by [key], so rotation does not recreate game runtime.
 */
@Composable
fun rememberRetainedEngine(
    key: String = "default-engine",
    factory: () -> Engine,
): Engine {
    require(key.isNotBlank()) { "Engine retention key must not be blank." }
    val store = viewModel<EngineStoreViewModel>()
    return remember(store, key) { store.getOrPut(key, factory) }
}

/**
 * Android default setup:
 * - retains engine by [key];
 * - binds it to lifecycle with [config].
 */
@Composable
fun rememberManagedEngine(
    key: String = "default-engine",
    config: AndroidEngineLifecycleConfig = AndroidEngineLifecycleConfig(),
    factory: () -> Engine,
): Engine {
    val engine = rememberRetainedEngine(key = key, factory = factory)
    BindEngineToLifecycle(
        engine = engine,
        key = key,
        config = config,
    )
    return engine
}

/**
 * Binds [engine] to current Android LifecycleOwner.
 */
@Composable
fun BindEngineToLifecycle(
    engine: Engine,
    key: String = "default-engine",
    config: AndroidEngineLifecycleConfig = AndroidEngineLifecycleConfig(),
) {
    val owner = LocalLifecycleOwner.current
    val store = viewModel<EngineStoreViewModel>()

    DisposableEffect(owner, engine, key, config, store) {
        val lifecycle = owner.lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (config.startOnStart) {
                    engine.resume()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                if (config.stopOnStop) {
                    engine.stop()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (!config.clearOnDestroy) return
                val asActivity = owner as? Activity
                if (asActivity?.isChangingConfigurations == true) return
                store.remove(key)
            }
        }
        lifecycle.addObserver(observer)

        // Ensure immediate start when composable appears in an already-started lifecycle.
        if (config.startOnStart && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            engine.resume()
        }

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Android default host:
 * - retains engine across configuration changes;
 * - binds lifecycle start/stop automatically;
 * - renders [EngineCanvas].
 */
@Composable
fun ManagedEngineCanvas(
    key: String = "default-engine",
    modifier: Modifier = Modifier,
    assets: AssetRegistry = remember { AssetRegistry() },
    focusRequestKey: Int = 0,
    lifecycleConfig: AndroidEngineLifecycleConfig = AndroidEngineLifecycleConfig(),
    engineFactory: () -> Engine,
) {
    val engine = rememberManagedEngine(
        key = key,
        config = lifecycleConfig,
        factory = engineFactory,
    )
    EngineCanvas(
        engine = engine,
        modifier = modifier,
        assets = assets,
        focusRequestKey = focusRequestKey,
    )
}

internal class EngineStoreViewModel : ViewModel() {
    private val engines = linkedMapOf<String, Engine>()

    fun getOrPut(
        key: String,
        factory: () -> Engine,
    ): Engine = engines.getOrPut(key, factory)

    fun remove(key: String) {
        engines.remove(key)?.stop()
    }

    override fun onCleared() {
        engines.values.forEach { it.stop() }
        engines.clear()
    }
}
