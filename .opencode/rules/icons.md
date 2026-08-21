---
name: Icons
---

# Icon Creation

* MUST use `../../docs/docs/assets/images/file.png` as a reference for your design in color, stroke, shapes, curved, fills, ...
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

## Icon Size

* The size of an icon MUST ALWAYS be adjusted to the place that renders it
  * The 48x48 canvas of the file is the drawing, NEVER the size a place renders at
  * The adjustment happens ONLY on loading, in `MkDocsIconLoader.load(path, size, owner)`, on the vector -
    NEVER by adding a file of another resolution
* EVERY icon MUST leave `load` with its size fixed, wrapped in `FixedSizeIcon`
  * Observed: without it an inlay hint paints the icon at 48x48, whatever size `load` set
  * FORBIDDEN: removing the wrapper without checking every place the icon is rendered at
* The size MUST follow the place, not the other way round
  * Tool window stripe: 20
  * Lists, gutter, completion popup, facet, file icons: 16
  * Editor inlay: 12 - an inlay sits inside a line and MUST stay below the line height
  * Overlay badge on another icon: 8

### Inlay Hint

* Observed with `MkDocsMaterialIcons.Inlay`, and to be kept unless measured otherwise:
  * `PresentationFactory.smallScaledIcon` and `ScaleAwarePresentationFactory` paint the icon at 48x48
  * `PresentationFactory.icon(...)` paints it at the size `load` set, but sits on the text baseline
  * `factory.inset(factory.icon(...), top = 5, right = 1)` places it in the middle of the line
* A changed size or a new inlay place MUST be looked at in a running IDE before the numbers are
  written down here
* A new place MUST get its own size argument; reusing an entry of another size is FORBIDDEN
* If a place renders an icon too large or too small, the size argument of `load` MUST be corrected,
  NEVER the SVG
