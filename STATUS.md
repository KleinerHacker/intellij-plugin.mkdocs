# Implementation status

Tracks what the plugin supports compared to [MkDocs](https://www.mkdocs.org/) itself.

Legend: ✅ done · 🚧 in progress · ⬜ not started

## Project / module system

| Topic                                                          | Status |
|----------------------------------------------------------------|--------|
| Detect `mkdocs.yml` / `mkdocs.yaml`                            | ✅      |
| Directory above the configuration file becomes an MkDocs module | ✅      |
| Represented as an *MkDocs* facet on the surrounding module      | ✅      |
| Own module created when the site belongs to no module           | ✅      |
| Module name from `site_name`, fallback directory name           | ✅      |
| Re-detection after virtual file system changes                  | ✅      |
| Site root marked in the project view (site name + badge)        | ✅      |
| Own file icon for `mkdocs.yml` / `mkdocs.yaml`                   | ✅      |
| Own file icon for the Markdown pages below `docs_dir`           | ✅      |
| Own file icon for `requirements.txt` in the site root           | ✅      |
| Documentation, assets and stylesheets directory marked in the project view | ✅ |
| Own file icon for a `*.css` referenced by `extra_css`           | ✅      |
| Create a new site from the IDE (*New → MkDocs Site*)            | ✅      |
| Missing site directories suggested in *New Directory*           | ✅      |
| Tool window listing the detected sites                          | ✅      |

## Configuration (`mkdocs.yml`)

| Topic                                                     | Status |
|-----------------------------------------------------------|--------|
| `site_name`                                               | ✅      |
| `site_name` written from the facet page                   | ✅      |
| `site_author`, `site_description`, `site_url` written by the creation wizard | ✅ |
| `repo_name`, `repo_url` written, prefilled from Git       | ✅      |
| `copyright` written, prefilled from the IDE Copyright profiles | ✅ |
| File level banner for missing `site_name` / `site_author` / `site_description` | ✅ |
| Schema mapping for `mkdocs.yaml` (`mkdocs.yml` covered by the IDE catalogue) | ✅ |
| Schema-based completion and annotation of all other keys  | ✅      |
| `docs_dir` read and used (project view marking, site creation) | ✅   |
| `site_dir` read and written by the creation wizard        | ✅      |
| `docs_dir` / `site_dir` written from the facet page, directories renamed with their references | ✅ |
| `site_dir` awareness (excludes)                           | ⬜      |
| `nav` navigation read and shown in the *Site Page* tool window | 🚧 |
| `nav` navigation (references, completion, validation)     | ✅      |
| File references for `extra_css`, `theme.logo`, `theme.favicon` | ✅ |
| Directory references for `docs_dir` and `site_dir`        | ✅      |
| Gutter icon opening the target of every path              | ✅      |
| Character and structure check of every path, OS aware     | ✅      |
| `theme` (including `material`)                            | 🚧      |
| `plugins` / `markdown_extensions`                         | ⬜      |

## Features (optional MkDocs extensions)

Implemented as facets next to the MkDocs facet. The `siteFeature` extension point and the feature step of
the creation wizard exist; Angular Material is the first feature using them. A feature can contribute wizard
pages of its own, which appear behind the feature step while the feature is selected.

| Feature                                        | Status |
|------------------------------------------------|--------|
| `siteFeature` extension point and wizard step  | ✅      |
| Feature contributed wizard pages               | ✅      |
| Angular Material                               | 🚧      |
| I18N                                           | ⬜      |
| Mike                                           | ⬜      |

## Angular Material (`mkdocs-material`)

Tracks what the plugin supports compared to
[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/).

| Topic                                                                                 | Status |
|---------------------------------------------------------------------------------------|--------|
| Theme detected from `mkdocs.yml`, mapping and scalar form                              | ✅      |
| *MkDocs Angular Material* facet, added and removed by hand as well                     | ✅      |
| Offered in the feature step of the creation wizard                                     | ✅      |
| Refined JSON schema bound to the facet, base MkDocs schema bundled                     | ✅      |
| `theme.features` — all 28 flags, each with a description                               | ✅      |
| `theme.palette` — single mapping and toggle sequence, colours and schemes              | ✅      |
| `theme.font`, `theme.language`, `theme.icon`, `theme.direction`                        | ✅      |
| `theme.logo`, `theme.favicon`, `theme.custom_dir`                                      | ✅      |
| `extra.social`, `extra.analytics`, `extra.consent`, `extra.generator`, `extra.status`  | ✅      |
| `extra.version` / `extra.alternate` left open for Mike and I18N                        | ✅      |
| Settings pages on the facet and in the wizard                                          | ⬜      |
| Required and recommended `markdown_extensions` reported with a fix                     | ⬜      |
| `theme.custom_dir` and `extra_javascript` as path references                           | ⬜      |
| Icon completion from the installed `mkdocs-material` package                           | ⬜      |
| Markdown front matter and `--md-*` CSS variables                                       | ⬜      |
| Theme override scaffolding                                                             | ⬜      |

## Build / run

| Topic                             | Status |
|-----------------------------------|--------|
| Run configuration `mkdocs serve`  | ⬜      |
| Run configuration `mkdocs build`  | ⬜      |
| Deployment (`gh-deploy`, `mike`)  | ⬜      |
