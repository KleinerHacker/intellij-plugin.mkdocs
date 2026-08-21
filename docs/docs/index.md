# MkDocs – JetBrains Plugin

JetBrains plugin providing MkDocs support in all IntelliJ-platform IDEs.

## Features

- [Automatic module detection](mkdocs-facet.md) — every directory containing an `mkdocs.yml` becomes an MkDocs
  module in the IDE, named after its `site_name`.
- [Site creation](mkdocs-facet.md#creating-a-site) — a five step wizard writing the layout, the site metadata, the
  repository link and the copyright notice, prefilled from Git and from the IDE's Copyright profiles.
- [Configuration file support](configuration.md) — a JSON schema for both spellings of the configuration
  file, and an inspection reporting missing site metadata with a quick fix.
- [Angular Material](angular-material-facet.md) — a site using the Material theme carries a facet of its own, and its
  `mkdocs.yml` is edited against a refined schema covering `theme.features`, `theme.palette`, the fonts, the
  icons and the Material part of `extra`.
- [Site Page tool window](toolwindow.md) — one tab per site, showing the tree written under `nav`, kept up to
  date with the configuration file and the pages behind it.
- [Settings](settings.md) — *Tools → MkDocs*, one page per feature of a site; today the installation of
  *Material for MkDocs* the icons are read from.

## Installation

Install the plugin from the JetBrains Marketplace via
*Settings → Plugins → Marketplace* and search for **MkDocs**.

Alternatively, download the plugin archive from the
[releases page](https://github.com/KleinerHacker/intellij-plugin.mkdocs/releases)
and install it via *Settings → Plugins → ⚙ → Install Plugin from Disk…*.

## Documentation

This documentation is versioned: use the version selector in the header to switch
between releases. The `latest` alias always points at the most recent stable release.

- [Modules](mkdocs-facet.md) — how MkDocs sites are detected and represented in the IDE
- [Angular Material](angular-material-facet.md) — the facet of a site rendered with *Material for MkDocs*
- [Site Page tool window](toolwindow.md) — the navigation of every site of the project
- [Configuration file](configuration.md) — schema mapping and inspections for `mkdocs.yml` / `mkdocs.yaml`
- [Settings](settings.md) — the settings pages under *Tools → MkDocs*
- [API Docs](dokka/html/index.html) — the generated Dokka API reference
- [Licences](licences/index.html) — licences of all bundled third-party dependencies
