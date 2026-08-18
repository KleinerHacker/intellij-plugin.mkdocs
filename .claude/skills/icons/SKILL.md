---
name: Icons
description: Create and handle (use) icons in UI of IntelliJ
---

* Icons MUST be named by `<name>@<size>.svg`, example: `file@16.svg`
    * Dark mode variants MUST be named `<name>@<size>_dark.svg`
* All icons MUST be saved to folder `icons` in the project resources folder
    * It is allowed to create subfolders by category, explicitly named by user
* There MUST be a central Icon object `org.pcsoft.ij.plugin.mkdocs.MkDocsIcons`, each category is separated by its own nested object
* MUST use `icon-creator` agent to create icons
