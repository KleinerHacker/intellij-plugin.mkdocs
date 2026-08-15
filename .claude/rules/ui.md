---
name: IntelliJ Plugin UI
---

# I18N

* ALWAYS use I18N for UI
* ONLY use I18N for UI texts that are independent of technical code
  * ONLY for plain UI texts

## Languages

* The base bundle `src/main/resources/messages/MkDocsBundle.properties` is ENGLISH and is the single
  source of truth for all keys
* The following translations MUST be present, one file per language, next to the base bundle:
  * `MkDocsBundle_zh_CN.properties` - Simplified Chinese
  * `MkDocsBundle_ja.properties` - Japanese
  * `MkDocsBundle_ko.properties` - Korean
  * These are the languages JetBrains ships an official language pack for
* Adding, renaming or removing a key in the base bundle MUST be applied to EVERY translation in the
  same change
  * FORBIDDEN: a translation carrying a key the base bundle does not have
  * FORBIDDEN: a translation missing a key of the base bundle
* Every file MUST be UTF-8; `\uXXXX` escapes are FORBIDDEN
* MessageFormat placeholders (`{0}`, `{1}`, ...) MUST be kept identical to the base bundle, including
  the doubled apostrophes of `''{0}''`
* Technical identifiers MUST NOT be translated
  * Examples: `mkdocs.yml`, `site_name`, `docs_dir`, `theme.custom_dir`, `pymdownx.*`, `--md-*`,
    product names such as MkDocs, Material for MkDocs, Angular Material, Mike
* Adding another language MUST be confirmed with the user first