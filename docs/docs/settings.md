# Settings

The settings of the plugin live under *Tools → MkDocs*. That page holds nothing itself — every setting
belongs to one feature of a site, and each feature carries a page of its own below it. Today there is one:
*Material*. Its setting belongs to the project rather than to the IDE, because it points into the environment
of *this* project.

## Material

### Detected installation

The page states where it found the installed *Material for MkDocs*, or that it found none. That line is the
answer to the question an empty completion popup raises: whether the plugin knows the installation at all.

### Icon directory

The icons of *Material for MkDocs* are not a list the plugin carries — they are the SVG files shipped inside
the installed package, so which ones exist depends on the version that is installed. The plugin finds them
by itself in the virtual environments a project normally keeps next to its sources:

* `.venv`, `venv`, `env` and `.virtualenv`,
* in the Windows layout `Lib/site-packages` and in the POSIX layout `lib/python3.x/site-packages`,
* and inside the package at `material/templates/.icons`.

Searched is not only the site root but every directory from it up to the root of the project, which is the
layout of a site living in `docs/` while the environment lies next to the sources. The walk stops at the
project, never above it.

Leave the field empty while one of those applies. Fill it in for every other setup — an interpreter outside
the project, a system wide installation, a directory mounted from a container. The path names the directory
holding the sets, the one containing `material`, `fontawesome` and `octicons`.

!!! note

    A configured path that no longer exists falls back to the search rather than switching the icons off, so
    a moved environment degrades into the default behaviour instead of into silence.

Applying the page throws the icon index away, so a corrected path takes effect in the next completion popup
rather than after a restart. The index is also refreshed on its own whenever something below a
`site-packages` directory changes — installing or upgrading `mkdocs-material` while the IDE is open is
enough.
