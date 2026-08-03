# Eigenes Datei-Icon fuer mkdocs.yml / mkdocs.yaml

## Aufgabe 1: Plan-Ablage

* Plan als `.claude/plans/ConfigFileIcon.md` anlegen
* Statusdatei `.claude/plans/ConfigFileIcon-status.md` anlegen
* Abgeschlossene Plaene `ProjectViewDecorator*.md` entfernen
* Plan-Dateien NICHT zu GIT hinzufuegen

## Aufgabe 2: Icon erzeugen

* `icon-creator`-Agent fuer Konfigurationsdatei-Icon beauftragen
* Groessen 16x16 und 32x32
* Motiv: Dokumentseite mit MkDocs-Marke, klar vom YAML-Standardicon unterscheidbar
* Eigene Farbvariante fuer Dunkelmodus mit ausreichendem Kontrast
* Namensschema `mkdocs-file@16.png` / `mkdocs-file@16_dark.png`, analog fuer `@32`
* Ablage in `src/main/resources/icons`
* Dateien per `git add` aufnehmen

## Aufgabe 3: Icon-Registry erweitern

* `MkDocsIcons` um `ConfigFile` und `ConfigFileLarge` ergaenzen
* KDoc fuer neue Felder schreiben

## Aufgabe 4: Icon-Provider implementieren

* Neue Klasse `module/MkDocsFileIconProvider.kt`
* Implementiert `com.intellij.ide.FileIconProvider`
* Nur Dateien, keine Verzeichnisse verarbeiten
* Erkennung ueber `MkDocsProject.isConfigFile(file.name)`
* Rueckgabe `MkDocsIcons.ConfigFile`, sonst `null`
* Datei per `git add` aufnehmen

## Aufgabe 5: Registrierung

* `fileIconProvider`-Extension in `plugin.xml` eintragen
* Kommentar zur Funktion ergaenzen

## Aufgabe 6: Tests

* `MkDocsFileIconProviderTest` (Developer-Test) fuer Provider-Logik
* Faelle: `mkdocs.yml`, `mkdocs.yaml`, Grossschreibung, andere YAML-Datei, Verzeichnis
* KDoc je Testmethode mit Use-Case-Beschreibung
* Datei per `git add` aufnehmen

## Aufgabe 7: Dokumentation

* `CHANGELOG.md` unter `[UNRELEASED]` ergaenzen
* `README.md` Feature-Liste ergaenzen
* `docs/docs/modules.md` Abschnitt zur Projektbaum-Darstellung erweitern
* `STATUS.md` aktualisieren

## Aufgabe 8: Verifikation

* Gradle `build` ueber Agent ausfuehren
* Gradle `verifyPlugin` ueber Agent ausfuehren
* DEPRECATION-/REMOVAL-Warnungen beheben
* `.claude/plans` nach Abschluss aufraeumen
