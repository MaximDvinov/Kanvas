# Kanvas

Kanvas is a Kotlin Multiplatform 2D runtime for Compose applications.
It provides a small engine layer for scenes, entities, systems, rendering, camera control, assets, and normalized input events.

The engine stays domain-neutral. Game rules, simulations, AI, economy, and app-specific behavior are expected to live in your own `SceneSystem` implementations or in optional extension modules.

## Status

Current version: `0.2.0-alpha`

Supported targets:

- Android
- JVM desktop
- Wasm JS browser
- iOS arm64
- iOS simulator arm64

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| `:engine` | `io.github.maximdvinov:engine` | Core runtime, scene model, renderer API, assets, input, and Compose host. |
| `:enginePhysics` | `io.github.maximdvinov:enginePhysics` | Optional 2D physics primitives, broadphase, collision, and physics DSL. |
| `:engineGravityBarnesHut` | `io.github.maximdvinov:engineGravityBarnesHut` | Optional Barnes-Hut n-body gravity simulation. |
| `:engineWorldObjectsKit` | `io.github.maximdvinov:engineWorldObjectsKit` | Reusable templates for common 2D world objects. |

## Installation

After the first Maven Central release, consumers only need `mavenCentral()`:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("io.github.maximdvinov:engine:0.2.0-alpha")
}
```

Optional extensions:

```kotlin
dependencies {
    implementation("io.github.maximdvinov:enginePhysics:0.2.0-alpha")
    implementation("io.github.maximdvinov:engineGravityBarnesHut:0.2.0-alpha")
    implementation("io.github.maximdvinov:engineWorldObjectsKit:0.2.0-alpha")
}
```

GitHub Packages is also configured as a secondary publishing target:

```kotlin
repositories {
    maven("https://maven.pkg.github.com/MaximDvinov/Kanvas")
}
```

GitHub Packages may require GitHub credentials for dependency resolution. Maven Central is the recommended public distribution channel.

## Quick Start

Use `GameHost` when building a Compose application:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kanvas.fx.core.Engine
import com.kanvas.fx.game.Entity
import com.kanvas.fx.game.GameHost
import com.kanvas.fx.game.Render
import com.kanvas.fx.game.Scene
import com.kanvas.fx.game.System
import com.kanvas.fx.game.Transform

@Composable
fun App() {
    val engine = remember { Engine() }

    GameHost(
        engine = engine,
        modifier = Modifier.fillMaxSize()
    ) {
        Scene("main", setCurrent = true) {
            System(id = "movement") { frame ->
                // Update simulation state here.
            }

            Entity("player") {
                Transform(position = Offset(120f, 180f))
                Render(zIndex = 10) { entity ->
                    circle(
                        center = entity.position,
                        radius = 24f,
                        color = Color(0xFF4EA1FF)
                    )
                }
            }
        }
    }
}
```

For lower-level or advanced use cases, you can create an `Engine` directly and host it with `EngineCanvas`:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kanvas.fx.compose.EngineCanvas
import com.kanvas.fx.dsl.engine

@Composable
fun App() {
    val game = remember {
        engine {
            scene("main", setCurrent = true) {
                onUpdate { frame ->
                    // Imperative update logic.
                }

                entities {
                    entity("player") {
                        // Configure update, render, and input callbacks.
                    }
                }
            }
        }
    }

    EngineCanvas(engine = game)
}
```

## Core Concepts

### Engine

`Engine` is the runtime container. It owns registered scenes, tracks the active scene, dispatches input, and advances simulation through `tick(deltaSeconds)`.

### Scene

`Scene` is an isolated 2D space. It owns entities, scene systems, camera state, lifecycle callbacks, update callbacks, and input handlers.

### Entity

`Entity` represents an object in a scene. Entities can render, receive input, and participate in update logic. They are intentionally lightweight so you can model game objects, particles, UI world objects, or simulation bodies.

### SceneSystem

`SceneSystem` is the recommended extension point for domain logic. A physics step, gravity simulation, enemy AI, procedural spawning, or animation controller can all be implemented as systems.

### Renderer2D

`Renderer2D` exposes primitives such as circles, lines, rectangles, polygons, textures, and materials. Rendering is camera-aware when hosted through `EngineCanvas`.

### RenderStyle and Effects

`RenderStyle` now supports world-space gradients and composable built-in effects without custom shaders.

```kotlin
val style = style {
    radialGradient(
        colors = listOf(Color.White, Color(0xFFFF8A65), Color(0xFFFF5252)),
        center = Offset(160f, 120f),
        radius = 64f,
    )
    glow(color = Color(0xFFFFC107), alpha = 0.45f, radius = 18f)
    bloom(color = Color(0xFFFF5252), alpha = 0.22f, radius = 28f)
    shadow(color = Color.Black, alpha = 0.35f, radius = 12f, offset = Offset(6f, 8f))
    outline(color = Color.White, width = 2f)
}
```

Available gradient helpers:

- `linearGradient(...)`
- `radialGradient(...)`
- `sweepGradient(...)`

Available built-in effects:

- `glow(...)`, `bloom(...)`, `blur(...)`
- `shadow(...)`, `innerShadow(...)`, `outline(...)`
- `opacity(...)`, `colorFilter(...)`

Native blur is implemented per platform where possible, with fallback behavior retained for unsupported cases.

### Camera2D

`Camera2D` controls world position, zoom, and zoom limits. Pointer input is mapped from screen-space to world-space through the active scene camera.

### Assets

`AssetRegistry` stores platform-neutral asset IDs for textures and shaders. Assets can be backed by paths, bitmaps, shader text, or external shader DSL entries.

## Input

`EngineInputEvent` normalizes pointer, drag, zoom, scroll, and keyboard events across supported platforms:

- `PointerDown`, `PointerMove`, `PointerUp`, `Tap`
- `DragStart`, `Drag`, `DragEnd`
- `PinchZoom`, `Scroll`
- `KeyDown`, `KeyUp`

Input handlers receive coordinates already mapped into the engine model where applicable.

## Physics Example

Add the physics module:

```kotlin
dependencies {
    implementation("io.github.maximdvinov:enginePhysics:0.2.0-alpha")
}
```

Then attach physics as scene-level logic:

```kotlin
Scene("level", setCurrent = true) {
    System(id = "physics") { frame ->
        // Step your physics world with frame.deltaSeconds.
    }

    Entity("ball") {
        Transform(position = Offset(100f, 100f))
        Render { entity ->
            circle(
                center = entity.position,
                radius = 16f,
                color = Color(0xFF67D391)
            )
        }
    }
}
```

## Samples

The repository includes several sample applications:

- `samples/planetSample` (`:samples:planetSample:*`) - multiplatform planet scene sample.
- `samples/flappySample` (`:samples:flappySample:*`) - Compose game sample.
- `samples/effectsShowcase` (`:samples:effectsShowcase:desktop`) - desktop showcase for layered effects, textures, and sprite animation.
- `samples/planetMergeSample` (`:samples:planetMergeSample:*`) - multiplatform merge-style game sample.
- `:docsSite` and `:docsShowcase` - documentation site and focused browser showcase examples.

Useful commands:

```bash
./gradlew :samples:planetSample:desktop:run
./gradlew :samples:effectsShowcase:desktop:run
./gradlew :engine:jvmTest :enginePhysics:jvmTest
./gradlew publishAllPublicationsToLocalBuildRepository
```

## Publishing

Library modules are configured for:

- Maven Central staging via Sonatype Central Portal OSSRH compatibility API.
- GitHub Packages as a secondary package registry.
- Local verification through `build/local-maven`.

See [docs/RELEASING.md](docs/RELEASING.md) for the release checklist, required secrets, and publishing commands.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Flappy example](docs/FLAPPY_EXAMPLE.md)
- [Releasing](docs/RELEASING.md)
- [Changelog](CHANGELOG.md)
- [Asset sources](ASSETS_SOURCES.md)

## License

Kanvas is available under the [MIT License](LICENSE).
