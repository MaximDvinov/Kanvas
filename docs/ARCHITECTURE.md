# Kanvas Architecture

## Principles

- `:engine` keeps runtime/render/input abstractions without game-domain rules.
- Extensions (`:enginePhysics`, `:engineGravityBarnesHut`, `:engineWorldObjectsKit`) build on public `:engine` APIs.
- Sample apps validate real usage without polluting core modules.

## Layering

1. Core (`:engine`)
2. Systems/extensions (`:enginePhysics`, `:engineGravityBarnesHut`)
3. Object kits (`:engineWorldObjectsKit`)
4. Applications/samples (`:desktopApp`, `:planetSample:*`, `:flappySample:*`)

## Runtime Model

- `Engine` owns scenes and main update ticks.
- `Scene` owns entities, scene systems, and camera state.
- `Entity` composes behavior through update/render/input callbacks.
- `SceneSystem` runs deterministic frame-level simulation logic.

## Rendering Model

- Compose host (`EngineCanvas`) bridges UI lifecycle and input.
- `Renderer2D` provides primitive drawing API and style abstraction.
- Render effects are pluggable through `RenderEffect` + `ShaderAsset` hooks.

## Input Model

- Pointer and keyboard events are normalized by engine events.
- Screen coordinates are mapped to world coordinates via active scene camera.
- Camera controls are opt-in and scene-local.

## Extension Contract

When extending the engine:
- avoid tight coupling to specific apps;
- expose concise DSL entry points;
- keep platform-specific code outside common source sets whenever possible.
