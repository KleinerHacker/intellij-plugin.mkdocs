---
name: documentation
---

# Documentation

## Code

* EVERY public member in EVERY source file (except automatically generated source files) is to be documented with KDoc
* EVERY test method is to be documented with a detailed KDoc describing the use case

# Plugin Description

* Update all supported features ALWAYS in plugin.xml
  * List MUST be up to date

## CHANGELOG.md

* A change file MUST be present
* It MUST be updated with the applied changes after a change
    * ONLY changes an END USER of the plugin can see or notice in the IDE belong there
    * FORBIDDEN entries: tests of any kind and their coverage, refactorings, renamings, moved code,
      build and CI changes, changes to the rules under `.claude`, changes to the documentation itself
    * If a change produces no such entry, the changelog MUST stay untouched - an entry MUST NOT be invented
      to have written one
* The prescribed format MUST be kept
    * New entries MUST go under `[UNRELEASED]`

## STATUS.md

* The current implementation status against the MKDocs documentation MUST be recorded here
