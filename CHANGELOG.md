<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mkdocs Changelog

## [Unreleased]

### Added

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
- The second wizard step offers the optional features of a site. No feature ships yet — the `siteFeature`
  extension point exists so the planned MkDocs extensions can be plugged in later.
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
