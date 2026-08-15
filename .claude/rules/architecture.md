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
    * `inspection` - Annotators and inspections on MKDocs files, plus their fixes
    * `schema` - JSON schema mappings for MKDocs configuration files
* The `*Icons` class collecting all icons of a module MUST ALWAYS reside on the package root level of that
  module, NOT in a specialised package
* For any deviation the user MUST be asked

## Resources

* `src/main/resources/facets/<feature>` holds the description of a feature as data, NOT as Kotlin code
    * Example: `facets/material` - the feature flags, markdown extensions, `extra` keys, fonts and colours of
      *Material for MkDocs*
* Every such data file MUST be YAML and MUST be accompanied by a JSON schema of the same base name
    * Naming scheme: `<name>.yaml` and `<name>.schema.json`
    * The YAML file MUST name its schema in the first line:
      `# yaml-language-server: $schema=./<name>.schema.json`
    * The schema MUST set `additionalProperties: false` and MUST list every mandatory field under `required`
* The files are read with the Jackson the IntelliJ Platform brings along, which needs NO declaration in
  `build.gradle.kts` - it arrives transitively with the platform dependency, the same way gson does
    * A Jackson dependency resolved from Maven is FORBIDDEN - it makes the Plugin Verifier report unresolved
      references inside the jars' multi-release internals
    * `bundledLibrary(...)` is FORBIDDEN as well - reaching into the platform's own jars is not a supported
      dependency path, and the Gradle plugin warns about it
* Access to the parsed data MUST go through an injectable service, NEVER through a Kotlin `object`
    * Example: `MkDocsMaterialDataService`, annotated with `@Service(Service.Level.APP)`
    * The data classes themselves MUST stay free of Jackson annotations and of any loading logic