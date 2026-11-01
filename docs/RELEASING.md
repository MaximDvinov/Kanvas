# Releasing Kanvas

The release includes these Maven coordinates under `io.github.maximdvinov`:

- `engine`
- `engineComposeDsl`
- `enginePhysics`
- `engineGravityBarnesHut`
- `engineWorldObjectsKit`

Each module includes Android, JVM, iOS arm64, iOS simulator arm64, and Wasm JS publications. Releases use the [Vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) and the Sonatype Central Portal. GitHub Packages is a best-effort secondary destination.

## Prerequisites

Register and verify the `io.github.maximdvinov` namespace in the Central Portal, generate a user token, and publish the signing key's public half to a key server. Configure these GitHub repository secrets:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: Central Portal user token credentials.
- `SIGNING_KEY_BASE64`: base64-encoded ASCII-armored private PGP key.
- `SIGNING_PASSWORD`: key passphrase.

Keep the private key and token out of the repository. Use a macOS host with Xcode, Android SDK 37.1, and JDK 17 and 21 available to build all release targets.

## Local checks

```bash
./gradlew :engine:jvmTest :enginePhysics:jvmTest :engineComposeDsl:jvmTest \
  :engine:testAndroidHostTest :enginePhysics:testAndroidHostTest
./gradlew publishAllPublicationsToLocalBuildRepository
./gradlew -p tests/publication-consumer compileKotlin
```

The local repository is `build/local-maven`. Local publication does not require a signing key; Central publication does. Check the generated POM and Gradle module metadata, including target variants and inter-module dependencies, before releasing.

## Release

1. Select a new version that is not already published. Set `VERSION_NAME` in `gradle.properties`, finalize the changelog, and run the checks on the exact commit to be released.
2. Review the version, changelog, commit, artifact coordinates, and validation results before publishing.
3. Push the matching tag, for example `v1.2.3` for `VERSION_NAME=1.2.3`. Do not push all local tags. The Publish workflow rejects a tag that does not match the version.
4. The workflow validates the changelog, checks tests and local publications, signs and uploads the artifacts to Central Portal, and waits for publication. It then attempts GitHub Packages independently and creates a GitHub Release using the changelog notes. Tags containing a hyphen are marked as prereleases. A manual workflow run on a branch only runs checks.
5. Verify every module and platform artifact resolves from Maven Central, and check the Central Portal deployment, workflow, and GitHub Release.
6. Start the next development version and add an `Unreleased` changelog section.

Maven Central releases are immutable. If publication is partial or its result is uncertain, inspect the Portal deployment and published coordinates before taking another action; do not rerun the upload blindly.

## Local Central publication

Use only as an alternative to the tag-triggered workflow. Provide the plugin's standard `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey`, and `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` environment variables securely. After the same review and checks, run:

```bash
./gradlew publishAndReleaseToMavenCentral -PmavenCentralDeploymentValidation=PUBLISHED
```

Consult the [plugin's Central Portal guide](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) for token and signing setup.
