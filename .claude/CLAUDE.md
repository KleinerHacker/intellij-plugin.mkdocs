# Global Rules

## Concurrency

* Concurrent or long-running processes (e.g. `build`, `test`, `verifyPlugin`, `koverXmlReport`)
  MUST ALWAYS be executed through an agent (Task tool)
  * NOT through a background command of the shell
  * The agent returns the result; only the result is reported

## Limiting search

* NEVER decompile or reflect depending on third party class
  * If this is required, ask the user first

## Console / CLI Output

* On Console or in CLI: MUST ALWAYS in GERMANY
* Plans printed on Console MUST ALWAYS in GERMANY

## File Output

* Into files: MUST ALWAYS in ENGLISH

## Skills

A skill listed here carries rules that are binding. It MUST be invoked BEFORE the work starts, not
afterwards - the rules it holds are not repeated anywhere else.

* Facet, MkDocs site feature, content module, `spec` resources -> `facets`
* Icon drawn, moved, renamed, registered or resized -> `icons`
* UI text, resource bundle, translation -> `i18n`
* `verifyPlugin`, IDE matrix, `sinceBuild` / `untilBuild` -> `plugin-verification`
* README or any file below `docs/` -> `docs-site`
* SSL handshake failure in a build -> `java-tls-certificate`
