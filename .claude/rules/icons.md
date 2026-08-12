---
name: Icons
---

# Icon Creation

* MUST use `docs/docs/images/file.png` as a reference for your design in color, stroke, shapes, curved, fills, ...
* MUST create each icon in SVG format - vectors scale, so there MUST be exactly ONE file per motif
  * FORBIDDEN: additional files that only differ in target size (`@16`, `@32`, ...)
  * FORBIDDEN: a size suffix in the file name
  * The internal canvas MUST ALWAYS be 48x48: `width="48" height="48" viewBox="0 0 48 48"`
  * The drawing MUST still read at the smallest size it is rendered at (16x16), so the EFFECTIVE
    width of strokes and gaps MUST NOT fall below 3 units
* An ADDITIONAL file is allowed ONLY if the drawing itself differs, not its resolution
  * Example: an overlay badge, which needs a simpler shape than the full icon
  * Such a file MUST carry a suffix naming its purpose, e.g. `-overlay`, NEVER a size
  * The user MUST be asked before such a variant is added
* MUST if it is required by contrast, MUST create own icons for dark and light mode (with darker oder brighter colors)
