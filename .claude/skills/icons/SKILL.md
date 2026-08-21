---
name: Icons
description: Create and handle (use) icons in UI of IntelliJ
---

* `.claude/rules/icons.md` is the single source of truth for icons and MUST be read before an icon is
  drawn, moved, renamed or registered
    * It rules the drawing itself, the 48x48 canvas, the file names, where a file lives, how it is
      registered and which size a rendering place gets
* MUST use the `icon-creator` agent to create icons
