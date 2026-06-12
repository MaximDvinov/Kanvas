# Flappy Example (Compose-Native)

This document describes the recommended pattern used by the Flappy sample:

`Input -> Action -> Store -> ReducerSystem -> RenderSync`

## Benefits

- Game logic reads as domain rules instead of mutable scene operations.
- Physics and rules can be tested separately from rendering.
- The Compose API remains declarative (`GameHost { Scene { ... } }`).

## Main Parts

### 1. Domain State + Reducer

File: `samples/flappySample/shared/src/commonMain/kotlin/com/kanvas/fx/flappySample/domain/FlappyReducer.kt`

- `FlappyState` is the single source of truth for the runtime.
- `FlappyAction` represents user intents such as `Flap` and `Restart`.
- `FlappyReducer.reduceAction(...)` is pure input-intent handling.
- `FlappyReducer.step(...)` is a pure simulation step based on `deltaTimeSeconds`.

This layer does not depend on `Scene`, `Entity`, or `Renderer`.

### 2. Store + ReducerSystem

File: `samples/flappySample/shared/src/commonMain/kotlin/com/kanvas/fx/flappySample/engine/FlappyRuntime.kt`

- `GameStore<FlappyState, FlappyAction>` stores state and a pending action queue.
- `ReducerSystem(...)` inside `Scene`:
  1. applies pending actions,
  2. runs the frame step,
  3. commits the next state,
  4. publishes UI state.

### 3. Input Mapping

`FlappyInputSystem` maps `EngineInputEvent` values to `FlappyAction`.

- `Tap/Drag/Space/W/Up` -> `Flap`
- `R` -> `Restart`

This keeps input policy in one place.

### 4. Render Sync

`FlappyRenderSyncSystem` receives `FlappyState` and synchronizes entities:

- creates, updates, and removes pipe entities,
- updates the bird position,
- moves the ground,
- updates animated tilt.

Rendering should not make gameplay decisions. It should only display state.

### 5. Lifecycle

The `Scene` uses lifecycle hooks:

- `onEnter { ... }` initializes scene runtime objects.
- `onExit { ... }` cleans up temporary entities.

This replaces lazy initialization inside the update loop.

## Canonical Scaffold

```kotlin
GameHost(engine = runtime.engine, assets = runtime.assets) {
    Scene("main", setCurrent = true) {
        onEnter { engine -> /* setup */ }
        onExit { engine -> /* cleanup */ }

        input(onAny = world::onInput)

        ReducerSystem<MyState, MyAction>(
            id = "world",
            store = world.store,
            onFrame = world::onFrame,
            onStateCommitted = world::onStateCommitted,
        )

        System(id = "animation", phase = SystemPhase.RenderPrep) { frame ->
            animationSystem.update(frame)
        }
    }
}
```

## Recommendations for New Games

- Keep gameplay logic in reducer/state only.
- Keep `RenderSync` as a thin state-to-entities adapter.
- Use `Collider2DComponent` and `Collision2D` for simple arcade collisions.
- Avoid raw strings for entity lookup when possible; prefer typed keys or typed queries.

## Related Files

- `samples/flappySample/shared/src/commonMain/kotlin/com/kanvas/fx/flappySample/ui/FlappyGameApp.kt`
- `samples/flappySample/shared/src/commonMain/kotlin/com/kanvas/fx/flappySample/engine/FlappyRuntime.kt`
- `samples/flappySample/shared/src/commonMain/kotlin/com/kanvas/fx/flappySample/domain/FlappyReducer.kt`
