# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

## [0.2.0-alpha] - 2026-06-27

### Added
- Built-in effect pipeline for `blur`, `shadow`, `innerShadow`, `outline`, `bloom`, `opacity`, and `colorFilter`.
- Extended gradient support with `linear`, `radial`, and `sweep` variants in `RenderStyle`.
- Dedicated desktop showcase module at `:samples:effectsShowcase:desktop` with combined effects, textures, and sprite animation examples.
- Cross-platform native blur backends for JVM/Desktop, Android, iOS, and Wasm with fallback behavior preserved.

### Changed
- Updated renderer effect execution to support reusable pre/post effect composition across primitives and textures.
- Expanded material effects with built-in `Blur`, `Shadow`, and `Outline` kinds.
- Improved sample coverage to demonstrate layered effects, animated sprites, and textured rendering in one showcase.

### Added
- Public repository baseline docs and community files.
- CI workflow for pull requests and `main` branch.
- GitHub Packages publishing configuration for the Kanvas library modules.
- Maven Central staging publishing configuration with PGP signing support.
- Publish workflow for tagged releases starting with `v0.1.0-alpha.1`.
- Issue and pull request templates.
- Release process notes.
- English README with installation, module overview, concepts, and examples.
