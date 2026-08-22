---
name: docs-site
description: Rules for the user facing documentation of this project - the README contents, the MkDocs site under docs/ with its mkdocs.yml, docs/docs, assets and stylesheets, and the external MkDocs reference. Read before changing the README or any file below docs/.
---

## Readme

* There MUST be instructions on how to check out, build and run the project
* EVERY feature must be documented in a bullet point
* There MUST be instructions on how to consume the artifacts
* There MUST be a short outline of the "WHAT" of the project
* There MUST be a reference to the MkDocs documentation (gh-pages), the API documentation and the licence report
* The readme MUST be checked automatically after changes and adjusted if required

## MkDocs

* MkDocs MUST be integrated under `docs`
* Structure:
    * `docs/mkdocs.yml` - Root file
    * `docs/docs` - *.MD files
    * `docs/docs/assets` - Further asset files (MUST reside inside `docs_dir` so that MkDocs ships them)
    * `docs/docs/stylesheets` - Additional CSS files
* The documentation MUST be checked after every change and adjusted if necessary

## External Documentation

* All MKDocs documentation is available here: `https://www.mkdocs.org/user-guide/writing-your-docs/`
