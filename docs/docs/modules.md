# Modules

The plugin turns every MkDocs site in your project into a *module* the IDE knows about. This is the
foundation everything else builds on: optional MkDocs extensions are added on top of a module, the same way
a framework is added to a Java module.

## How a module is detected

A directory is an MkDocs module when it directly contains one of the MkDocs configuration files:

- `mkdocs.yml`
- `mkdocs.yaml`

The *directory above the configuration file* is the module — not the configuration file itself, and not the
`docs_dir` inside it.

Detection runs automatically:

- once when the project is opened, and
- again after every change to the file system that could affect it (creating, deleting, renaming or moving a
  configuration file or a directory).

There is nothing to enable and nothing to import.

!!! note "Ignored directories"
    Build outputs and dependency caches are skipped, so a configuration file copied into `build/`,
    `site/`, `node_modules/`, `dist/`, `target/`, `out/`, `__pycache__/`, a virtual environment or a VCS
    directory never creates a module.

## The MkDocs facet

A detected module is marked with the **MkDocs** facet. You can see it in
*File → Project Structure → Modules → &lt;module&gt; → Facets*, showing:

| Field              | Meaning                                                      |
|--------------------|--------------------------------------------------------------|
| Site name          | `site_name` from the configuration file                      |
| Configuration file | the detected file, relative to the module root               |

Both values are read-only: the configuration file is the single source of truth. Change `site_name` in
`mkdocs.yml` and the facet follows.

## In the project view

The site root is marked wherever it appears in the project view:

- the site name is shown in brackets behind the directory name — exactly the way a Maven project directory
  is rendered, so a site is recognisable without opening anything, and
- the folder icon carries a small MkDocs badge in its lower right corner.

The name in brackets is the `site_name` stored in the MkDocs facet, so it changes as soon as the
configuration file does. Only the site root itself is marked, never the directories below it, and never a
directory whose module has not been detected yet.

The configuration file itself carries a dedicated MkDocs icon instead of the generic YAML one — in the
project view, in editor tabs and in navigation popups alike. Its file type stays YAML, so all YAML support
keeps working; only the icon changes.

## Module name

The module name is taken from `site_name`. If the key is missing or empty, the name of the directory
containing the configuration file is used instead. Should two sites end up with the same name, the second one
gets a `~2` suffix.

## Which module the facet lands on

| Situation                                                | Result                                        |
|----------------------------------------------------------|-----------------------------------------------|
| The site directory belongs to an existing module          | that module receives the MkDocs facet         |
| The site directory belongs to no module at all            | a new module is created for the site directory |

Reusing the surrounding module is deliberate: it keeps the module layout that Gradle, Maven or the .NET
solution import produced intact. A module created by the plugin is removed again as soon as its configuration
file disappears.
