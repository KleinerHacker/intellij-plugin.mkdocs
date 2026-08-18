Die # Implementation status

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
| Theme description kept as bundled YAML under `material/spec`, each with a JSON schema | ✅      |
| `theme.features` — all 28 flags, each with a description                               | ✅      |
| `theme.palette` — single mapping and toggle sequence, colours and schemes              | ✅      |
| `theme.font`, `theme.language`, `theme.icon`, `theme.direction`                        | ✅      |
| `theme.logo`, `theme.favicon`, `theme.custom_dir`                                      | ✅      |
| `extra.social`, `extra.analytics`, `extra.consent`, `extra.generator`, `extra.status`  | ✅      |
| `extra.version` / `extra.alternate` left open for Mike and I18N                        | ✅      |
| Settings pages on the facet and in the wizard                                          | ✅      |
| Required and recommended `markdown_extensions` reported with a fix                     | ✅      |
| Quick documentation for an entry of `markdown_extensions`                              | ✅      |
| Completion of the options below an entry of `markdown_extensions`, and of their values | ✅      |
| Quick documentation for such an option                                                 | ✅      |
| Quick documentation inside the completion popup of `markdown_extensions`               | ✅      |
| `theme.custom_dir` and `extra_javascript` as path references                           | ✅      |
| Intention creating the target of a path                                                | ✅      |
| Icon completion from the installed `mkdocs-material` package                           | ✅      |
| Icon shorthands `:material-…:` completed in the pages                                  | ✅      |
| Icon directory override in the settings                                                | ✅      |
| `--md-*` CSS variables completed in style sheets                                       | ✅      |
| Inlay hint marking the keys the theme alone reads                                      | ✅      |
| Completion entries of the theme marked with its icon                                   | ✅      |
| Markdown front matter                                                                  | ⬜      |
| Theme override scaffolding, with live templates for the Jinja blocks                   | ✅      |

### Markdown extensions of the theme

What the plugin knows about each extension `Material for MkDocs` builds on. *Forced* means a flag under
`theme.features` cannot render without it, which is reported as an error; everything else is the weak
warning. *Options* means the quick fix writes the options the extension needs, not only its identifier.
Every one of them carries a description and a documentation link shown in quick documentation.

| Extension                | Package               | Forced by a feature | Options written |
|--------------------------|-----------------------|---------------------|-----------------|
| `admonition`             | Python Markdown       | ⬜                   | ⬜               |
| `attr_list`              | Python Markdown       | ✅                   | ⬜               |
| `md_in_html`             | Python Markdown       | ✅                   | ⬜               |
| `def_list`               | Python Markdown       | ⬜                   | ⬜               |
| `footnotes`              | Python Markdown       | ✅                   | ⬜               |
| `tables`                 | Python Markdown       | ⬜                   | ⬜               |
| `toc`                    | Python Markdown       | ⬜                   | ✅               |
| `pymdownx.superfences`   | `pymdown-extensions`  | ✅                   | ⬜               |
| `pymdownx.highlight`     | `pymdown-extensions`  | ✅                   | ✅               |
| `pymdownx.inlinehilite`  | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.snippets`      | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.tabbed`        | `pymdown-extensions`  | ✅                   | ✅               |
| `pymdownx.emoji`         | `pymdown-extensions`  | ✅                   | ✅               |
| `pymdownx.details`       | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.critic`        | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.caret`         | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.keys`          | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.mark`          | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.tilde`         | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.tasklist`      | `pymdown-extensions`  | ⬜                   | ✅               |
| `pymdownx.smartsymbols`  | `pymdown-extensions`  | ⬜                   | ⬜               |
| `pymdownx.arithmatex`    | `pymdown-extensions`  | ⬜                   | ✅               |

`pymdownx.emoji` is only forced once a page writes an icon shorthand. The annotator cannot see that yet — it
would need the pages indexed rather than the configuration file — so the extension is currently never
reported as forced.

### Sub-plugins of the theme (`plugins`)

*Material for MkDocs* ships plugins of its own, switched on under the top level `plugins` key and configured
with options below their name. **None of them is supported yet.** For every entry of the list this means the
same three gaps: the plugin name is not completed, its options are neither completed nor validated, and
nothing offers to add it.

The plain MkDocs schema describes `plugins` as a list of free strings and mappings, so a name that does not
exist and an option the plugin does not read are both waved through today.

| Plugin     | Edition       | Purpose                                                     | Name | Options | Offered |
|------------|---------------|------------------------------------------------------------|------|---------|---------|
| `search`   | MkDocs itself | the search index and the search field of the theme          | ⬜    | ⬜       | ⬜       |
| `tags`     | Material      | tags in the front matter, and the page listing them         | ⬜    | ⬜       | ⬜       |
| `blog`     | Material      | a blog section with posts, archive and categories           | ⬜    | ⬜       | ⬜       |
| `social`   | Material      | the preview cards rendered for social networks              | ⬜    | ⬜       | ⬜       |
| `offline`  | Material      | a build that works from the file system, without a server   | ⬜    | ⬜       | ⬜       |
| `group`    | Material      | a group of plugins switched on by a condition               | ⬜    | ⬜       | ⬜       |
| `privacy`  | Material      | external assets downloaded into the build                   | ⬜    | ⬜       | ⬜       |
| `info`     | Material      | the report archive the theme's issue template asks for      | ⬜    | ⬜       | ⬜       |
| `meta`     | Insiders      | front matter applied to every page of a directory           | ⬜    | ⬜       | ⬜       |
| `optimize` | Insiders      | images shrunk and converted while the site is built         | ⬜    | ⬜       | ⬜       |
| `typeset`  | Insiders      | the formatting of a heading kept in navigation and title    | ⬜    | ⬜       | ⬜       |
| `projects` | Insiders      | several sites built as one                                  | ⬜    | ⬜       | ⬜       |

*Name* is completion of the plugin name, *Options* completion and validation of its options, *Offered* an
action or page adding it. The Insiders editions of `tags` and `blog` add further options to the plugins of
the same name; those are unsupported along with the rest.

Third party plugins commonly used next to the theme — `awesome-pages`, `git-revision-date-localized`,
`git-committers`, `minify`, `redirects`, `glightbox`, `macros`, `mkdocstrings` — are equally unsupported and
are not tracked individually here.

`mike` and the I18N plugins are deliberately absent from this list: they are planned as MkDocs *features* of
their own and are tracked under *Features* above.

### Icon sets of the theme

Read from the installed package rather than carried as a list, so whichever sets the installed version ships
are the ones offered.

| Set             | Completed in `mkdocs.yml` | Completed as a shorthand in a page |
|-----------------|---------------------------|------------------------------------|
| `material`      | ✅                         | ✅                                  |
| `fontawesome`   | ✅                         | ✅                                  |
| `octicons`      | ✅                         | ✅                                  |
| `simple`        | ✅                         | ✅                                  |
| a custom set    | ✅                         | ✅                                  |

## Build / run

| Topic                             | Status |
|-----------------------------------|--------|
| Run configuration `mkdocs serve`  | ⬜      |
| Run configuration `mkdocs build`  | ⬜      |
| Deployment (`gh-deploy`, `mike`)  | ⬜      |
