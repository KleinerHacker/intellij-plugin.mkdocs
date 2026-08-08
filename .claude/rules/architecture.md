---
name: architecture
---

# Architecture

## Structure

* The root package is ALWAYS `org.pcsoft.intellij.plugin.mkdocs`
* Below that there are specialised packages for:
    * `services` - Injectable services (IntelliJ Platform)
    * `types` - General types
    * `settings` - Settings features, settings pages, ...
    * `build` - Everything around building MKDocs, e.g. run config, building, ...
* The `*Icons` class collecting all icons of a module MUST ALWAYS reside on the package root level of that
  module, NOT in a specialised package
* For any deviation the user MUST be asked