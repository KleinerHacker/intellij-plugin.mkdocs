---
name: architecture
---

# Architecture 

## Module

The build is a multi module Gradle build. The root project is a pure AGGREGATOR; every project below it
carries code. The split is done - the rules below describe what is, and every change MUST keep it that way.

* The root project holds NO production code and NO plugin descriptor
    * It applies only `org.jetbrains.dokka` and `org.jetbrains.kotlinx.kover`
    * It aggregates the cross-project concerns: the Dokka publication over every project, the coverage
      merge, the licence report hand-off and the MkDocs documentation site (`runDocs`, `buildDocs`,
      `deployDocs` and the `install*` tasks)
    * Moving plugin code, `plugin.xml`, signing, publishing or the verification matrix back into the root
      is FORBIDDEN
* `:plugin` applies `org.jetbrains.intellij.platform` and owns `META-INF/plugin.xml`, the signing, the
  publishing and the `pluginVerification` matrix
    * It holds the plugin implementation: module system, schema, inspections, build, references, UI
    * It MUST stay a LEAF of the project tree - a project MUST NEVER be nested below `plugin/`
        * The licence-report plugin reports over `project + subprojects`, and resolving the
          `runtimeClasspath` of a foreign project at execution time fails under Gradle 9 with
          "Resolution of the configuration ':utils:runtimeClasspath' was attempted without an exclusive
          lock". A leaf keeps the report inside `:plugin`, whose `runtimeClasspath` already carries every
          external artefact the modules pull in
* Every other project applies `org.jetbrains.intellij.platform.module` and produces a plain jar
    * It does so through the convention plugin `mkdocs.module-conventions` of the included build
      `build-logic`, which also brings the shared Kotlin toolchain, the bytecode level, the test split,
      Dokka and the quality gates (`mkdocs.kotlin-conventions`, applied by `:plugin` as well)
* `:plugin` pulls every module in with `pluginComposedModule(implementation(project(":...")))`, which
  puts it on the compile classpath AND merges its jar into the published plugin
* The module layout is:

```
mkdocs/
├── build.gradle.kts               the aggregator: Dokka, Kover, licences, MkDocs site
├── build-logic/                   the convention plugins, an included build
├── plugin/                        the plugin: implementation + plugin.xml, a LEAF project
├── utils/                         shared model and helpers, no project dependency
└── facets/
    ├── api/                       the facet contract, compileOnly on :utils
    └── <facet-name>/              one project per facet, e.g. material
```

* The dependency arrows are:
    * `:utils` is the only leaf - it MUST NOT depend on any other project
    * `:facets:api` depends on `:utils` with `compileOnly`, and on nothing else
        * `MkDocsSiteTemplate.validate` checks the directory names with `MkDocsProject`; every real user
          of the API carries `:utils` anyway, so the API never drags it into a runtime classpath
        * A dependency of any other kind MUST be confirmed with the user first
    * A facet depends on `:facets:api` and `:utils`, NEVER on the plugin
    * The plugin depends on `:facets:api`, `:utils` and every facet, the latter only to package them
* `:facets:api` holds ONLY the extension contract and the DTOs of its own signatures
    * Today: `MkDocsSiteFeature`, `MkDocsFeatureWizardStep`, `MkDocsSite`, `MkDocsSiteTemplate`
    * Implementation, services and UI are FORBIDDEN there
* `:utils` holds what more than one facet needs and what carries no facet knowledge
    * Today: `MkDocsProject`, `MkDocsConfig`, `MkDocsConfigWriter`, `MkDocsConfigEditScope`,
      `MkDocsSiteFiles`, `MkDocsFacetSync`, `MkDocsIconLoader`
    * The vendored MkDocs base schema lives here too, together with the `refreshMkDocsSchema` task that
      renews it - a facet refines that schema and must not reach into the plugin for it
* For any deviation the user MUST be asked

### Facets

* Everything a facet is made of - the plugin descriptor of its content module, its resources, its tests and
  the hand-off to the plugin - is ruled by `facets.md`

## Structure

* The root package is ALWAYS `org.pcsoft.ij.plugin.mkdocs`
    * Every project carries its own branch below it: `…mkdocs.api` for `:facets:api`, `…mkdocs.utils`
      for `:utils`, `…mkdocs.<facet-name>` for a facet, everything else for the plugin
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

* The resource rules of a facet - the `<facet-name>/` prefix, the `spec` data files and how they are read -
  live in `facets.md`
