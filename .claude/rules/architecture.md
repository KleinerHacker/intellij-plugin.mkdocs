---
name: architecture
---

# Architecture 

## Module

The build is a multi module Gradle build. The root project IS the plugin; the other projects carry the
code. Until the split described in `.claude/plans/Multi-Modul-Umbau.md` is finished, the whole plugin
still lives in the root project - the rules below are the target every change MUST move towards, never
away from.

* The root project applies `org.jetbrains.intellij.platform` and owns `META-INF/plugin.xml`, the
  signing, the publishing and the `pluginVerification` matrix
    * It holds the plugin implementation: module system, schema, inspections, build, references, UI
* Every other project applies `org.jetbrains.intellij.platform.module` and produces a plain jar
* The module layout is:

```
mkdocs/
├── build.gradle.kts / src/main    the plugin: implementation + plugin.xml
├── utils/                         shared model and helpers, no project dependency
└── facets/
    ├── api/                       the facet contract, no project dependency
    └── <facet-name>/              one project per facet, e.g. material
```

* The dependency arrows are:
    * `:facets:api` and `:utils` are leaves - they MUST NOT depend on any other project
    * A facet depends on `:facets:api` and `:utils`, NEVER on the plugin
    * The plugin depends on `:facets:api`, `:utils` and every facet, the latter only to package them
* `:facets:api` holds ONLY the extension contract and the DTOs of its own signatures
    * Today: `MkDocsSiteFeature`, `MkDocsFeatureWizardStep`, `MkDocsSite`, `MkDocsSiteTemplate`
    * Implementation, services and UI are FORBIDDEN there
* `:utils` holds what more than one facet needs and what carries no facet knowledge
    * Today: `MkDocsProject`, `MkDocsConfig`, `MkDocsConfigWriter`
* The plugin MUST NOT reference a facet from Kotlin code - every hand-off goes through the
  `siteFeature` extension point
* A new facet MUST start as a new project under `facets/`, NEVER as a package inside the plugin
* For any deviation the user MUST be asked

### Plugin descriptor

* A facet is registered as a plugin content module of the V2 model, listed in `plugin.xml` under
  `<content>`
* The module descriptor MUST lie in the resource ROOT of the facet project and MUST be named after the
  module, e.g. `org.pcsoft.ij.plugin.mkdocs.material.xml`
    * Placing it under `META-INF` is FORBIDDEN - the platform does not find it there
* Functionality that needs another plugin MUST become its own content module with
  `loading="optional"` in `<content>` and a `<dependencies><plugin id="..."/></dependencies>` in its
  descriptor
    * This replaces `<depends optional="true" config-file="...">`
* Every content module gets its own class loader, whose parents are only the modules named in its
  `<dependencies>`

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

* Every resource of a facet that another facet could carry under the same name MUST live under
  `<facet-name>/` inside the resource root of that facet project
    * Resource paths are class path paths and are flat across the jars of one plugin; the directory is
      what keeps two facets from overwriting each other
    * Example: `material/spec`, `material/schema`, `material/icons`, `material/liveTemplates`
    * Relying on the class loader of the content module alone is NOT enough - the prefix is mandatory
* Exempt are the paths the platform prescribes, where the file name already carries the feature name
    * `inspectionDescriptions/<ShortName>.html` and `messages/<Bundle>.properties`
* `<facet-name>/spec` holds the description of a feature as data, NOT as Kotlin code
    * Example: `material/spec` - the feature flags, markdown extensions, `extra` keys, fonts and colours
      of *Material for MkDocs*
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
