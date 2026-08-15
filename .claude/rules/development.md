---
name: development
---

# Development

* NEVER run `verifyPlugin` on your own initiative
    * It MAY ONLY be run when the user explicitly asks for it
    * An explicit request overrides every other rule that would trigger a verification

## Planning

* A plan MUST be created for EVERY change, ALWAYS ask the user to create a plan or not
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
* After plan is finished cleanup `.claude\plans` folder

## Implementation

* Kotlin MUST ALWAYS be used
* Gradle MUST ALWAYS be used

* All changes to a single file MUST be applied in ONE single tool call
    * Before editing, ALL required changes to that file MUST be collected and planned completely
    * Then the file is written EXACTLY ONCE - with the `Write` tool (full content) or with a
      SINGLE `Edit` call
    * FORBIDDEN: several `Edit` calls on the same file, one after another, for the same change
    * FORBIDDEN: incremental "edit -> read -> edit again" cycles on the same file
    * If a change to file A reveals a follow-up change in file A, the file MUST NOT be patched
      again - the complete new content MUST be written in one operation instead
    * This rule applies per file, NOT per task: several DIFFERENT files MAY be edited in
      parallel, each with exactly one call

## Building

* A build MUST always be performed with the Gradle target `build` after every change
* A plugin verification with the Gradle target `verifyPlugin` MUST only be performed when the user explicitly asks for it
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