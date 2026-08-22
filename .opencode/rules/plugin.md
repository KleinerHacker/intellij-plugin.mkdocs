---
name: IntelliJ Plugin API
---

# Plugin API Rules

* NEVER use internal marked members

* NEVER use deprecated members
* NEVER use members marked for removal

## Services

* A class marked with `@Service` MUST NEVER implement a singleton - no `getInstance`, no `INSTANCE`, no `object`
* Access is ALWAYS `service<T>()` or `project.service<T>()`, with the type argument written out
* Applies to production and test code alike
