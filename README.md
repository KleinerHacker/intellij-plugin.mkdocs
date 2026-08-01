# MkDocs – JetBrains Plugin

[![CI](https://github.com/KleinerHacker/intellij-plugin.mkdocs/actions/workflows/ci.yml/badge.svg)](https://github.com/KleinerHacker/intellij-plugin.mkdocs/actions/workflows/ci.yml)

Support for [MkDocs](https://www.mkdocs.org) projects in all IntelliJ-platform IDEs.

📖 **Documentation:** <https://kleinerhacker.github.io/intellij-plugin.mkdocs/>

## Project structure

Single-project Gradle build — the root project *is* the publishable plugin.

```
.
├── .github/                GitHub workflows, issue templates and Dependabot configuration
├── .run/                   Predefined Run/Debug configurations
├── docs/                   MkDocs documentation site (mkdocs.yml + docs/)
├── gradle/
│   ├── wrapper/            Gradle wrapper
│   ├── libs.versions.toml  Version catalog — the single source of truth for versions
│   └── test-logging.properties  JUL config that silences platform-test logging
├── src/main
│   ├── kotlin/             Kotlin production sources
│   └── resources/
│       └── META-INF/       plugin.xml and plugin logo
├── build.gradle.kts        The entire build: plugin, quality gates and docs tasks
├── gradle.properties       Gradle configuration properties
├── settings.gradle.kts     Gradle project settings
└── CHANGELOG.md            Release notes — the release workflow reads them from here
```

## Target platform

| Setting          | Value                                           |
|------------------|-------------------------------------------------|
| Target IDE       | IntelliJ IDEA 2026.2 (`libs.versions.toml`)     |
| Compile JDK      | 25 (the 2026.2 platform jars are class file 69) |
| Emitted bytecode | Java 21                                         |
| `sinceBuild`     | 262, `untilBuild` unbounded                     |

To compile and test against a locally installed IDE instead of the downloaded SDK, set `localIdePath`
in `~/.gradle/gradle.properties`, pass `-PlocalIdePath=…`, or export `LOCAL_IDE_PATH`. Point it at the
IDE installation root (the directory containing `lib/`, `plugins/`, `bin/`).

## Gradle tasks

| Task                           | Description                                                        |
|--------------------------------|--------------------------------------------------------------------|
| `runIde`                       | Start a sandbox IDE with the plugin installed                      |
| `test`                         | All tests                                                          |
| `test -PtestSuite=developer`   | Only fast developer tests (every class *not* named `*IT`)          |
| `test -PtestSuite=integration` | Only integration tests (classes named `*IT`)                       |
| `verifyPlugin`                 | Plugin Verifier compatibility check                                |
| `buildPlugin`                  | Build the distributable plugin archive into `build/distributions/` |
| `licensee` / `cyclonedxBom`    | Licence policy check / SBOM generation                             |
| `generateLicenseReport`        | Third-party licence report into `build/licences/`                  |
| `koverHtmlReport`              | Test coverage report                                               |
| `selfSignPlugin`               | Sign locally with `.signing/keystore.p12`                          |
| `buildDocs`                    | Build the docs with `mkdocs --strict` (generation test)            |
| `runDocs`                      | Serve the docs locally and open a browser                          |
| `deployDocs -Pversion=<tag>`   | Publish a versioned docs snapshot via mike                         |

### Test convention

A test class whose name ends in `IT` is an **integration test** — it exercises a shipped artifact or the
interplay of several layers and may measure time. Everything else is a **developer test** and must stay
fast, because it is the inner feedback loop. CI runs both suites in parallel from an identical setup.

## Predefined Run/Debug configurations

| Configuration name  | Gradle task    |
|---------------------|----------------|
| Run IDE with Plugin | `runIde`       |
| Run Tests           | `check`        |
| Run Verifications   | `verifyPlugin` |

> [!NOTE]
> The logs of a running task are available in the `idea.log` tab.

## GitHub integration

### Workflows

| Workflow                                         | Trigger         | Description                                                                                   |
|--------------------------------------------------|-----------------|-----------------------------------------------------------------------------------------------|
| [CI](.github/workflows/ci.yml)                   | Push / PR       | Licences, SBOM, assemble, both test suites, signability check, docs generation test           |
| [Release](.github/workflows/release.yml)         | Tag push        | Tests, build, docs deploy (mike), Marketplace publish, GitHub release with artifacts and SBOM |
| [Sign Plugin](.github/workflows/sign-manual.yml) | Manual dispatch | Produces a signed archive for the one-off first upload to the Marketplace                     |

The `gh-pages` documentation site is updated **only** by tag releases. Pushes to the default branch merely
build the docs for verification.

### Required repository secrets

| Secret              | Description                                                                                   |
|---------------------|-----------------------------------------------------------------------------------------------|
| `KEYSTORE_BASE64`   | Base64-encoded PKCS#12 keystore holding the plugin signing key                                |
| `KEYSTORE_PASSWORD` | Password of that keystore                                                                     |
| `KEY_ALIAS`         | Alias of the signing key inside the keystore                                                  |
| `PUBLISH_TOKEN`     | JetBrains Marketplace token — [generate here](https://plugins.jetbrains.com/author/me/tokens) |

If the signing secrets are absent, the CI signability check emits a warning and is skipped instead of
failing. See [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

### Issue templates and Dependabot

- [Bug Report](.github/ISSUE_TEMPLATE/bug-report.yml)
- [Feature Request](.github/ISSUE_TEMPLATE/feature-request.yml)
- [Dependabot configuration](.github/dependabot.yml) — weekly Gradle and daily GitHub Actions updates

## Releasing

1. Move the `## [Unreleased]` entries of [CHANGELOG.md](./CHANGELOG.md) into a `## [<tag>]` section —
   the release workflow extracts the release notes from there and fails if the section is missing.
2. Push a tag. A tag starting with a digit other than `0` is treated as a stable release (Marketplace
   channel `default`, docs alias `latest`); anything else is published as a pre-release on the `eap` channel.

## Useful links

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)
- [Marketplace Quality Guidelines](https://plugins.jetbrains.com/docs/marketplace/quality-guidelines.html)
- [IntelliJ Platform UI Guidelines](https://jetbrains.github.io/ui)
