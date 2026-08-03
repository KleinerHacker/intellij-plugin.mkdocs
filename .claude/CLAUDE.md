# Global Rules

## Programming

* Kotlin MUST ALWAYS be used
* Gradle MUST ALWAYS be used

## Structure

* The root package is ALWAYS `org.pcsoft.intellij.plugin.mkdocs`
* Below that there are specialized packages for:
  * `services` - Injectable services (IntelliJ Platform)
  * `types` - General types
  * `settings` - Settings features, settings pages, ...
  * `build` - Everything around building MKDocs, e.g. run config, building, ...
* For any deviation the user MUST be asked

## Building

* A build MUST always be performed with the Gradle target `build` after every change
* A plugin verification MUST always be performed with the Gradle target `verifyPlugin` after every change
  * The verification MUST pass without errors, but MAY contain warnings
  * If a DEPRECATION or REMOVAL warning is reported, the code MUST be adjusted
  * If an error is detected, the code MUST be adjusted
  * If the cases above cannot be fixed, the user MUST be asked what to do, together with a list of prepared solution proposals

## Testing

* The IntelliJ plugin test system MUST be used
* Every use case MUST be tested
* Code coverage should reach at least 90%, ideally 100% where possible
* The package structure of the production code is to be mirrored
* Tests are to be split into two categories
  * **Developer tests** - Simple unit tests covering individual pieces of functionality
  * **Integration tests** - Tests covering complete features or aiming at performance

## Documentation

### Code

* EVERY public member in EVERY source file (except automatically generated source files) is to be documented with KDoc
* EVERY test method is to be documented with a detailed KDoc describing the use case

### Readme

* There MUST be instructions on how to check out, build and run the project
* EVERY feature must be documented in a bullet point
* There MUST be instructions on how to consume the artifacts
* There MUST be a short outline of the "WHAT" of the project
* There MUST be a reference to the MkDocs documentation (gh-pages), the API documentation and the licence report
* The readme MUST be checked automatically after changes and adjusted if required

### MkDocs

* MkDocs MUST be integrated under `docs`
* Structure:
  * `docs/mkdocs.yml` - Root file
  * `docs/docs` - *.MD files
  * `docs/docs/assets` - Further asset files (MUST reside inside `docs_dir` so that MkDocs ships them)
  * `docs/docs/stylesheets` - Additional CSS files
* The documentation MUST be checked after every change and adjusted if necessary

### CHANGELOG.md

* A change file MUST be present
* It MUST be updated with the applied changes after a change
  * The changes MUST be visible to the user, otherwise they MUST NOT go into the changelog
* The prescribed format MUST be kept
  * New entries MUST go under `[UNRELEASED]`

### STATUS.md

* The current implementation status against the MKDocs documentation MUST be recorded here

### External Documentation

* All MKDocs documentation is available here: `https://jrsoftware.org/ishelp/`

## Planning

* A plan MUST be created for EVERY change
  * A switch to plan mode MUST happen
* The PLAN MUST ALWAYS be written in GERMAN - both the plan file and the console output
  * This applies to headings, bullet points and every other text of the plan
* The PLAN MUST NOT contain a summary or explanation of the changes
  * FORBIDDEN sections: "Context", "Background", "Summary", "Overview", "Rationale", "Trade-offs"
  * FORBIDDEN: prose paragraphs of any kind - the plan consists of bullet points ONLY
* The implementation tasks MUST be explained in short bullet points with no more than 20 words per bullet and a maximum of 10 bullets per task
  * A bullet describes WHAT is done, NOT WHY
* Before leaving plan mode the plan MUST be checked against ALL rules above
* The plan MUST be written into the local `.claude/plans` directory, together with a status file
  * Naming scheme:
    * Plan: `<Name>.md`
    * Status: `<Name>-status.md`
  * The status MUST ALWAYS be kept up to date
* When restarting an existing plan after an interruption, plan mode MUST be entered
  * The remaining items are laid out again according to the prescribed scheme

## Implementation

* All changes to a single file MUST be applied at once (in a single write operation)

## Concurrency

* Concurrent or long running processes (e.g. `build`, `test`, `verifyPlugin`, `koverXmlReport`)
  MUST ALWAYS be executed through an agent (Task tool)
  * NOT through a background command of the shell
  * The agent returns the result; only the result is reported

## GIT

* All changes are made through GIT:
  * Rename / move: `git mv`
  * Delete: `git rm`
  * Create: add with `git add` after creation
* Commits, pushes, pulls or any other actions communicating with the Git server MUST NEVER be invoked.
  * Should it be required, the user MUST be asked
* Exceptions:
  * NEVER add plans or plan status
  

### Target Environment

* GitHub MUST be used
* All files around GitHub reside in `.github`
* For deeper structural changes the pipeline MUST be checked and adjusted if necessary

#### Pipeline

* There MUST be a pipeline for the regular build in `ci.yml`
  * It contains: [Build] Build -> Test -> Verify plugin, [Verify] Licences / Signature / Build and verify MkDocs
  * `Build` and `Verify` MUST be processed in parallel; everything within `Verify` MUST also run in parallel
* There MUST be a pipeline for a tag based release named `release.yml`
  * It contains: [Changelog] Verify against version -> ([Build] Build -> Test -> Verify plugin, [Verify] Verify licences / Create signature, [MkDocs] Build -> Deploy, [Release] Push artifacts / Deploy to marketplace -> Write release)
  * `Changelog` runs first
  * `Build`, `Verify`, `MkDocs` in parallel afterwards
    * `Verify` internally in parallel as well
  * `Release` at the end
    * Deployment errors MUST be ignored, but shown as a warning

## Limiting search

* NEVER decompile or reflect depending third party class
  * If this is required, ask the user first

# Claude Code

## Console / CLI Output

* On Console or in CLI: MUST ALWAYS in GERMANY
* Plans printed on Console MUST ALWAYS in GERMANY

## File Output

* Into files: MUST ALWAYS in ENGLISH