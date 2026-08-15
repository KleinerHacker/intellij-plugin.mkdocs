# MkDocs – JetBrains Plugin

[![CI](https://github.com/KleinerHacker/intellij-plugin.mkdocs/actions/workflows/ci.yml/badge.svg)](https://github.com/KleinerHacker/intellij-plugin.mkdocs/actions/workflows/ci.yml)

Support for [MkDocs](https://www.mkdocs.org) projects in all IntelliJ-platform IDEs.

📖 **Documentation:** <https://kleinerhacker.github.io/intellij-plugin.mkdocs/>

## Features

- **Automatic module detection** — the directory above an `mkdocs.yml` / `mkdocs.yaml` is recognised as an
  MkDocs module and marked with the *MkDocs* facet, visible in *Project Structure → Facets*. The facet is
  assigned by the detection only — it cannot be added by hand, and a facet without a configuration file
  behind it reports an error.
- The module name is taken from `site_name`, falling back to the directory name.
- **Rename the site on the facet page** — the site name is written into `site_name`, so the module and the
  project view follow.
- **Rename the technical directories on the facet page** — the documentation, output, assets and stylesheets
  directory can be changed in *Project Structure*. The directory is renamed and every reference to it follows
  (`extra_css`, `nav`, `theme.logo`, `theme.favicon` and the links of the pages); `docs_dir` and `site_dir`
  are written back into `mkdocs.yml` and taken out again once they hold nothing but the MkDocs default.
- Detection re-runs after every relevant change to the virtual file system, so adding, renaming or deleting a
  configuration file is picked up without a reload.
- A module of its own is created only for a site that belongs to no module at all; otherwise the surrounding
  module is used, which keeps Gradle and Maven imports untouched.
- **Create a site from the IDE** — *New → MkDocs Site*, in the project view context menu and under
  *File → New*. The five step wizard asks for the layout (name and location, documentation directory, assets
  directory, stylesheets directory, build output directory), the site metadata (`site_name`, `site_author`, `site_description`,
  `site_url`), the repository (`repo_name`, `repo_url`), the `copyright` notice and the optional features,
  then writes `mkdocs.yml`, a start page and the directory structure. Everything left empty is omitted from
  the configuration file. The last step is fed by the `siteFeature` extension point and currently offers
  *Angular Material*.
- **Angular Material facet** — a site whose `mkdocs.yml` names `material` as its theme carries the
  *MkDocs Angular Material* facet, next to the *MkDocs* facet and wearing the MkDocs logo badged with the
  Material glyph. The facet follows the file, and the file follows
  the facet: adding it in *Project Structure* writes `theme.name: material`, removing it takes the `theme`
  key out again, and settings written next to the name survive. Both shapes MkDocs accepts — `theme` as a
  mapping and `theme` as a plain scalar — are recognised.
- **Refined schema for a Material site** — a site carrying the Angular Material facet is edited against a
  schema describing the two blocks the theme actually fills: all 28 flags of `theme.features` with a
  description each, `theme.palette` in both its shapes with the colours and schemes the theme ships,
  `theme.font`, `theme.language`, `theme.icon`, `theme.direction`, `theme.logo`, `theme.favicon`,
  `theme.custom_dir`, and `social`, `analytics`, `consent`, `generator` and `status` under `extra`. It stands
  in front of the MkDocs schema rather than replacing it, applies only where the facet is, and leaves
  `extra.version` and `extra.alternate` open for the planned Mike and I18N features. The MkDocs schema it
  builds on is bundled, so it works offline.
- **Markdown extensions of a Material site** — an extension the configuration forces, because a flag under
  `theme.features` does not render without it, is reported above `mkdocs.yml` as an error, with a fix adding
  it together with the options it needs. Everything the theme merely builds on is a weak warning that can be
  switched off. Quick documentation explains every entry of `markdown_extensions`.
- **Icons of the installed theme** — the icons of `mkdocs-material` are completed in `mkdocs.yml`
  (`theme.icon.*`, the toggle of a palette, `extra.social`) and in the pages as the shorthands
  `:material-check:`, each showing its drawing. They are read from the installed package, which is looked for
  in the virtual environments next to the site or named under *Tools → MkDocs*. The custom properties of the
  theme (`--md-…`) are completed inside CSS files.
- **Template overrides** — the context menu of a site root creates the override directory, the selected
  templates with a working Jinja scaffold, and `theme.custom_dir` pointing at them, in one undoable step.
  Live templates for the Jinja blocks come with it.
- **Prefilled from the environment** — repository address, repository name and author come from the Git
  repository the site is created in; an entry deviating from it is reported as a warning. The copyright
  notice comes from the IDE's Copyright profiles, with a choice when several are configured and none is
  marked as the default.
- **Metadata banner** — a missing `site_name`, `site_author` or `site_description` is reported by a banner
  above `mkdocs.yml` / `mkdocs.yaml`, each with a fix adding the key.
- **JSON schema for both spellings** — `mkdocs.yaml` is mapped to the MkDocs schema, which the bundled
  schema catalogue offers for `mkdocs.yml` only, so completion and validation work for either file name.
- **Output directory follows the build system** — `site_dir` is pre-filled with `target/docs` in a Maven
  module, `build/docs` in a Gradle module, `out/docs` in a plain IntelliJ IDEA module and `site` everywhere
  else, so a build never drops its HTML next to the sources.
- **Marked in the project view** — the site root directory shows its `site_name` in brackets behind the
  directory name, like a Maven project directory, and its folder icon carries a small MkDocs badge. The
  documentation directory, the assets directory and the stylesheets directory get their own badge.
- **Own file icons** — `mkdocs.yml` / `mkdocs.yaml` is shown with a dedicated MkDocs icon instead of the
  generic YAML one, every Markdown file below `docs_dir` gets a page icon, and a `requirements.txt` sitting
  directly next to the configuration file gets a requirements icon. A `*.css` file gets a style sheet icon
  while `extra_css` names it — the key is what makes MkDocs load it, so an unreferenced style sheet keeps the
  ordinary icon. All of this applies everywhere the IDE renders the files.
- **Paths in `mkdocs.yml` are references** — the entries of `extra_css`, the targets of `nav`, `theme.logo`,
  `theme.favicon`, `docs_dir` and `site_dir` point at the file or directory they name. Ctrl+click follows the
  path, completion offers what is there, renaming the file rewrites the entry, and a path leading nowhere is
  reported — `site_dir` excepted, as the build output is not expected to exist yet.
- **Gutter icon on every path** — one click opens the target. A file shows the icon it carries everywhere
  else in the IDE, `docs_dir` and `site_dir` show the badge of their directory.
- **Path check** — a path no file system would accept is an error: forbidden or control characters, an empty
  segment, a segment ending in a dot or a space, an absolute path, a drive letter or a `..` leaving the site.
  What only breaks elsewhere — a backslash separator, a reserved Windows name, non-ASCII characters, a space
  in a segment — is a warning, so a site stays portable without the current machine complaining. `site_dir`
  names the build output and may lie outside the site, so an absolute path, a drive letter and a `..` are not
  reported there.
- **Suggestions in *New Directory*** — a site missing its documentation, assets or stylesheets directory
  offers it in the platform dialog, with the same badge the project view uses.
- ***Site Page* tool window** — one tab per detected site, each showing the navigation written under `nav` in
  its `mkdocs.yml`. A node is labelled with the title from `nav`, else with the first `#` heading of the page,
  else with the file name without its extension. Sections become folders, external targets become links, and
  an entry pointing nowhere stays visible and greyed. Double clicking opens the page in the editor or the
  address in the browser. A site without `nav` says so instead of showing an invented navigation.

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
│       ├── META-INF/       plugin.xml and plugin logo
│       └── facets/         What a feature offers, as YAML next to its JSON schema
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
