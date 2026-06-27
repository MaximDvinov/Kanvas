# Releasing Kanvas

This project publishes the public library modules:

- `io.github.maximdvinov:engine`
- `io.github.maximdvinov:enginePhysics`
- `io.github.maximdvinov:engineGravityBarnesHut`
- `io.github.maximdvinov:engineWorldObjectsKit`

## Distribution Targets

Maven Central is the primary public distribution target. GitHub Packages is kept as a secondary registry and must not block Central publishing.

Maven Central publishing uses Sonatype Central Portal through the OSSRH Staging compatibility endpoint:

```text
https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/
```

After Gradle uploads the Maven artifacts, CI calls the Central Portal manual upload endpoint with automatic publishing enabled.

## Required Sonatype Setup

1. Create or sign in to a Sonatype Central Portal account.
2. Create and verify the namespace for `io.github.maximdvinov`.
3. Generate a Central Portal user token.
4. Create a PGP key for artifact signing.

Maven Central and GitHub Packages releases are immutable. If a bad version is released, publish a new version instead of trying to overwrite it.

## Required GitHub Secrets

Add these repository secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_KEY_BASE64
SIGNING_PASSWORD
```

`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` must be the Central Portal token username and token password, not the account password.

`SIGNING_KEY_BASE64` must contain the base64-encoded ASCII-armored private PGP key. `SIGNING_PASSWORD` is the key passphrase.

Generate `SIGNING_KEY_BASE64` locally:

```bash
gpg --batch --pinentry-mode loopback \
  --passphrase "your-key-passphrase" \
  --armor --export-secret-keys <KEY_ID> | base64 | tr -d '\n'
```

Plain multiline `SIGNING_KEY` is still supported for local use, but `SIGNING_KEY_BASE64` is preferred in GitHub Actions because it avoids broken multiline secret formatting.

Optional repository variable:

```text
MAVEN_CENTRAL_NAMESPACE=io.github.maximdvinov
```

## Local Preflight

Run tests for the main library modules:

```bash
./gradlew :engine:jvmTest :enginePhysics:jvmTest
```

Verify all publications locally:

```bash
./gradlew publishAllPublicationsToLocalBuildRepository
```

Artifacts are written to:

```text
build/local-maven
```

## Signed Local Verification

To verify signing without publishing remotely:

```bash
export SIGNING_KEY="$(cat private-key.asc)"
export SIGNING_PASSWORD="your-key-passphrase"

./gradlew publishAllPublicationsToLocalBuildRepository
```

Signing is required for Maven Central publishing tasks and optional for local publication tasks.

## Release Version

The published version is controlled by `VERSION_NAME` in `gradle.properties`:

```properties
VERSION_NAME=0.2.0-alpha
```

Before publishing, move changelog entries from `Unreleased` into a dated version section:

```md
## [0.2.0-alpha] - YYYY-MM-DD
```

## Publish from GitHub Actions

Create and push a version tag:

```bash
git tag v0.2.0-alpha
git push origin main --tags
```

The `Publish` workflow will:

1. Run JVM tests for the core library modules.
2. Verify local Maven publications.
3. Publish signed artifacts to Maven Central staging.
4. Upload the staging repository into the Central Portal and publish automatically after validation.
5. Publish artifacts to GitHub Packages as a best-effort secondary registry.

After the workflow completes, verify the deployment status in the Central Portal.

## Manual Maven Central Publish

Use this only if GitHub Actions is unavailable:

```bash
export MAVEN_CENTRAL_USERNAME="token-username"
export MAVEN_CENTRAL_PASSWORD="token-password"
export SIGNING_KEY_BASE64="$(base64 < private-key.asc | tr -d '\n')"
export SIGNING_PASSWORD="your-key-passphrase"

./gradlew publishAllPublicationsToMavenCentralStagingRepository
```

Then upload the staging repository into the Central Portal:

```bash
token="$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"

curl --fail-with-body \
  -X POST \
  -H "Authorization: Bearer $token" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.maximdvinov?publishing_type=automatic"
```

## Post-release

1. Verify the artifacts resolve from `mavenCentral()`.
2. Create a GitHub Release from the changelog section.
3. Add a fresh `Unreleased` section to `CHANGELOG.md`.
4. Bump `VERSION_NAME` for the next development cycle if needed.

## References

- Sonatype Central Portal registration: `https://central.sonatype.org/register/central-portal/`
- Sonatype publishing requirements: `https://central.sonatype.org/publish/requirements/`
- Sonatype OSSRH Staging compatibility API: `https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/`
