# Configuration file

What the plugin does with `mkdocs.yml` and `mkdocs.yaml` while you edit them.

## JSON schema

MkDocs accepts both spellings of its configuration file, and the IDE maps a JSON schema to it so keys are
completed and unknown ones are reported. That mapping comes from the bundled SchemaStore catalogue — which
lists `mkdocs.yml` only. A site using the `.yaml` spelling was therefore edited without completion and
without validation, although MkDocs treats the two files identically.

The plugin closes that gap: every file named `mkdocs.yaml` is mapped to the same schema the catalogue maps
`mkdocs.yml` to. The `.yml` spelling is deliberately left alone — two mappings on one file would only compete
with each other.

The schema is not shipped with the plugin. It is named as a remote source, exactly as the catalogue entries
are, so it is downloaded once, cached by the IDE, and stays the same schema `mkdocs.yml` is validated
against instead of ageing inside the plugin.

!!! note "Which schema is in use"
    *Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings* lists the mapping under the
    name *MkDocs*.

### Refined for the Material theme

The MkDocs schema stops where the theme begins: it describes `theme` with a handful of keys and `extra` with
none, which is all MkDocs itself defines. A site rendered with the Material theme keeps most of its
configuration in exactly those two blocks, so a site carrying the *MkDocs Angular Material* facet is edited
against a refined schema on top — the feature flags, the palette, the fonts, the icons and the Material part
of `extra`, each key completed, validated and documented. It comes in front of the MkDocs schema rather than
in place of it, so both are in force. See [Angular Material](angular-material.md#editing-the-configuration-file).

## Paths are references

A path in `mkdocs.yml` is not a piece of text to the plugin, it is a reference to the file or directory it
names:

| Key                              | Points at   | Resolved against |
|----------------------------------|-------------|------------------|
| `docs_dir`, `site_dir`           | a directory | the site root    |
| `theme.custom_dir`               | a directory | the site root    |
| `theme.logo`, `theme.favicon`    | a file      | `docs_dir`       |
| every entry of `extra_css`       | a file      | `docs_dir`       |
| every entry of `extra_javascript`| a file      | `docs_dir`       |
| every target of `nav`            | a file      | `docs_dir`       |

`theme.custom_dir` is read next to `mkdocs.yml` rather than below `docs_dir`: the templates it holds are not
content of the site, they are what renders it. An entry of `extra_javascript` counts in both shapes MkDocs
1.6 accepts — the plain `- extra.js` and the mapping `- path: extra.js` carrying `type` and `defer` next to
it.

That is what MkDocs itself resolves them against, so what the IDE follows is what the build reads. Being a
reference brings everything the platform ties to one: **Ctrl+click** and *Go to declaration* open the target,
completion offers what actually lies there — directories only where a directory is expected, and of the files
only those the key accepts: `*.css` below `extra_css`, `*.js` and `*.mjs` below `extra_javascript`, and image
files such as `*.png`, `*.jpg`, `*.svg` or `*.ico` behind `theme.logo` and `theme.favicon`. A directory is
always offered, so a file lying in a sub directory stays reachable. **Renaming** the
file rewrites the entry, and *Find usages* on a page lists the `nav` entry pointing at it. The rewritten entry
stays relative to the directory MkDocs reads it against, so renaming the stylesheets directory leaves the
`extra_css` entries pointing at their style sheets rather than at a path relative to the site root.

A value naming a file of a type its key cannot use — a page behind `extra_css`, a style sheet behind
`theme.logo` — is reported by the inspection *Path naming a file of the wrong type*, found under
*Settings → Editor → Inspections → MkDocs*. It is a check of its own rather than part of the path check below,
so a project generating an asset into a file without the usual extension can switch it off and keep everything
else. A value carrying no extension at all is never reported: behind `theme.logo` and `theme.favicon` the
Material theme accepts the name of one of its own icons, such as `material/library`, which is no path.

A path leading nowhere is marked in the text as an error, the way an unresolved reference is marked anywhere
else in the IDE, and *Create the missing target* is offered next to it. Only the first segment that leads
nowhere is marked — a `nav` entry reading `old/guide/tuning.md` whose `old` is gone is one mistake, not three.
`site_dir` is the one exception: it names the output of the build, which is not expected to exist before the
site has been built once.

`site_dir` is also the one value that need not lie inside the site at all. It says where the build *writes*,
so a directory beside the checkout, above it through `..` or an absolute one on another volume is perfectly
ordinary — and an absolute value is followed as the absolute path it is. Every other key names a part of the
site, and a part of the site lies inside it.

A target of `nav` leaving the site — an address with a scheme, or a protocol relative one — is left alone.
There is no file behind it, and MkDocs passes it through to the theme unchanged.

### Creating the target

**Alt+Enter** on a path that leads nowhere offers *Create the missing target*. It creates the directories
along the way and puts a directory or an empty file at the end of it, depending on what the key names — a
page, a style sheet and a script become files, `docs_dir` and `theme.custom_dir` become directories. The
value itself is left exactly as written: what is created is what was already asked for.

It is not offered for `site_dir`. That directory is build output, and creating it by hand only produces an
empty directory the next build would have written anyway.

## Gutter icons

Every one of those paths carries an icon in the gutter beside its line; one click opens the target.

A file shows the icon it carries everywhere else in the IDE, which is what makes the line readable at a
glance: a page below `docs_dir` shows the MkDocs page icon, a style sheet named in `extra_css` the style
sheet icon, a logo its image icon. `docs_dir` and `site_dir` show the badge of their directory.

An icon appears only where the target is actually there — an icon opening nothing would say the opposite of
what it shows.

## Path check

Beyond *does it exist*, the plugin checks whether a path can exist at all, because a site is usually built on
a machine other than the one it is written on.

An **error** marks what no file system here would accept:

- a forbidden or a control character
- an empty segment, or a segment ending in a dot or a space
- an absolute path, or one starting with a drive letter
- a `..` climbing out of the directory the path is resolved against

A **warning** marks what works here but breaks elsewhere:

- a backslash as separator — MkDocs reads a path as POSIX
- one of the reserved Windows names, such as `con` or `aux`
- non-ASCII characters or a space inside a segment
- a path longer than a conservative Windows setup accepts

Which of the two a finding gets therefore depends on the operating system the IDE runs on: the same entry is
an error where it cannot work and a warning where it merely will not travel.

The last two errors are not reported for `site_dir`. The build output may lie outside the site, so an absolute
path, a drive letter and a `..` climbing above the site root are all legitimate there; how the name itself is
spelled is checked exactly as everywhere else, because a name no file system accepts stays unusable wherever
the directory lies.

## Missing site metadata

Three keys decide how a built site presents itself:

| Key                | Effect                                                     |
|--------------------|------------------------------------------------------------|
| `site_name`        | the browser title and the name shown in the theme          |
| `site_author`      | the author metadata of every generated page                |
| `site_description` | the description search engines and link previews show      |

MkDocs requires none of them, which is exactly why they are so easily forgotten. The plugin therefore shows
a **banner** at the top of the editor for every missing key — in `mkdocs.yml` and `mkdocs.yaml` only; every
other YAML file in the project is none of its business.

A banner rather than a highlight in the text, because what is reported is precisely what is *not* in the
file: there is nothing to underline. Technically it is a file level annotation, the same mechanism behind
messages such as *This file does not belong to the project*.

Every key gets a banner of its own, carrying a fix of its own, so a site that deliberately omits one of them
can keep the others. The fix adds the key with an empty value — it cannot know what the site is called or who
wrote it, and an invented value is harder to notice than an empty one. The key lands where it belongs among
the other metadata keys, so following all three banners produces the order MkDocs documents rather than the
order you happened to click in.
