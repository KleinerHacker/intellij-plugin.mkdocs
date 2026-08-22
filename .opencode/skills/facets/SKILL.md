---
name: facets
description: Rules for MkDocs facets - creating or changing a facet project under facets/, its plugin content module and descriptor, its spec/schema resources, its resource bundle, the installation locator and its tests. Read before any work on a facet or an MkDocs site feature (Material, I18N, Mike).
---


# Facets

A facet is one optional feature of an MkDocs site - the Material theme, I18N, Mike - shipped as a project of
its own under `facets/`. This file is the single source of truth for everything a facet is made of. The
dependency arrows of the Gradle projects and the module layout live in `.claude/rules/architecture.md` and are NOT repeated
here.

* An IntelliJ IDEA feature MUST always be built so that it can be added to or removed from an IntelliJ
  module - every IDE extension is linked to that feature

## Feature detection

* Each feature MUST be auto-detected if `mkdocs.yml` is present in the module directory and/or that file has
  been updated
* Features can be added via the UI; in that case the `mkdocs.yml` file MUST be updated by the IDE
* A facet MUST NOT look up the MkDocs facet of the plugin: `MkDocsSiteFiles.findConfigFile(module)` answers
  which configuration file a module stands for, and a `null` answers "no MkDocs site here"
* The plugin MUST NOT reference a facet from Kotlin code - every hand-off goes through the `siteFeature`
  extension point
* A new facet MUST start as a new project under `facets/`, NEVER as a package inside the plugin

### The facets

* **Angular Material** - extension for Angular Material (extension point)
* **I18N** - extension for I18N (extension point)
* **Mike** - extension for Mike (complex extension)

### What activating a facet adds to the IDE

* Extends YAML support (intelligence, completion and annotation) in `mkdocs.yml` for the given feature
* Extends file detection in MkDocs module folders

## Installation of a feature

* WHERE a feature is installed is asked of `pip`, through `MkDocsInstallationLocator` of `:utils`, and of
  NOTHING else
    * A facet names the distribution (`mkdocs-material`) and the path inside the package
      (`material/templates/.icons`); the locator answers with the directory
    * FORBIDDEN: searching the checkout for `.venv`, `venv`, `site-packages` or any other directory that
      looks like an environment - pip knows where the packages of the interpreter in use lie
    * The answer of `pip show` is cached in `MkDocsPipService`; anything that can change an installation
      calls `invalidate()`
    * The call starts a process and MUST NOT run on the EDT
* The path a user configures by hand lives in `MkDocsInstallationSettings` of `:utils`, under a key of the
  facet - a facet MUST NOT persist a path of its own
* A settings page offering that path MUST use `MkDocsInstallationComboBox` of `:utils`
    * It shows what was found and stays editable for setups pip cannot answer for
    * The texts are handed in from the resource bundle of the facet

## Plugin descriptor

* A facet is registered as a plugin content module of the V2 model, listed in `plugin.xml` under
  `<content>`
* The module descriptor MUST lie in the resource ROOT of the facet project and MUST be named after the
  module, e.g. `org.pcsoft.ij.plugin.mkdocs.material.xml`
    * Placing it under `META-INF` is FORBIDDEN - the platform does not find it there
* The descriptor MUST carry the `package` attribute naming the Kotlin package of that module, e.g.
  `<idea-plugin package="org.pcsoft.ij.plugin.mkdocs.material">`
    * Without it the IDE dies while loading the plugin: "Package is not specified (module=...)"
    * Every module MUST get a package of its own, and the classes it registers MUST live below it -
      the Markdown completion therefore sits in `…material.markdown`, not in `…material.icon`
* The projects are merged into the plugin jar with `pluginComposedModule(implementation(project(...)))`
    * `pluginModule(...)` is FORBIDDEN: it ships each project as `lib/modules/<gradle-path>.jar`, and the
      loader - which reads a content module by its own name - then fails with
      "Cannot resolve org.pcsoft.ij.plugin.mkdocs.material.xml"
* Functionality that needs another plugin MUST become its own content module with
  `loading="optional"` in `<content>` and a `<dependencies><plugin id="..."/></dependencies>` in its
  descriptor
    * This replaces `<depends optional="true" config-file="...">`
* Every content module gets its own class loader, whose parents are only the modules named in its
  `<dependencies>`
    * An optional module therefore names the base module of its facet next to the IDE plugin it needs
* `plugin.xml` MUST NOT name a class of a facet - only the `<content>` block may name its modules
    * `MkDocsCoreIndependenceTest` checks both the Kotlin sources and the descriptors
* Every user visible text of a facet lives in a resource bundle of its own, declared in that descriptor
    * Naming scheme: `messages/MkDocs<FacetName>Bundle.properties`, e.g. `messages.MkDocsMaterialBundle`
    * The I18N rules of the `i18n` skill apply to it unchanged, translations included

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
* A key of a refined JSON schema that the base MkDocs schema describes as well MUST wrap its definition in an
  `anyOf`, NEVER name it with a bare `$ref`
    * The platform merges the branches of the `allOf` per key, and a bare `$ref` is merged away: measured, the
      popup below `extra` stayed empty, while the same definition inside an `anyOf` is walked and keeps its keys
* Access to the parsed data MUST go through an injectable service, NEVER through a Kotlin `object`
    * Example: `MkDocsMaterialDataService`, annotated with `@Service(Service.Level.APP)`
    * The data classes themselves MUST stay free of Jackson annotations and of any loading logic

## Tests

* A test of a facet lives in that facet project - it can reach the `internal` members of the facet and
  the test helpers next to them, which a test outside the project cannot
* A test that needs the platform to have *registered* something (a facet type, a completion contributor,
  an annotator, an intention) MUST live in the plugin project
    * A module project ships no `META-INF/plugin.xml`, so nothing is registered in its test run
    * The same holds for a test driving the detection of the plugin, e.g. `MkDocsModuleService.sync()`
    * Such a test keeps the package of the feature it covers, below `src/test` of the plugin

### Suggestion lists

Applies to every list a facet contributes to the completion popup of `mkdocs.yml`, no matter where the
entries come from - a `CompletionContributor` of the facet, or the refined JSON schema of the facet.

* EVERY such place MUST be covered by an integration test in the PLUGIN project (class name ends in `IT`)
    * A unit test on the contributor or on the generated schema is NOT enough: whether the entries reach the
      popup is decided by the platform, not by the class producing them
    * The test MUST drive the real completion, e.g. `myFixture.completeBasic()` on a `configureByText`
* EVERY place MUST carry a positive AND a negative case
    * Positive: the values arrive at that place in EVERY shape the key accepts - a sequence, a mapping, an
      entry carrying options, a value below an already written one
    * Negative: nothing is offered one level deeper (the options of an entry), at a neighbouring key of the
      same name, in a file that is not a configuration file, and on a site the facet is not active on
* The expected values MUST be read from the facet's data service, NEVER written as a literal list
    * A value added to `<facet-name>/spec` then has to arrive in the popup without the test being touched
* A list whose entries carry a type text, a tail text or an icon MUST have that rendering asserted through
  `LookupElementPresentation.renderElement`
* A test needing the refined schema MUST detect the site first and drop the cached answer of the schema
  provider afterwards, because the light fixture reuses one project per class
    * `MkDocsModuleService.getInstance(project).sync()` and then `MkDocsMaterialSchemaCache.invalidate(project)`
