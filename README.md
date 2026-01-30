# Kanvas

Kanvas is a Compose Multiplatform-based 2D game engine foundation with:

- a runtime update loop,
- scene/object lifecycle,
- input callbacks,
- camera-aware world rendering,
- rendering through a dedicated Compose canvas host,
- and a Kotlin DSL for engine setup.

The engine core is intentionally domain-neutral. Any game-specific logic (gravity model variants, atmosphere effects, gameplay rules, world archetypes) should live in extensions or app-side code.

## Modules

- `:engine` - engine core, DSL, render facade, Compose host
- `:enginePhysics` - optional classic physics extension (rigid bodies, colliders, collisions)
- `:engineGravityBarnesHut` - optional astrophysics extension (Barnes-Hut n-body gravity)
- `:engineWorldObjectsKit` - optional example module with classic game object archetypes
- `:desktopApp` - sample desktop apps (gravity simulation, platformer, collision playground)
- `:samplePlanetsApp` - multiplatform planets sample app (common UI + desktop entry + iOS `MainViewController`)

## Core API

### Runtime

- `Engine` - manages scenes, frame ticks, scene switching, and input dispatch.
- `Scene` - owns `Entity` instances, scene callbacks, camera, visibility, and scene systems.
- `Entity` - callback/component-driven object with transform and render/input hooks.
- `SceneSystem` - scene-level simulation unit executed once per frame.
- `Camera2D` - scene camera (`position`, `zoom`, `zoomRange`) used by render and input mapping.
- `EngineConfig` - includes fixed timestep configuration.

### Input Events

The engine emits unified events:

- `Tap`
- `PointerDown`, `PointerMove`, `PointerUp`
- `DragStart`, `Drag`, `DragEnd`
- `Scroll`
- `KeyDown`, `KeyUp`

`EngineCanvas` converts pointer input from screen-space to world-space using the active scene camera.

### Rendering

- `EngineCanvas(...)` - Compose rendering surface with built-in frame loop and pointer-to-engine input bridge.
- `Renderer2D` - draw facade:
  - `circle`, `line`, `rect`
  - `polygon(points, color, ...)`
  - `texture(textureId, topLeft, size, tint)`
  - helper methods: `vector(...)`, `trail(...)`
- World rendering is camera-aware (pan/zoom).
- Scene supports optional bounds-based culling and per-frame render metrics.

### Universal Render Style

All draw primitives accept `RenderStyle`. This allows attaching visual behavior without hardcoding effects into primitive-specific APIs.

`RenderStyle` supports:

- fill color
- linear gradient
- stroke
- glow
- texture tint
- shader/effect id
- effect phase control (`beforeDraw`, `afterDraw`)

Use style DSL from `Renderer2D`:

```kotlin
render {
    val s = style {
        linearGradient(
            startColor = Color(0xFF3BCBFF),
            endColor = Color(0xFF9A79FF),
            start = Offset(-100f, 0f),
            end = Offset(100f, 0f),
        )
        stroke(color = Color.White.copy(alpha = 0.35f), width = 2f)
        glow(alpha = 0.45f, widthMultiplier = 2.2f)
    }

    circle(
        center = Offset(0f, 0f),
        radius = 42f,
        color = Color.White,
        style = s,
    )
}
```

### Camera Controls

Camera controls are pluggable and scene-scoped:

- `KeyboardCameraControl`
- `GestureCameraControl`

DSL presets:

- `camera { keyboardDefaults() }`
- `camera { gestureDefaults() }`
- `camera { desktopDefaults() }`
- `camera { touchDefaults() }`

### Assets

- `AssetRegistry` stores:
  - `TextureAsset` (`Path` or `Bitmap` source),
  - `ShaderAsset` (`Text` or `ExternalDsl` source).

`ExternalDsl` allows integrating third-party shader DSL objects (for example redbytefx definitions) without coupling engine core to a specific shader library.

#### Why registration exists

Asset registration gives:

- stable ids in scene logic,
- reuse and caching,
- centralized lifecycle,
- platform-specific loading behind a shared API,
- easy resource swapping by id.

#### Lazy texture loading

`AssetRegistry` supports path-based registration with deferred bitmap resolution:

```kotlin
val assets = AssetRegistry().apply {
    addTextureResolver(myResolver) // platform-side
    registerTexture("planet-00", "/textures/planets/planet00.png")
}
```

At first render use, `TextureSource.Path` is resolved to `TextureSource.Bitmap` and cached.

Desktop sample includes `enableDesktopTextureAutoResolve()` to resolve classpath resources and files.

#### Multiplatform resource loading (default + custom)

`AssetRegistry` now has a pluggable resource API:

- `addResourceResolver(...)`
- `setResourceResolvers(...)`
- `restoreDefaultResourceResolvers()`
- `addImageDecoder(...)`
- `setImageDecoders(...)`
- `restoreDefaultImageDecoders()`

By default, Kanvas installs a standard multiplatform resolver/decoder chain:

- JVM: classpath resource lookup + file fallback
- iOS: main bundle lookup

You can override this to plug any custom storage (CDN cache, encrypted blobs, platform FS adapters) without changing engine API.

### Render Effects and Shader Hooks

Engine provides a generic effect hook instead of concrete built-in game effects.

Core types:

- `RenderEffect`
- `RenderPhase` (`Pre`, `Post`)
- `PrimitiveKind` (`Circle`, `Line`, `Rect`, `Polygon`, `Texture`)
- `PrimitiveGeometry` (screen-space geometry payload for the primitive)

Attach effect via `ShaderAsset(id, ShaderSource.ExternalDsl(effect))` and reference it in style:

```kotlin
style {
    shader("planet-atmosphere")
    effectPhase(beforeDraw = false, afterDraw = true)
}
```

Example effect implementation:

```kotlin
val atmosphere = RenderEffect { drawScope, phase, primitive, geometry ->
    if (phase != RenderPhase.Post || primitive != PrimitiveKind.Texture) return@RenderEffect
    val g = geometry as? PrimitiveGeometry.Texture ?: return@RenderEffect
    // custom drawScope pass here
}
```

### Physics (Optional Extension)

- module: `:enginePhysics`
- `PhysicsWorld2D` with:
  - colliders: `Collider2D.Circle`, `Collider2D.Aabb`, `Collider2D.Polygon` (convex)
  - rigid body model: `PhysicsBody2D`
  - collision callbacks: `onCollisionEnter`, `onCollisionStay`, `onCollisionExit`
  - DSL hook: `systems { physics2d(world) }`
  - presets: `physicsWorld2dUniformGrid(...)`, `physicsWorld2dNoBroadphase(...)`

### Gravity (Optional Extension)

- module: `:engineGravityBarnesHut`
- `GravityBody` and `BarnesHutGravitySystem`
- DSL hook: `systems { gravityBarnesHut(...) }`

The core engine stays domain-neutral. Physics behavior is added via scene systems:

```kotlin
systems {
    add(MyCustomPhysicsSystem())
}
```

Platformer-oriented object setup example:

```kotlin
import com.kanvas.fx.worldkit.classicObjects2d

val world = PhysicsWorld2D(gravity = Offset(0f, 900f))

scene("level", setCurrent = true) {
    visibility {
        enabled(true)
        debugOverlay(false)
    }
    renderQuality {
        effectsEnabled(true)
        maxLightsPerObject(4)
        glowQuality(1f)
    }
    systems { physics2d(world) }
    val objects = classicObjects2d(world)

    objects.floor("ground", center = Offset(0f, 280f), width = 900f)
    objects.wall("left-wall", center = Offset(-450f, 0f), height = 800f)
    objects.wall("right-wall", center = Offset(450f, 0f), height = 800f)
    objects.platform("plat-1", center = Offset(0f, 160f), width = 220f)
    objects.crate("crate-1", center = Offset(80f, 80f))

    val player = objects.player("hero", center = Offset(0f, 120f))
}
```

## DSL Example

```kotlin
import com.kanvas.fx.gravity.GravityBody
import com.kanvas.fx.gravity.gravityBarnesHut

val gameEngine = engine {
    texture("planet", "/textures/planet.png")
    shader("warp", "/* shader code */")

    scene("main", setCurrent = true) {
        camera {
            position(0f, 0f)
            zoom(1f)
            zoomRange(min = 0.2f, max = 6f)
            desktopDefaults()
        }

        onUpdate { frame ->
            // scene-level simulation code
        }

        onInput { event ->
            // scene-level input reactions
        }

        systems {
            gravityBarnesHut(
                bodiesProvider = { gravityBodies },
            )
        }

        val playerBody = GravityBody(
            id = "player-body",
            position = Offset(120f, 80f),
            velocity = Offset.Zero,
            mass = 100f,
        )

        entities {
            entity("player") {
                render(zIndex = 10) { _ ->
                    circle(
                        center = playerBody.position,
                        radius = 16f,
                        color = Color.Cyan,
                        style = style {
                            glow(alpha = 0.35f, widthMultiplier = 2f)
                        },
                    )
                }
                bounds {
                    circle(centerX = 120f, centerY = 80f, radius = 18f)
                }
                onUpdate { _ ->
                    // sync runtime state with playerBody
                }
            }
        }
    }
}
```

## Input and Gesture Notes

- `Tap` is emitted separately from drag lifecycle.
- `DragStart`/`Drag`/`DragEnd` carry world-space coordinates.
- `EngineCanvas` maps screen coordinates to world using active camera (`position`, `zoom`).
- Camera controls are scene-local and composable (`keyboardDefaults`, `gestureDefaults`, custom controls).

## Extending Physics

The recommended approach is to keep custom simulation out of `:engine` and implement it as `SceneSystem` modules:

```kotlin
class MyBuoyancySystem : SceneSystem {
    override fun update(frame: FrameContext) {
        // integrate custom behavior
    }
}

scene("main") {
    systems {
        add(MyBuoyancySystem())
    }
}
```

`enginePhysics` and `engineGravityBarnesHut` are examples of this extension pattern.

## Render Host Usage

```kotlin
EngineCanvas(
    engine = gameEngine,
    modifier = Modifier.fillMaxSize(),
)
```

## Build

```bash
./gradlew :desktopApp:build
```

Run sample:

```bash
./gradlew :desktopApp:run
```

Run collision + physics playground:

```bash
./gradlew :desktopApp:runCollisionDemo
```

Run platformer demo:

```bash
./gradlew :desktopApp:runPlatformerDemo
```

Run multiplatform planets sample (desktop):

```bash
./gradlew :samplePlanetsApp:run
```

## Repository Readiness

This repository is prepared for public GitHub usage with:

- stable module structure;
- sample apps for quick validation;
- contribution guides and issue/PR templates;
- CI workflow for pull requests and `main` branch builds.

## Release Checklist

1. Ensure build is green:
```bash
./gradlew build
```
2. Update `CHANGELOG.md` (`Unreleased` -> release version/date).
3. Tag release:
```bash
git tag v0.1.0
git push origin v0.1.0
```

## Contributing

See `CONTRIBUTING.md` for coding, PR, and review flow.

## License

MIT. See `LICENSE`.
