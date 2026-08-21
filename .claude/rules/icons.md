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
* The `icon-creator` agent MUST be used to draw an icon
  * It returns the SVG; writing, moving and renaming the files happens through git, as `git.md` rules

## Where an icon lives

* An icon file lies in the `icons` folder of the resources of the module shipping it
  * The plugin: `plugin/src/main/resources/icons`
  * `:utils`: `utils/src/main/resources/icons`
  * A facet: below the `<facet-name>/` prefix its resources carry anyway, e.g. `material/icons` - the
    prefix is mandatory and ruled by `facets.md`
* Subfolders by category are allowed ONLY where the user named them
* Naming is `<name>.svg` and `<name>_dark.svg`, NEVER a size - see the rules above
* A drawing that more than one module renders MUST live in `:utils`
  * A facet MUST NOT reach into the plugin for an icon, and `MkDocsCoreIndependenceTest` checks that
  * Today: `MkDocsIconLoader.Logo` - the MkDocs logo a facet badges - and `MkDocsIconLoader.Folder` -
    the folder standing for a level in a completion popup

## Registration

* An icon is reached through the `*Icons` object of its module, NEVER loaded at the place rendering it
  * The plugin: `MkDocsIcons`; a facet: `MkDocs<FacetName>Icons`
  * Where that object lies is ruled by `architecture.md`: the package root of its module
* An icon of `:utils` is the one exception - `MkDocsIconLoader` hands it out itself, and the `*Icons` object
  of a module rendering it points at that field instead of loading the file a second time
* A composed icon - a base carrying an overlay badge - is composed through `MkDocsIconLoader.withBadge`
  * Where both parts are icons of this plugin it is composed ONCE, in the `*Icons` object, e.g.
    `MkDocsMaterialIcons.Group`
  * FORBIDDEN: composing such a one per rendered element, e.g. inside a `LookupElementRenderer`
  * Where the base comes out of the user's environment - a drawing of an installed theme - it is composed
    where it is rendered, because there is no fixed set of them to compose in advance

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
