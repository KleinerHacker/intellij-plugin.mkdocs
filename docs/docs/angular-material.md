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

## In the creation wizard

*New → MkDocs Site* offers **Angular Material** in its feature step. Switching it on writes the theme into
the configuration file of the new site and attaches the facet right away, so the finished site carries it
without waiting for the next detection run.

!!! note

    The facet records what `mkdocs.yml` declares — it does not install the theme. The
    `mkdocs-material` package still has to be available to the MkDocs build itself.
