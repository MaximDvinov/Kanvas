# Contributing to Kanvas

## Workflow

1. Fork the repository.
2. Create a branch from `main` with a focused scope.
3. Run build and relevant sample checks locally.
4. Open a pull request with clear description and testing notes.

## Development Setup

```bash
./gradlew :desktopApp:build
./gradlew :desktopApp:run
```

## Code Style

- Keep engine core (`:engine`) domain-neutral.
- Add domain-specific simulation in extension modules (`SceneSystem` based).
- Keep APIs multiplatform-first (avoid platform-only assumptions in common code).
- Prefer small, composable DSL additions.

## Pull Request Checklist

- Public API changes are documented in `README.md`.
- Behavioral changes are reflected in `CHANGELOG.md` under `Unreleased`.
- New modules or commands are covered in docs.
- Build passes for affected modules.
