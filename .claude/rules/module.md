---
name: module
---

# Module System for MKDocs

Each MKDocs system requires a registered IntelliJ Module. The Module is detected by `mkdocs.yaml` in this folder.

## Auto-detect modules

A module must autodetect by `mkdocs.yaml`. The module name is inherited from the `site_name` property. It must
be checked after each Virtual File System change / update.