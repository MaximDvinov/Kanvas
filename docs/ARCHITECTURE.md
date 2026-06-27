# Kanvas Architecture

## Principles

- `:engine` keeps runtime/render/input abstractions without game-domain rules.
- Extensions (`:enginePhysics`, `:engineGravityBarnesHut`, `:engineWorldObjectsKit`) build on public `:engine` APIs.
- Sample apps validate real usage without polluting core modules.

## Layering

1. Core (`:engine`)
2. Systems/extensions (`:enginePhysics`, `:engineGravityBarnesHut`)
3. Object kits (`:engineWorldObjectsKit`)
4. Applications/samples (`:samples:planetSample:*`, `:samples:flappySample:*`, `:samples:planetMergeSample:*`)

## Runtime Model

- `Engine` owns scenes and main update ticks.
- `Scene` owns entities, scene systems, and camera state.
- `Entity` composes behavior through update/render/input callbacks.
- `SceneSystem` runs deterministic frame-level simulation logic.

## Rendering Model

- Compose host (`EngineCanvas`) bridges UI lifecycle and input.
- `Renderer2D` provides primitive drawing API and style abstraction.
- `RenderStyle` provides world-space gradients plus built-in effects such as blur, glow, bloom, shadow, inner shadow, outline, opacity, and color filter.
- Built-in effects run through a shared pre/post render pipeline, so the same style model applies to primitives and textures.
- Native blur backends exist per platform where available, with fallback rendering kept in common code.
- Custom render effects remain pluggable through `RenderEffect` + `ShaderAsset` hooks.

## Input Model

- Pointer and keyboard events are normalized by engine events.
- Screen coordinates are mapped to world coordinates via active scene camera.
- Camera controls are opt-in and scene-local.

## Extension Contract

When extending the engine:
- avoid tight coupling to specific apps;
- expose concise DSL entry points;
- keep platform-specific code outside common source sets whenever possible.
