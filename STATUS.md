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
| `site_author`, `site_description`, `site_url` written by the creation wizard | ✅ |
| `repo_name`, `repo_url` written, prefilled from Git       | ✅      |
| `copyright` written, prefilled from the IDE Copyright profiles | ✅ |
| File level banner for missing `site_name` / `site_author` / `site_description` | ✅ |
| Schema mapping for `mkdocs.yaml` (`mkdocs.yml` covered by the IDE catalogue) | ✅ |
| Schema-based completion and annotation of all other keys  | ✅      |
| `docs_dir` read and used (project view marking, site creation) | ✅   |
| `site_dir` read and written by the creation wizard        | ✅      |
| `site_dir` awareness (excludes)                           | ⬜      |
| `nav` navigation read and shown in the *Site Page* tool window | 🚧 |
| `nav` navigation (references, completion, validation)     | ✅      |
| File references for `extra_css`, `theme.logo`, `theme.favicon` | ✅ |
| Directory references for `docs_dir` and `site_dir`        | ✅      |
| Gutter icon opening the target of every path              | ✅      |
| Character and structure check of every path, OS aware     | ✅      |
| `theme` (including `material`)                            | ⬜      |
| `plugins` / `markdown_extensions`                         | ⬜      |

## Features (optional MkDocs extensions)

Planned as facets stacked on the MkDocs facet. The `siteFeature` extension point and the feature step of the
creation wizard exist; no feature implements them yet.

| Feature                                        | Status |
|------------------------------------------------|--------|
| `siteFeature` extension point and wizard step  | ✅      |
| Angular Material                               | ⬜      |
| I18N                                           | ⬜      |
| Mike                                           | ⬜      |

## Build / run

| Topic                             | Status |
|-----------------------------------|--------|
| Run configuration `mkdocs serve`  | ⬜      |
| Run configuration `mkdocs build`  | ⬜      |
| Deployment (`gh-deploy`, `mike`)  | ⬜      |
