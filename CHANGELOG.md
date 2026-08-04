<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Mkdocs Changelog

## [Unreleased]

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

### Fixed

- The plugin no longer requires the Kotlin IDE plugin and can therefore be installed in Rider, CLion and
  GoLand, which do not ship it.
