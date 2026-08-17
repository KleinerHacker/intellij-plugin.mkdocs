# Feature Plan

Create a **Feature Plan** for the requested feature.

A Feature Plan is a high-level planning artifact that describes **what the feature should achieve and how it should be decomposed into multiple implementation plans**.

Unlike a normal implementation plan, do not describe every individual code change in detail. The purpose is to establish the overall feature architecture, identify dependencies, and divide the work into coherent implementation plans that can later be expanded into detailed implementation plans.

## Output Location

All Feature Plans **must be stored in**:

```text
.claude/plans/features/
```

Before creating a new Feature Plan, inspect the directory and determine the next available sequential number.

Use the following naming convention:

```text
<NUMBER>_<FeatureName>.md
```

For example:

```text
.claude/plans/features/
├── 001_UserAuthentication.md
├── 002_AuditLogging.md
└── 003_UserImport.md
```

The number must:

* be sequential
* use three digits
* be determined from the existing Feature Plans
* use the next available number
* never overwrite an existing Feature Plan

The Feature Plan filename should use a concise, descriptive PascalCase feature name.

## Feature Status

Every Feature Plan must have an associated status.

Prefer storing the status in a separate file:

```text
<NUMBER>_<FeatureName>_status.md
```

For example:

```text
.claude/plans/features/
├── 001_UserAuthentication.md
├── 001_UserAuthentication_status.md
```

The status file must contain the current implementation status of the feature and its individual implementation plans.

The status file should be updated as implementation plans are completed.

Do not duplicate detailed planning information in the status file.

### Status Format

Use the following structure:

```markdown
# Feature Status: <Feature Name>

Status: NOT_STARTED

## Implementation Plans

| ID | Implementation Plan | Status |
|----|---------------------|--------|
| IP-01 | <name> | NOT_STARTED |
| IP-02 | <name> | NOT_STARTED |
| IP-03 | <name> | NOT_STARTED |

## Overall Progress

0%

## Notes

<optional notes>
```

Use only these statuses:

* `NOT_STARTED`
* `IN_PROGRESS`
* `BLOCKED`
* `COMPLETED`

The overall feature status should be derived from the implementation-plan statuses.

Suggested interpretation:

* `NOT_STARTED`: no implementation plan has started
* `IN_PROGRESS`: at least one plan is being implemented
* `BLOCKED`: progress is blocked by an unresolved dependency or problem
* `COMPLETED`: all implementation plans are completed

Calculate the overall progress based on the number of completed implementation plans.

## Process

### 1. Inspect Existing Feature Plans

Before creating the Feature Plan:

1. Inspect `.claude/plans/features/`.
2. Determine the next available feature number.
3. Inspect existing Feature Plans if they are relevant to the requested feature.
4. Avoid creating a duplicate feature.
5. Reuse existing architectural decisions where appropriate.

### 2. Analyze the Existing System

Inspect the relevant parts of the repository before creating the plan.

Understand:

* existing architecture
* relevant modules and components
* existing abstractions
* data flow
* APIs and interfaces
* persistence
* configuration
* tests
* build and deployment structure
* existing conventions and patterns

Do not assume that the requested feature should be implemented according to an idealized architecture.

Prefer the architecture and conventions already established by the project unless there is a clear reason to change them.

### 3. Define the Feature

Describe the feature as a whole.

Establish:

* the problem being solved
* intended behavior
* functional requirements
* technical requirements
* architectural consequences
* affected components
* constraints
* assumptions
* open questions

Focus on the **end state**, not individual implementation steps.

### 4. Identify Implementation Boundaries

Divide the feature into logically coherent implementation plans.

Good boundaries may include:

* a new domain capability
* a new API
* persistence changes
* infrastructure changes
* substantial refactoring
* UI/frontend work
* external integrations
* migration work
* testing work where this constitutes a meaningful independent unit

Avoid creating plans that are merely collections of trivial file changes.

Each implementation plan must represent a meaningful unit of work that can later be expanded into a normal detailed implementation plan.

### 5. Define Dependencies

Determine the dependency graph between implementation plans.

For every implementation plan identify:

* prerequisites
* dependent plans
* whether it can be implemented independently
* interfaces or architectural changes required by other plans

Prefer parallelizable implementation plans where possible.

### 6. Create the Feature Plan

Create the Feature Plan in:

```text
.claude/plans/features/<NUMBER>_<FeatureName>.md
```

Create the corresponding status file:

```text
.claude/plans/features/<NUMBER>_<FeatureName>_status.md
```

Do not modify unrelated files.

## Required Feature Plan Structure

# Feature Plan: <Feature Name>

## 1. Objective

Describe the overall goal of the feature.

## 2. Current State

Describe the relevant existing implementation and architecture.

## 3. Target State

Describe the desired state after the entire feature has been implemented.

## 4. Requirements

### Functional Requirements

List the functional requirements.

### Technical Requirements

List the technical requirements and constraints.

## 5. Architecture

Describe the architectural changes required by the feature.

Include relevant:

* components
* modules
* interfaces
* data flows
* persistence
* external integrations

## 6. Implementation Plan Overview

Create a table:

| ID    | Implementation Plan | Objective | Dependencies |
| ----- | ------------------- | --------- | ------------ |
| IP-01 | ...                 | ...       | -            |
| IP-02 | ...                 | ...       | IP-01        |
| IP-03 | ...                 | ...       | IP-01        |
| IP-04 | ...                 | ...       | IP-02, IP-03 |

## 7. Implementation Plans

For each implementation plan:

### IP-01: <title>

**Objective**

Explain what this plan accomplishes.

**Scope**

Describe what belongs in this plan and what explicitly does not.

**Affected Areas**

List the relevant modules, components, APIs, etc.

**Dependencies**

List prerequisite implementation plans.

**Expected Result**

Describe the state of the system after this plan has been implemented.

**Technical Considerations**

Describe important design decisions, constraints, compatibility considerations, migration concerns, etc.

Repeat for every implementation plan.

## 8. Dependency Graph

Represent the dependency structure explicitly.

Example:

```text
IP-01
├── IP-02
│   └── IP-04
└── IP-03
    └── IP-04
```

## 9. Risks and Open Questions

List architectural risks, unresolved decisions, assumptions, and questions that must be answered before implementation.

## 10. Feature Completion Criteria

Define criteria that must be fulfilled for the entire feature to be considered complete.

These criteria should describe observable outcomes rather than individual implementation tasks.

## Status File Requirements

Create the status file alongside the Feature Plan.

The initial status file must contain all implementation plans identified in the Feature Plan.

Use:

```markdown
# Feature Status: <Feature Name>

Status: NOT_STARTED

## Implementation Plans

| ID | Implementation Plan | Status |
|----|---------------------|--------|
| IP-01 | <name> | NOT_STARTED |
| IP-02 | <name> | NOT_STARTED |
| IP-03 | <name> | NOT_STARTED |

## Overall Progress

0%

## Notes

Feature Plan created. No implementation plan has been started yet.
```

The status file is the authoritative source for the current implementation status of the feature.

When an implementation plan is completed, update its status and recalculate the overall progress.

## Important Rules

* Do not implement anything.
* Do not modify production or source code.
* Do not create commits.
* Do not create normal implementation plans unless explicitly requested.
* Create exactly one Feature Plan for the requested feature.
* Create exactly one corresponding status file.
* Always store both files in `.claude/plans/features/`.
* Never overwrite an existing Feature Plan.
* Always determine the next sequential three-digit number from the existing files.
* The Feature Plan describes the **feature as a whole**.
* Implementation Plans describe the **major units required to implement the feature**.
* Do not artificially split the feature into many small plans.
* Explicitly identify dependencies.
* Prefer parallelizable plans where possible.
* Reuse existing project architecture and conventions.
* If the feature is ambiguous, investigate the codebase first and document unresolved questions.
* Do not silently make major architectural decisions that are not supported by the existing codebase.
* Keep the Feature Plan stable; implementation progress belongs in the status file.
