# Releasing Kanvas

## 1. Preflight

```bash
./gradlew build
```

## 2. Update Changelog

Move `Unreleased` notes in `CHANGELOG.md` into a version section:

```md
## [0.1.0] - YYYY-MM-DD
```

## 3. Tag and Push

```bash
git tag v0.1.0
git push origin main --tags
```

## 4. GitHub Release

Create release notes from the version section and reference key module updates.

## 5. Post-release

Create a fresh `Unreleased` section in `CHANGELOG.md` for next iteration.
