<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mkdocs Changelog

## [Unreleased]

### Added

- The **installation of Material for MkDocs is now found on its own** in far more setups: the virtual
  environments are looked for from the site root up to the root of the project, so a site in `docs/` next to
  a `.venv` in the project root is found as well.
- The settings moved into a page of their own: *Tools → MkDocs* is now the node the features hang under, and
  the icon directory sits below it under **Material**. That page also states which installation was found,
  which is the answer to an empty icon completion.
- The **drawing of an icon** is now shown in the editor: in `mkdocs.yml` in front of every value naming one,
  and in the pages of a site in front of every shorthand such as `:material-weather-sunny:`. A name the
  installed theme does not offer stays without a drawing. Both hints can be switched off separately under
  *Settings → Editor → Inlay Hints*.
- The **icon names are now completed at every place** `mkdocs.yml` names an icon: next to the keys below
  `theme.icon`, the palette toggle and `extra.social`, the mappings `theme.icon.admonition` and
  `theme.icon.tag` and the `icon` of a rating of `extra.analytics.feedback` are offered as well.
- A site rendered with the **Material theme** now carries the *MkDocs Angular Material* facet, next to the
  MkDocs facet and visible in *Project Structure → Facets*. It wears the MkDocs logo with the Material glyph
  badged into its corner, so it reads as part of MkDocs in a list that shows facets flat. It appears as soon
  as `mkdocs.yml` names `material` as
  its theme — written as a mapping or as a plain scalar, in any case — and disappears again when the theme is
  taken out or switched to another one.
- The facet can also be added and removed by hand: adding it writes `theme.name: material` into `mkdocs.yml`,
  removing it takes the whole `theme` key out again. Settings written next to the theme name survive, and a
  file that already declares the theme is left untouched.
- The feature step of the creation wizard offers **Angular Material**, which writes the theme into the new
  site and attaches the facet right away. It is the first feature filling that step.
- The **site name** can now be changed on the facet page in *Project Structure*. It is written into
  `site_name`, so the module, the project view and the *Site Page* tool window follow. An empty name is
  refused.
- The **technical directories** of a site can now be changed on its facet page in *Project Structure*: the
  documentation directory, the output directory, the assets directory and the stylesheets directory. Applying
  the page renames the directory on disk and every reference to it follows — the entries of `extra_css`, the
  targets of `nav`, `theme.logo`, `theme.favicon` and the links of the pages. `docs_dir` and `site_dir` are
  written back into `mkdocs.yml`, and taken out again once they carry nothing but the MkDocs default. The
  output directory is only written, never moved: it holds build output, which the next build writes anyway.
- A name that cannot be applied is reported before anything is moved: a name carrying a path of its own, a
  directory that is not there, and a name already taken inside the site.
- A site carrying the *MkDocs Angular Material* facet now gets a **refined JSON schema** for its `mkdocs.yml` /
  `mkdocs.yaml`, so the two blocks the Material theme actually fills — `theme` and `extra` — are completed,
  validated and explained instead of being waved through. The plain MkDocs schema describes `theme` with four
  keys and `extra` with none, which is correct for MkDocs and of no use to a site rendered with Material.
- `theme.features` offers all 28 feature flags of the theme, each with a one line description shown next to the
  offered value, and a flag the theme does not know is reported.
- `theme.palette` is covered in both shapes the theme accepts — a single mapping and a sequence for the colour
  scheme toggle — with `scheme`, `primary`, `accent`, `media` and `toggle`. The colour names offered are the
  ones the theme ships, primary and accent each with their own set.
- `theme.font`, `theme.language`, `theme.icon`, `theme.logo`, `theme.favicon`, `theme.custom_dir` and
  `theme.direction` are described as well, as is the Material part of `extra`: `social`, `analytics`,
  `consent`, `generator` and `status`.
- `extra.version` and `extra.alternate` are deliberately left unconstrained — they belong to the coming Mike
  and I18N features and must not be reported as unknown in the meantime.
- The refined schema is bound to the facet: a site on another theme keeps the plain MkDocs schema, so no key is
  offered that the theme rendering the site does not read. Both schemas apply side by side, the refined one
  first, and the MkDocs schema it builds on is bundled with the plugin rather than fetched at runtime.
- A **Markdown extension that the configuration forces** is now reported above `mkdocs.yml` as an error: a flag
  such as `content.code.annotate` renders nothing at all without `pymdownx.superfences`, and nothing in the
  file shows it. The quick fix adds the extension together with the options it needs. Nothing is reported for a
  site that ticks no such flag — the theme renders a plain site without a single extension.
- Everything the theme merely **builds on** is offered separately, as a weak warning that can be switched off
  in *Settings → Editor → Inspections*: a site keeping its Markdown plain is not doing anything wrong.
- **Quick documentation** on an entry of `markdown_extensions` now explains what the extension does and links
  to its own documentation, instead of saying nothing.
- **Quick documentation** on an entry of `theme.features` now explains what the flag does: its one line
  description, the section of the page it changes, the flags it needs or clashes with, the Markdown
  extensions it forces, whether it needs an *Insiders* build, and a link into the documentation of the theme.
- `markdown_extensions` now **completes** the extensions the theme builds upon, each with its one line
  description — in both shapes the key accepts, the sequence of names and the mapping of name to options. An
  extension outside that list stays valid, so a site may keep on using one of its own.
- The **options of a Markdown extension** are now completed one level below its entry — `permalink` and
  `toc_depth` under `- toc:`, and the same for every other extension the theme builds upon. Each entry names
  the kind of value it takes and what it does, and the value itself is offered where it is a flag or a fixed
  set of choices. That level was empty before: no schema describes it.
- **Quick documentation** on such an option now explains what it does, what it takes, which values it accepts,
  what the extension falls back to without it and what *Material for MkDocs* recommends for it.
- ++ctrl+q++ inside the **completion popup** now answers as well, for an extension and for an option alike. The
  descriptions are no longer written behind the offered names: a popup of two dozen entries, each carrying a
  sentence, was unreadable, and the sentence is one key away.
- `theme.custom_dir` and the entries of `extra_javascript` — in both the plain and the mapping form MkDocs 1.6
  accepts — are now **path values** like every other: navigation, completion, renaming, the gutter icon and the
  path check apply to them. The override directory is resolved next to `mkdocs.yml`, a script below `docs_dir`.
- A new intention **creates the target** a path points at, with the directories along the way, as a directory or
  as an empty file depending on the key. Not offered for `site_dir`, which the build writes itself.
- The **icons of the installed Material for MkDocs** are now completed in `mkdocs.yml` — at `theme.icon.*`, at
  the toggle of a palette and at the icons of `extra.social` — and in the pages of a site as the shorthands
  `:material-check:` and their like. The drawing is shown next to each entry. The names are read from the
  installed package, so they always match the version of the theme that is actually there.
- The installation is looked for in the virtual environments next to the site (`.venv`, `venv`, `env`,
  `.virtualenv`, on Windows and on POSIX layouts alike). A new settings page under *Tools → MkDocs* takes the
  path for every other setup.
- The **custom properties of the theme** (`--md-…`) are completed inside CSS files, each with the part of the
  page it paints.
- **Template overrides** can now be created from the context menu of a site root: the override directory, the
  selected files with a working Jinja scaffold in them, and `theme.custom_dir` pointing at the directory — all
  in one undoable step. Live templates for the Jinja blocks come with it.
- The keys a configuration file owes to the **Material theme** are now marked as such. An icon sits in front of
  `theme.features`, `theme.palette`, `theme.font`, `theme.icon`, `theme.direction` and the theme's own keys
  below `extra`, with a tooltip saying that MkDocs itself does not read them. What MkDocs reads — `theme.name`,
  `theme.logo`, `theme.favicon`, `theme.custom_dir`, `markdown_extensions` — stays unmarked. The hint can be
  switched off under *Settings → Editor → Inlay Hints*.
- The same mark now appears on the **completion entries** coming from the theme: in `mkdocs.yml`, on the icon
  shorthands `:material-…:` in the pages, and on the `--md-*` custom properties in the style sheets. An entry
  that already shows a drawing of its own keeps it and is badged instead.
- The user interface is now **localised** into the three languages JetBrains ships a language pack for:
  Simplified Chinese, Japanese and Korean. The IDE picks the texts up on its own once the matching language
  pack is installed; without one everything stays English as before.

## [0.2.0]

### Added

- Every path written in `mkdocs.yml` is now a real reference: the entries of `extra_css`, the targets of
  `nav`, `theme.logo` and `theme.favicon` point at their file, `docs_dir` and `site_dir` at their directory.
  Ctrl+clicking follows the path, completion offers what is there, renaming a file rewrites the entry, and a
  path leading nowhere is reported — except for `site_dir`, which names the build output and is therefore
  never expected to exist.
- Each of those paths carries a gutter icon opening the target with one click. A file shows the icon it
  carries elsewhere in the IDE, so a page, a style sheet or an image is recognised in the configuration file
  itself; `docs_dir` and `site_dir` show the badge of their directory.
- A path that no file system would accept is reported: forbidden or control characters, an empty segment, a
  segment ending in a dot or a space, an absolute path, a drive letter or a `..` leaving the site. What only
  breaks on other operating systems — a backslash as separator, a reserved Windows name, non-ASCII characters
  or a space in a segment — is reported as a warning, so a site stays portable without the current machine
  complaining about paths that work on it. `site_dir` is exempt from the last three: it names the build
  output, which may well be written outside the site, so an absolute path, a drive letter and a `..` leaving
  the site are all accepted there.
- A site can now carry a **stylesheets directory** next to its assets directory. The creation wizard asks for
  the name, defaulting to `stylesheets`, and creates the directory inside the documentation directory. Like
  the assets directory it has no MkDocs key, so the chosen name is remembered in the MkDocs facet and shown
  there.
- The stylesheets directory carries a badge of its own in the project view, and both it and the assets
  directory are offered side by side in the *New Directory* dialog while they are still missing — creating
  one of the two no longer hides the other.
- A `*.css` file gets a style sheet icon of its own while `extra_css` in `mkdocs.yml` names it. The key is
  what makes MkDocs load a style sheet, so a file lying unreferenced in the stylesheets directory keeps the
  ordinary icon, and one referenced from elsewhere below `docs_dir` gets the icon all the same.
- A *Site Page* tool window on the left shows the navigation of the detected MkDocs sites. Every site gets a
  tab of its own, and each tab renders the tree written under `nav` in its `mkdocs.yml`, in the order the file
  gives it. Sections become folders, targets leaving the site become links.
- A node is labelled with the title written in `nav`. Without one the first `#` heading of the page is used,
  and without a heading the file name without its extension. Headings are read out of the open editor when
  there are unsaved changes, so renaming one shows up without saving first.
- An entry of `nav` whose page cannot be found stays in the tree, greyed and with the path in its tooltip,
  rather than being dropped silently.
- A site whose `mkdocs.yml` carries no `nav` — or an empty one — says so in place of the tree. No navigation
  is invented from the contents of the documentation directory.
- Double clicking a page opens it in the editor, double clicking an external entry opens it in the browser.
  The tree follows changes to `mkdocs.yml` and to the pages by itself and can be re-read from the toolbar.
- The site creation wizard now covers the metadata of a site. Beyond the technical layout it asks for
  `site_name`, `site_author`, `site_description` and `site_url`, for `repo_name` and `repo_url`, and for the
  `copyright` notice. Everything left empty is omitted from `mkdocs.yml` instead of being written empty.
- Repository address, repository name and author are prefilled from the Git repository the site is created
  in. SSH addresses are rewritten to their browser form and credentials are dropped.
- An entry deviating from the local repository is reported as a warning, for both the address and the name,
  without blocking the wizard — a site may well document another repository.
- The copyright notice is taken from the Copyright settings of the IDE. With several notices configured and
  none marked as the default, the notice can be picked from a list; the text stays editable either way.
- A banner above `mkdocs.yml` / `mkdocs.yaml` reports a missing `site_name`, `site_author` or
  `site_description`. Every key gets a banner of its own, carrying a fix that adds it.
- `requirements.txt` gets an own MkDocs icon, but only when it sits directly next to `mkdocs.yml` /
  `mkdocs.yaml` in the site root. A `requirements.txt` anywhere else keeps the icon the IDE gives it.
- `mkdocs.yaml` is now mapped to the MkDocs JSON schema, which the bundled schema catalogue offers for
  `mkdocs.yml` only — the second spelling MkDocs accepts previously had no completion and no validation.

### Changed

- The first wizard step now asks for a *name* rather than the site name: it is the directory the site is
  created in, and it no longer doubles as `site_name`. The site name has moved to the second step and starts
  out prefilled with it.
- The site creation wizard grew from two steps to five: layout, site metadata, repository, copyright and
  optional features.
- The plugin description shown in the marketplace and in the IDE plugin manager now lists all implemented
  features instead of a single introductory sentence.
- Section nodes of the *Site Page* tool window carry an own MkDocs folder icon instead of the generic folder
  of the platform.
- The note shown in place of an empty tree wraps at word boundaries to the width of the tool window and
  re-wraps when that width changes.
- The grey text behind a page node shows the file name only. A page living in a subdirectory adds its path
  relative to the documentation directory in brackets, for example `install.md (guide/install.md)`.

## [0.1.0]

### Added

- MkDocs module detection: the directory above an `mkdocs.yml` / `mkdocs.yaml` is recognised as an MkDocs
  module and marked with the new *MkDocs* facet.
- The module name is derived from `site_name`, falling back to the directory name.
- Detection re-runs automatically after relevant virtual file system changes.
- The site root directory is marked in the project view: the site name is shown bold in brackets behind the
  directory name and the folder icon carries a small MkDocs badge. The colour of the site name comes from a
  dedicated colour scheme entry and follows light and dark themes.
- `mkdocs.yml` / `mkdocs.yaml` gets its own icon instead of the generic YAML one.
- New MkDocs sites can be created from the IDE: *New → MkDocs Site*, both in the project view context menu
  and under *File → New*. A two step wizard asks for the location, the site name, the documentation directory
  and the assets directory, and writes `mkdocs.yml`, a start page and the directory structure. The location
  follows the site name like in the new project dialog, and every missing level of the path is created. The
  step warns when the target directory is not empty and refuses a directory that already holds an MkDocs
  configuration file. *Next* stays disabled until the location, the site name and both directory names are
  usable.
- The second wizard step offers the optional features of a site. No feature ships yet.
- The documentation directory and the assets directory of a site now carry their own badge in the project
  view, so the three directories of a site are distinguishable at a glance.
- A module containing more than one MkDocs site no longer silently drops all but the first one: every further
  site gets a module of its own, its directory being excluded from the module it was taken from and handed
  back once the site disappears.
- A directory holding both `mkdocs.yml` and `mkdocs.yaml` now always resolves to `mkdocs.yml`, the file MkDocs
  itself would load, instead of whichever the file system returned first.
- The MkDocs facet can no longer be added by hand in the Project Structure dialog — it is assigned by the
  detection only. A facet that reaches a module without a configuration file behind it now reports an error
  in its tab instead of showing empty fields.

- Every Markdown file below the documentation directory of a site now carries its own page icon, recursively
  through subdirectories. Markdown files outside the documentation directory, such as a README in the site
  root, keep the icon the IDE gives them.
- The creation wizard has a fourth field for the build output directory, written to `site_dir`. It is
  pre-filled from the build system surrounding the location — `target/docs` for Maven, `build/docs` for
  Gradle, `out/docs` for a plain IntelliJ IDEA module, and `site` when there is no build system — and follows
  the location until it is edited by hand.
- The *New Directory* dialog now suggests the directories a site is still missing: the documentation
  directory at the site root, the assets directory inside it, each with its badge and only while it does not
  exist yet.

### Fixed

- The plugin no longer requires the Kotlin IDE plugin and can therefore be installed in Rider, CLion and
  GoLand, which do not ship it.
- A site name containing YAML syntax such as `:`, `#` or a quote no longer produces a broken `mkdocs.yml` —
  values are quoted when they need to be.
- The assets directory of a new site is no longer created with a `.gitkeep` file in it.
