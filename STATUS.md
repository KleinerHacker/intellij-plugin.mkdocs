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
| Tool window listing the detected sites                          | ⬜      |

## Configuration (`mkdocs.yml`)

| Topic                                                     | Status |
|-----------------------------------------------------------|--------|
| `site_name`                                               | ✅      |
| Schema-based completion and annotation of all other keys  | ⬜      |
| `docs_dir` / `site_dir` awareness (file detection, excludes) | ⬜   |
| `nav` navigation (references, completion, validation)     | ⬜      |
| `theme` (including `material`)                            | ⬜      |
| `plugins` / `markdown_extensions`                         | ⬜      |

## Features (optional MkDocs extensions)

Planned as facets stacked on the MkDocs facet.

| Feature          | Status |
|------------------|--------|
| Angular Material | ⬜      |
| I18N             | ⬜      |
| Mike             | ⬜      |

## Build / run

| Topic                             | Status |
|-----------------------------------|--------|
| Run configuration `mkdocs serve`  | ⬜      |
| Run configuration `mkdocs build`  | ⬜      |
| Deployment (`gh-deploy`, `mike`)  | ⬜      |
