---
name: features
---

# Features in MkDocs modules

* Each feature MUST be auto-detected if `mkdocs.yml` is present in the module directory and/or that file has been updated.
* Features can be added via the UI; in that case the `mkdocs.yml` file MUST be updated by the IDE.

## Angular Material Feature

* Extension for Angular Material (extension point)

## I18N Feature

* Extension for I18N (extension point)

## Mike Feature

* Extension for Mike (complex extension)

## General IDE extensions enabled by activating the features above

* Extends YAML support (intelligence, completion and annotation) in `mkdocs.yml` for the given feature
* Extends file detection in MkDocs module folders