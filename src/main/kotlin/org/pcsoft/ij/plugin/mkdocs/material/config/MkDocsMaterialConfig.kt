/*
 * Copyright (c) KleinerHacker alias Pfeiffer C Soft 2026.
 * This work is licensed under the Apache License, Version 2.0.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, this software is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations.
 */

package org.pcsoft.ij.plugin.mkdocs.material.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigEditScope
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigWriter

/**
 * Reads a [MkDocsMaterialSettings] out of an MkDocs configuration file and writes changes to it back.
 *
 * Writing is a diff, never a render: [write] compares the snapshot the page was opened with against the one
 * the user leaves it with and touches exactly the keys that differ. The model is lossy — it does not know
 * about the options below a Markdown extension, about a key of another plugin next to `theme.name`, about the
 * comments an author wrote — and rendering it back into the file would throw all of that away on the first
 * *Apply* that changed a single language code.
 *
 * The same care governs the palette. `theme.palette` has shapes beyond the two this model covers, and a shape
 * it cannot represent switches the snapshot to [MkDocsMaterialSettings.editable] `false`: the settings page
 * then shows the palette read-only and [write] leaves the key alone entirely, rather than replacing a hand
 * tuned palette with an approximation of it.
 *
 * [read] must be called inside a read action, [write] inside a write action — the caller owns both, because a
 * change to the configuration belongs into the same undoable command as whatever triggered it.
 */
object MkDocsMaterialConfig {

    /** The key of the palette below `theme`. */
    const val KEY_PALETTE: String = "${MkDocsConfig.KEY_THEME}.palette"

    /** The key of the feature flags below `theme`. */
    const val KEY_FEATURES: String = "${MkDocsConfig.KEY_THEME}.features"

    /** The key of the fonts below `theme`. */
    const val KEY_FONT: String = "${MkDocsConfig.KEY_THEME}.font"

    /** The key of the body font below `theme.font`. */
    const val KEY_FONT_TEXT: String = "$KEY_FONT.text"

    /** The key of the code font below `theme.font`. */
    const val KEY_FONT_CODE: String = "$KEY_FONT.code"

    /** The key of the language below `theme`. */
    const val KEY_LANGUAGE: String = "${MkDocsConfig.KEY_THEME}.language"

    /** The key of the writing direction below `theme`. */
    const val KEY_DIRECTION: String = "${MkDocsConfig.KEY_THEME}.direction"

    /** The key of the logo below `theme`. */
    const val KEY_LOGO: String = "${MkDocsConfig.KEY_THEME}.logo"

    /** The key of the favicon below `theme`. */
    const val KEY_FAVICON: String = "${MkDocsConfig.KEY_THEME}.favicon"

    /** The key of the override directory below `theme`. */
    const val KEY_CUSTOM_DIR: String = "${MkDocsConfig.KEY_THEME}.custom_dir"

    /** The top level key listing the Markdown extensions of the site. */
    const val KEY_MARKDOWN_EXTENSIONS: String = "markdown_extensions"

    /** The media query marking the light entry of a two entry palette. */
    const val MEDIA_LIGHT: String = "(prefers-color-scheme: light)"

    /** The media query marking the dark entry of a two entry palette. */
    const val MEDIA_DARK: String = "(prefers-color-scheme: dark)"

    /** The icon the theme's own documentation puts on the toggle of the light palette. */
    const val TOGGLE_ICON_LIGHT: String = "material/brightness-7"

    /** The icon the theme's own documentation puts on the toggle of the dark palette. */
    const val TOGGLE_ICON_DARK: String = "material/brightness-4"

    /** The label the theme's own documentation puts on the toggle of the light palette. */
    const val TOGGLE_NAME_LIGHT: String = "Switch to dark mode"

    /** The label the theme's own documentation puts on the toggle of the dark palette. */
    const val TOGGLE_NAME_DARK: String = "Switch to light mode"

    /** The key naming the media query of a palette entry. */
    private const val KEY_MEDIA: String = "media"

    /** The key naming the scheme of a palette. */
    private const val KEY_SCHEME: String = "scheme"

    /** The key naming the primary colour of a palette. */
    private const val KEY_PRIMARY: String = "primary"

    /** The key naming the accent colour of a palette. */
    private const val KEY_ACCENT: String = "accent"

    /** The key holding the toggle of a palette entry. */
    private const val KEY_TOGGLE: String = "toggle"

    /** The key naming the icon of a toggle. */
    private const val KEY_ICON: String = "icon"

    /** The key naming the label of a toggle. */
    private const val KEY_NAME: String = "name"

    /** The value of `theme.font` telling the theme to load nothing from Google Fonts. */
    private const val FONT_DISABLED: String = "false"

    // -------------------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------------------

    /**
     * Reads the Material settings of [configFile].
     *
     * A file without a `theme` block, and a file whose theme is written as the scalar shorthand
     * `theme: material`, both read as [MkDocsMaterialSettings.EMPTY] — neither carries any of these settings,
     * and the shorthand is turned into a mapping by the writer as soon as one of them is set.
     *
     * The caller must hold a read action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @return the snapshot, never `null`
     */
    fun read(project: Project, configFile: VirtualFile): MkDocsMaterialSettings {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return MkDocsMaterialSettings.EMPTY
        val palette = readPalette(yamlFile)
        val fontValue = keyValueAt(yamlFile, KEY_FONT)?.value
        val fontEnabled = !(fontValue is YAMLScalar && fontValue.textValue.trim().equals(FONT_DISABLED, true))

        return MkDocsMaterialSettings(
            paletteMode = palette.mode,
            light = palette.light,
            dark = palette.dark,
            features = MkDocsConfig.readScalarSequence(project, configFile, KEY_FEATURES).toSet(),
            fontText = if (fontEnabled) MkDocsConfig.readNestedScalar(project, configFile, KEY_FONT_TEXT) else null,
            fontCode = if (fontEnabled) MkDocsConfig.readNestedScalar(project, configFile, KEY_FONT_CODE) else null,
            fontEnabled = fontEnabled,
            language = MkDocsConfig.readNestedScalar(project, configFile, KEY_LANGUAGE),
            direction = MkDocsConfig.readNestedScalar(project, configFile, KEY_DIRECTION),
            logo = MkDocsConfig.readNestedScalar(project, configFile, KEY_LOGO),
            favicon = MkDocsConfig.readNestedScalar(project, configFile, KEY_FAVICON),
            customDir = MkDocsConfig.readNestedScalar(project, configFile, KEY_CUSTOM_DIR),
            extensions = readExtensions(yamlFile),
            editable = palette.editable,
        )
    }

    /**
     * Reads the identifiers listed under the top level `markdown_extensions` of [yamlFile].
     *
     * Both shapes an entry can have are covered: the scalar `- admonition`, and the mapping
     * `- pymdownx.highlight:` carrying the options of the extension below it. Of the latter only the
     * identifier is taken — the options are the author's, and this model does not represent them.
     *
     * @param yamlFile the configuration file to read
     * @return the identifiers, in the order the file lists them
     */
    private fun readExtensions(yamlFile: YAMLFile): Set<String> {
        val sequence = keyValueAt(yamlFile, KEY_MARKDOWN_EXTENSIONS)?.value as? YAMLSequence ?: return emptySet()
        return sequence.items.mapNotNull { item ->
            when (val value = item.value) {
                is YAMLScalar -> value.textValue.trim()
                is YAMLMapping -> value.keyValues.firstOrNull()?.keyText?.trim()
                else -> null
            }?.takeIf { it.isNotEmpty() }
        }.toSet()
    }

    /**
     * Reads `theme.palette` of [yamlFile].
     *
     * Three shapes are represented, everything else is refused:
     * * the key is absent — [MkDocsMaterialSettings.PaletteMode.NONE];
     * * the key holds a mapping — [MkDocsMaterialSettings.PaletteMode.SINGLE];
     * * the key holds a sequence of exactly two mappings whose `media` is the standard
     *   `prefers-color-scheme` pair, light first — [MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE].
     *
     * A sequence of one, of three or more, one whose `media` names something else — the system preference
     * query `(prefers-color-scheme)` of a three palette setup, for instance — or one listing the dark entry
     * first, is not represented. Approximating such a palette and writing the approximation back would take a
     * setup apart that works; the page is switched read-only instead.
     *
     * @param yamlFile the configuration file to read
     * @return what the palette says, and whether it could be represented at all
     */
    private fun readPalette(yamlFile: YAMLFile): Palette {
        return when (val value = keyValueAt(yamlFile, KEY_PALETTE)?.value) {
            null -> Palette(MkDocsMaterialSettings.PaletteMode.NONE)

            is YAMLMapping -> Palette(MkDocsMaterialSettings.PaletteMode.SINGLE, light = entryOf(value))

            is YAMLSequence -> {
                val mappings = value.items.map { it.value as? YAMLMapping }
                if (mappings.size != 2 || mappings.any { it == null }) return unrepresentable()
                val first = mappings[0]!!
                val second = mappings[1]!!
                if (!isMedia(first, MEDIA_LIGHT) || !isMedia(second, MEDIA_DARK)) return unrepresentable()
                Palette(
                    MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE,
                    light = entryOf(first),
                    dark = entryOf(second),
                )
            }

            else -> unrepresentable()
        }
    }

    /**
     * Returns the palette of a file this model cannot represent.
     */
    private fun unrepresentable(): Palette = Palette(MkDocsMaterialSettings.PaletteMode.NONE, editable = false)

    /**
     * Tells whether the `media` of [mapping] is [expected], ignoring the white space inside the query.
     *
     * @param mapping one entry of the palette sequence
     * @param expected the query the entry has to carry
     */
    private fun isMedia(mapping: YAMLMapping, expected: String): Boolean =
        scalarOf(mapping, KEY_MEDIA)?.filterNot { it.isWhitespace() }?.lowercase() ==
            expected.filterNot { it.isWhitespace() }.lowercase()

    /**
     * Builds a palette entry out of [mapping].
     *
     * A scheme the theme does not know, and a colour that is not part of the Material palette, are dropped
     * rather than guessed at: the settings page offers a closed set for both, and there is nothing sensible to
     * show for a value outside it. The scheme falls back to [MkDocsMaterialScheme.DEFAULT], which is what the
     * theme assumes when the key is absent.
     *
     * @param mapping the mapping of one palette
     */
    private fun entryOf(mapping: YAMLMapping): MkDocsMaterialSettings.PaletteEntry {
        val toggle = mapping.keyValues.firstOrNull { it.keyText.trim() == KEY_TOGGLE }?.value as? YAMLMapping
        return MkDocsMaterialSettings.PaletteEntry(
            scheme = scalarOf(mapping, KEY_SCHEME)?.let { MkDocsMaterialScheme.byId(it) }
                ?: MkDocsMaterialScheme.DEFAULT,
            primary = scalarOf(mapping, KEY_PRIMARY)?.let { MkDocsMaterialColor.byId(it) },
            accent = scalarOf(mapping, KEY_ACCENT)?.let { MkDocsMaterialColor.byId(it) },
            toggleIcon = toggle?.let { scalarOf(it, KEY_ICON) },
            toggleName = toggle?.let { scalarOf(it, KEY_NAME) },
        )
    }

    /**
     * Returns the trimmed scalar value of [key] inside [mapping], or `null` if there is none.
     *
     * @param mapping the mapping to look in
     * @param key the key to read
     */
    private fun scalarOf(mapping: YAMLMapping, key: String): String? =
        (mapping.keyValues.firstOrNull { it.keyText.trim() == key }?.value as? YAMLScalar)
            ?.textValue?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Returns the key-value addressed by the dotted [path] of [yamlFile], or `null` if there is none.
     *
     * @param yamlFile the configuration file to look in
     * @param path the dotted path of the key
     */
    private fun keyValueAt(yamlFile: YAMLFile, path: String) =
        YAMLUtil.getQualifiedKeyInFile(yamlFile, *path.split('.').toTypedArray())

    // -------------------------------------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------------------------------------

    /**
     * Writes the difference between [from] and [to] into the file [scope] was opened on.
     *
     * Only the keys that actually differ are touched. A value that became empty removes its key instead of
     * writing an empty string: a configuration file stating `logo: ''` says something different from a file
     * not stating a logo at all.
     *
     * The caller must hold a write action, and the [scope] commits the whole batch once when it returns.
     *
     * @param scope the file being changed
     * @param from the snapshot the settings page was opened with
     * @param to the snapshot the settings page is applied with
     */
    fun write(scope: MkDocsConfigEditScope, from: MkDocsMaterialSettings, to: MkDocsMaterialSettings) {
        writeOptional(scope, KEY_LANGUAGE, from.language, to.language)
        writeOptional(scope, KEY_DIRECTION, from.direction, to.direction)
        writeOptional(scope, KEY_LOGO, from.logo, to.logo)
        writeOptional(scope, KEY_FAVICON, from.favicon, to.favicon)
        writeOptional(scope, KEY_CUSTOM_DIR, from.customDir, to.customDir)
        writeFont(scope, from, to)
        writeSequence(scope, KEY_FEATURES, from.features, to.features)
        writeSequence(scope, KEY_MARKDOWN_EXTENSIONS, from.extensions, to.extensions)
        writePalette(scope, from, to)
    }

    /**
     * Writes the difference between [from] and [to] into [configFile] as a single commit.
     *
     * The convenience the settings page and the wizard use: one *Apply* has to reach the file as one change,
     * not as one change per key.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param from the snapshot the settings page was opened with
     * @param to the snapshot the settings page is applied with
     */
    fun write(
        project: Project,
        configFile: VirtualFile,
        from: MkDocsMaterialSettings,
        to: MkDocsMaterialSettings,
    ) {
        if (from == to) return
        MkDocsConfigWriter.edit(project, configFile) { write(this, from, to) }
    }

    /**
     * Writes [to] into [path], or removes the key if [to] is gone.
     *
     * @param scope the file being changed
     * @param path the dotted path of the key
     * @param from the previous value
     * @param to the new value
     */
    private fun writeOptional(scope: MkDocsConfigEditScope, path: String, from: String?, to: String?) {
        val previous = from?.takeIf { it.isNotBlank() }
        val current = to?.takeIf { it.isNotBlank() }
        if (previous == current) return
        if (current == null) scope.removeNestedKey(path) else scope.setNestedScalar(path, current)
    }

    /**
     * Writes the entries [to] adds to and removes from the scalar sequence at [path].
     *
     * An entry the file writes as a mapping — a Markdown extension carrying its options — is not removed here:
     * [MkDocsConfigEditScope.removeScalarItem] only matches a scalar entry, and taking a configured extension
     * out would throw away options this model never saw. Such an entry stays, which is the safe outcome.
     *
     * @param scope the file being changed
     * @param path the dotted path of the sequence
     * @param from the previous entries
     * @param to the new entries
     */
    private fun writeSequence(
        scope: MkDocsConfigEditScope,
        path: String,
        from: Set<String>,
        to: Set<String>,
    ) {
        (from - to).forEach { scope.removeScalarItem(path, it) }
        (to - from).forEach { scope.addScalarItem(path, it) }
    }

    /**
     * Writes `theme.font` when the fonts differ between [from] and [to].
     *
     * The key carries two shapes: the mapping of `text` and `code`, and the scalar `false`, which tells the
     * theme to load nothing from Google Fonts. Switching between them replaces the whole key, because neither
     * shape can hold the other.
     *
     * @param scope the file being changed
     * @param from the snapshot the settings page was opened with
     * @param to the snapshot the settings page is applied with
     */
    private fun writeFont(
        scope: MkDocsConfigEditScope,
        from: MkDocsMaterialSettings,
        to: MkDocsMaterialSettings,
    ) {
        if (from.fontEnabled == to.fontEnabled && from.fontText == to.fontText && from.fontCode == to.fontCode) {
            return
        }
        if (!to.fontEnabled) {
            scope.setNestedBoolean(KEY_FONT, false)
            return
        }
        if (!from.fontEnabled) {
            // The key holds the scalar `false`, which has nowhere to keep a family: it has to go first.
            scope.removeNestedKey(KEY_FONT)
            writeOptional(scope, KEY_FONT_TEXT, null, to.fontText)
            writeOptional(scope, KEY_FONT_CODE, null, to.fontCode)
            return
        }
        writeOptional(scope, KEY_FONT_TEXT, from.fontText, to.fontText)
        writeOptional(scope, KEY_FONT_CODE, from.fontCode, to.fontCode)
    }

    /**
     * Writes `theme.palette` when it differs between [from] and [to].
     *
     * A palette this model could not represent is never written: neither snapshot describes what the file
     * actually says, so any write would be a guess. Everything else is written by removing the key and
     * building it again — a palette changes shape between a mapping and a sequence, and the entries of a
     * sequence carry a nested toggle, so patching the existing key in place would leave remains of the
     * previous shape behind.
     *
     * @param scope the file being changed
     * @param from the snapshot the settings page was opened with
     * @param to the snapshot the settings page is applied with
     */
    private fun writePalette(
        scope: MkDocsConfigEditScope,
        from: MkDocsMaterialSettings,
        to: MkDocsMaterialSettings,
    ) {
        if (!from.editable || !to.editable) return
        if (from.paletteMode == to.paletteMode && from.light == to.light && from.dark == to.dark) return

        if (from.paletteMode != MkDocsMaterialSettings.PaletteMode.NONE) {
            scope.removeNestedKey(KEY_PALETTE)
        }
        when (to.paletteMode) {
            MkDocsMaterialSettings.PaletteMode.NONE -> Unit

            MkDocsMaterialSettings.PaletteMode.SINGLE -> {
                scope.setNestedScalar("$KEY_PALETTE.$KEY_SCHEME", to.light.scheme.id)
                to.light.primary?.let { scope.setNestedScalar("$KEY_PALETTE.$KEY_PRIMARY", it.id) }
                to.light.accent?.let { scope.setNestedScalar("$KEY_PALETTE.$KEY_ACCENT", it.id) }
            }

            MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE -> {
                scope.addMappingTreeItem(
                    KEY_PALETTE,
                    entriesOf(to.light, MEDIA_LIGHT, TOGGLE_ICON_LIGHT, TOGGLE_NAME_LIGHT),
                )
                scope.addMappingTreeItem(
                    KEY_PALETTE,
                    entriesOf(to.dark, MEDIA_DARK, TOGGLE_ICON_DARK, TOGGLE_NAME_DARK),
                )
            }
        }
    }

    /**
     * Renders [entry] as the keys of one entry of a two entry palette.
     *
     * The toggle is filled in from the theme's own documentation whenever the entry does not carry one of its
     * own: a palette written without a toggle cannot be switched, and a user asking for a light and a dark
     * appearance is asking for exactly that switch.
     *
     * @param entry the palette to render
     * @param media the media query marking the entry
     * @param icon the icon of the toggle if [entry] has none
     * @param name the label of the toggle if [entry] has none
     * @return the keys of the entry, in the order they are written
     */
    private fun entriesOf(
        entry: MkDocsMaterialSettings.PaletteEntry,
        media: String,
        icon: String,
        name: String,
    ): List<Pair<String, Any>> {
        val entries = mutableListOf<Pair<String, Any>>(KEY_MEDIA to media, KEY_SCHEME to entry.scheme.id)
        entry.primary?.let { entries += KEY_PRIMARY to it.id }
        entry.accent?.let { entries += KEY_ACCENT to it.id }
        entries += KEY_TOGGLE to listOf(
            KEY_ICON to (entry.toggleIcon?.takeIf { it.isNotBlank() } ?: icon),
            KEY_NAME to (entry.toggleName?.takeIf { it.isNotBlank() } ?: name),
        )
        return entries
    }

    /**
     * What [readPalette] found.
     *
     * @property mode the shape the palette was written in
     * @property light the single or the light palette
     * @property dark the dark palette
     * @property editable `false` if the palette could not be represented
     */
    private data class Palette(
        val mode: MkDocsMaterialSettings.PaletteMode,
        val light: MkDocsMaterialSettings.PaletteEntry = MkDocsMaterialSettings.PaletteEntry(),
        val dark: MkDocsMaterialSettings.PaletteEntry =
            MkDocsMaterialSettings.PaletteEntry(scheme = MkDocsMaterialScheme.SLATE),
        val editable: Boolean = true,
    )
}
