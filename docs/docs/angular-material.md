# Angular Material

A site rendered with the [Material theme](https://squidfunk.github.io/mkdocs-material/) carries the
*MkDocs Angular Material* facet, next to the *MkDocs* facet of the same module and visible in
*Project Structure → Facets*. It appears only on a module that holds an MkDocs site, and it is removed
together with the *MkDocs* facet when the site disappears.

The dialog lists facets flat rather than as a tree, so the name and the icon are what tie the two together:
the facet is named after MkDocs and sorts directly beside it, and it wears the MkDocs logo with the Material
glyph badged into the corner.

The facet and the configuration file say the same thing, and either one can be the one that changes:

| You change | What happens |
|------------|--------------|
| `theme.name: material` appears in `mkdocs.yml` | the next detection run adds the facet |
| the theme is taken out or switched to another one | the detection removes the facet again |
| the facet is added in *Project Structure* | `theme.name: material` is written into `mkdocs.yml` |
| the facet is removed in *Project Structure* | the whole `theme` key is taken out of `mkdocs.yml` |

Nothing is written when the file already says what the facet says, so a facet the detection itself created
leaves the file untouched.

## How the theme is read

Both shapes MkDocs accepts are recognised, and the name is compared without regard to case:

```yaml
theme:
  name: material
```

```yaml
theme: material
```

## What the facet writes

Only the theme name is touched. Settings written next to it survive:

```yaml
theme:
  name: material     # ← replaced
  highlightjs: true  # ← kept
```

A `theme` written as a plain scalar has nowhere to keep the name, so adding the facet turns it into a
mapping. A file without a `theme` at all gets the key appended.

Removing the facet removes the `theme` key as a whole, and MkDocs falls back to its built-in theme.

## Editing the configuration file

MkDocs itself knows next to nothing about the theme it renders with. Its schema describes `theme` with a
handful of keys and `extra` with none at all, which is correct for MkDocs and of very little use here: those
two blocks are where a Material site keeps most of its configuration. A site carrying the facet is therefore
edited against a **refined schema**, which describes them properly — so the keys are completed, an unknown one
is reported, and every offered value comes with a line saying what it does.

What the refinement covers:

| Block | What it describes |
|-------|-------------------|
| `theme.features` | all 28 feature flags of the theme, each with its own description |
| `theme.palette` | `scheme`, `primary`, `accent`, `media` and `toggle`, in both shapes the theme accepts |
| `theme.font` | the text and the code font |
| `theme.language`, `theme.direction` | the language of the built-in labels and the reading direction |
| `theme.icon` | the icons the theme lets you replace — logo, repository, edit, menu, search and the rest |
| `theme.logo`, `theme.favicon`, `theme.custom_dir` | the assets and the override directory |
| `extra` | the Material part of it: `social`, `analytics`, `consent`, `generator` and `status` |

The palette is written either as a single mapping or as a sequence of them, the latter being what gives a site
its light/dark toggle. Both forms are described the same way, because a colour offered in one and missing in
the other would be an arbitrary difference. The colour names are the ones the theme actually ships, and
primary and accent get their own sets — the accent set is the shorter one, the theme having no accent for the
neutral colours.

The refinement is bound to the facet. A site on another theme keeps the plain MkDocs schema and is offered
nothing the theme rendering it would not read; take the theme out of `mkdocs.yml` and the refinement goes with
it. Where it does apply it does not replace the MkDocs schema but stands in front of it — both are in force,
so the keys MkDocs defines keep their completion and their documentation exactly as before.

Two keys under `extra` are deliberately left open:

| Key | Left to |
|-----|---------|
| `extra.version` | the planned Mike feature |
| `extra.alternate` | the planned I18N feature |

They are not part of the theme, and reporting them as unknown while the features that own them are still being
built would be wrong in the one direction that matters — a warning on a line that is perfectly correct.

!!! note

    The MkDocs schema the refinement builds on is bundled with the plugin instead of being fetched, so the
    completion in a Material site works offline and cannot change under you between two IDE starts.

## In the creation wizard

*New → MkDocs Site* offers **Angular Material** in its feature step. Switching it on writes the theme into
the configuration file of the new site and attaches the facet right away, so the finished site carries it
without waiting for the next detection run.

!!! note

    The facet records what `mkdocs.yml` declares — it does not install the theme. The
    `mkdocs-material` package still has to be available to the MkDocs build itself.

## Markdown extensions

The theme renders a plain site without a single entry under `markdown_extensions`, so nothing is required
just because it is absent. What *is* required follows from the configuration itself: a feature flag such as
`content.code.annotate` renders nothing without `pymdownx.superfences`, `attr_list` and `md_in_html`.

Those forced extensions are reported as an **error** in a banner above `mkdocs.yml`, one per extension, each
with a fix that adds it — together with the options it needs, because `pymdownx.tabbed` without
`alternate_style: true` renders the old tab style and looks broken.

Everything the theme merely builds on — call-outs, key caps, highlighted code — is offered separately as a
**weak warning**, and that inspection can be switched off entirely under *Settings → Editor → Inspections →
MkDocs*. Keeping the Markdown of a site plain is a decision, not a defect.

Pressing ++ctrl+q++ on an entry of `markdown_extensions` explains what the extension does and links to its
own documentation.

## Icons

The icons the theme offers are the SVG files of the installed `mkdocs-material` package, so they are read
from the installation rather than carried as a list. They are completed in three places of `mkdocs.yml` —
`theme.icon.*`, the `toggle.icon` of a palette and the `icon` of an entry of `extra.social` — and in the
pages of the site as the shorthands `:material-check:`, `:fontawesome-brands-github:` and their like. Each
entry shows the drawing next to the name.

The installation is looked for next to the site, in `.venv`, `venv`, `env` and `.virtualenv`, in the Windows
and the POSIX layout alike. For every other setup — a system wide interpreter, a container mount — the
directory can be named under *Tools → MkDocs*; see [Settings](settings.md).

!!! note

    Without an installed `mkdocs-material` nothing is offered. That is the normal state of a fresh checkout
    whose virtual environment has not been created yet, and not an error.

## Style sheets

A site is restyled beyond the palette by redefining the custom properties of the theme in a style sheet
listed under `extra_css`. All of them are completed inside CSS files, each with the part of the page it
paints.

## Where a key comes from

A configuration file of a Material site mixes two vocabularies. Some keys are read by MkDocs and work with any
theme; others exist only because this theme renders the site, and would be silently ignored the moment the
theme changes. Nothing in the file says which is which.

The plugin says it. An icon sits in front of every key the theme alone reads — `theme.features`,
`theme.palette`, `theme.font`, `theme.icon`, `theme.direction` and the theme's own keys below `extra` — and
its tooltip states that MkDocs itself does not read the key. The hint is an inlay hint like any other and can
be switched off under *Settings → Editor → Inlay Hints*.

What MkDocs reads stays unmarked, on purpose: `theme.name` names the theme, `theme.logo`, `theme.favicon` and
`theme.custom_dir` are part of the theme contract of MkDocs, and `markdown_extensions` is a top level key of
MkDocs. The theme uses all of them, but it does not own them.

The same icon appears on the completion entries that come from the theme — in `mkdocs.yml`, on the icon
shorthands in the pages and on the custom properties in the style sheets. An entry that already shows a
drawing of its own, such as an icon name, keeps the drawing and carries the mark as a small badge on it.

## Template overrides

*Material Template Overrides…* in the context menu of a site root creates what an override needs, in one
undoable step: the override directory, the selected files at exactly the path the theme reads them from, a
Jinja scaffold inside each of them, and `theme.custom_dir` pointing at the directory. A file that is already
there keeps its content.

Offered are `main.html` and the partials for header, footer, navigation, copyright notice and logo. The
scaffold of `main.html` extends `base.html` and opens a block with `{{ super() }}` in it; a partial is
*replaced* rather than extended, which its scaffold says, because MkDocs includes the file of `custom_dir`
instead of the original.

Live templates for the Jinja constructs come with it: `mext`, `mblock`, `mblockr`, `minc` and `mif`.
