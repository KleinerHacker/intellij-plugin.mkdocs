---
name: module
---

# Module System for MKDocs

Each MkDocs system requires a registered IntelliJ module. The module is detected by the `mkdocs.yml` file in its folder.

## Auto-detect modules

A module MUST be auto-detected via `mkdocs.yml`. The module name is derived from the `site_name` property. Detection
MUST be re-checked after each Virtual File System change / update.