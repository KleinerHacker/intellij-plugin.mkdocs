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
