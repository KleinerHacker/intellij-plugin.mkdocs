---
name: Icons
description: Create and handle (use) icons in UI of IntelliJ
---

* Icons are named by `<name>@<size>.png`, example: `file@16.png`
* All icons are saved to folder `icons` in the project resources folder
    * It is allowed to create subfolders by category, explicitly named by user
* There is a central Icon object `org.pcsoft.ij.plugin.mkdocs.MkDocsIcons`, each category is separated by its own nested object
* Use `icon-creator` agent to create icons