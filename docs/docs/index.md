# MkDocs – JetBrains Plugin

JetBrains plugin providing MkDocs support in all IntelliJ-platform IDEs.

## Features

- [Automatic module detection](modules.md) — every directory containing an `mkdocs.yml` becomes an MkDocs
  module in the IDE, named after its `site_name`.
- [Site creation](modules.md#creating-a-site) — a five step wizard writing the layout, the site metadata, the
  repository link and the copyright notice, prefilled from Git and from the IDE's Copyright profiles.
- [Configuration file support](configuration.md) — a JSON schema for both spellings of the configuration
  file, and an inspection reporting missing site metadata with a quick fix.

## Installation

Install the plugin from the JetBrains Marketplace via
*Settings → Plugins → Marketplace* and search for **MkDocs**.

Alternatively, download the plugin archive from the
[releases page](https://github.com/KleinerHacker/intellij-plugin.mkdocs/releases)
and install it via *Settings → Plugins → ⚙ → Install Plugin from Disk…*.

## Documentation

This documentation is versioned: use the version selector in the header to switch
between releases. The `latest` alias always points at the most recent stable release.

- [Modules](modules.md) — how MkDocs sites are detected and represented in the IDE
- [Configuration file](configuration.md) — schema mapping and inspections for `mkdocs.yml` / `mkdocs.yaml`
- [API Docs](dokka/html/index.html) — the generated Dokka API reference
- [Licences](licences/index.html) — licences of all bundled third-party dependencies
