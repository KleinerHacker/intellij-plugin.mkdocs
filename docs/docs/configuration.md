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
