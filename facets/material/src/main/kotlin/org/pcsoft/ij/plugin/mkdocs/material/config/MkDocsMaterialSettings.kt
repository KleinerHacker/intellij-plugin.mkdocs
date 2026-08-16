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

import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * An immutable snapshot of everything the Material settings pages can change in an MkDocs configuration file.
 *
 * The snapshot is deliberately **not** a model of `mkdocs.yml`: it covers the keys the four settings pages
 * offer and nothing else. Anything the file carries next to them — a key of another plugin, an option below a
 * Markdown extension, a comment — is invisible here, which is exactly why [MkDocsMaterialConfig.write] only
 * writes the keys that differ between two snapshots instead of rendering a snapshot back into the file. A
 * lossy model that rewrote whole blocks would silently destroy what it does not represent.
 *
 * Reading and writing live in [MkDocsMaterialConfig]; [Mutable] is the working copy a settings page binds to.
 *
 * @property paletteMode how many palettes the theme is configured with
 * @property light the palette used on its own in [PaletteMode.SINGLE], and the light one in
 *           [PaletteMode.LIGHT_DARK_TOGGLE]
 * @property dark the dark palette, used in [PaletteMode.LIGHT_DARK_TOGGLE] only
 * @property features the identifiers of `theme.features`, including the ones this plugin does not know — a
 *           newer version of the theme brings flags an older plugin must not drop
 * @property fontText the family of `theme.font.text`, or `null` if the theme decides
 * @property fontCode the family of `theme.font.code`, or `null` if the theme decides
 * @property fontEnabled `false` for `font: false`, which tells the theme to load no font from Google Fonts
 * @property language the value of `theme.language`
 * @property direction the value of `theme.direction`, `ltr` or `rtl`
 * @property logo the value of `theme.logo`
 * @property favicon the value of `theme.favicon`
 * @property customDir the value of `theme.custom_dir`
 * @property extensions the identifiers listed under the top level `markdown_extensions`, taken from both the
 *           scalar and the mapping shape of an entry
 * @property editable `false` if the palette of the file has a shape this model cannot represent; the palette
 *           fields then carry their defaults and must never be written back
 */
data class MkDocsMaterialSettings(
    val paletteMode: PaletteMode = PaletteMode.NONE,
    val light: PaletteEntry = PaletteEntry(),
    val dark: PaletteEntry = PaletteEntry(scheme = MkDocsMaterialScheme.SLATE),
    val features: Set<String> = emptySet(),
    val fontText: String? = null,
    val fontCode: String? = null,
    val fontEnabled: Boolean = true,
    val language: String? = null,
    val direction: String? = null,
    val logo: String? = null,
    val favicon: String? = null,
    val customDir: String? = null,
    val extensions: Set<String> = emptySet(),
    val editable: Boolean = true,
) {

    /**
     * How the theme is told about colours.
     */
    enum class PaletteMode {

        /** No `theme.palette` at all — the theme paints the site in its own default colours. */
        NONE,

        /** One palette, written as a mapping below `theme.palette`. */
        SINGLE,

        /**
         * Two palettes, written as a sequence of two entries carrying `media` and `toggle`, so the reader can
         * switch between a light and a dark appearance.
         */
        LIGHT_DARK_TOGGLE,
    }

    /**
     * One palette of the theme.
     *
     * @property scheme the ground the palette is painted on
     * @property primary the primary colour, or `null` if the theme decides
     * @property accent the accent colour, or `null` if the theme decides
     * @property toggleIcon the icon of the toggle switching to the other palette, used in
     *           [PaletteMode.LIGHT_DARK_TOGGLE] only
     * @property toggleName the label of that toggle
     */
    data class PaletteEntry(
        val scheme: MkDocsMaterialScheme = MkDocsMaterialScheme.DEFAULT,
        val primary: MkDocsMaterialColor? = null,
        val accent: MkDocsMaterialColor? = null,
        val toggleIcon: String? = null,
        val toggleName: String? = null,
    ) {

        /**
         * Returns a working copy of this entry.
         */
        fun toMutable(): Mutable = Mutable(scheme, primary, accent, toggleIcon, toggleName)

        /**
         * The working copy of a [PaletteEntry], writable field by field so a settings page can bind to it.
         *
         * @property scheme the ground the palette is painted on
         * @property primary the primary colour, or `null` if the theme decides
         * @property accent the accent colour, or `null` if the theme decides
         * @property toggleIcon the icon of the toggle switching to the other palette
         * @property toggleName the label of that toggle
         */
        class Mutable(
            var scheme: MkDocsMaterialScheme = MkDocsMaterialScheme.DEFAULT,
            var primary: MkDocsMaterialColor? = null,
            var accent: MkDocsMaterialColor? = null,
            var toggleIcon: String? = null,
            var toggleName: String? = null,
        ) {

            /**
             * Returns the immutable entry the fields currently describe.
             */
            fun toImmutable(): PaletteEntry = PaletteEntry(scheme, primary, accent, toggleIcon, toggleName)
        }
    }

    /**
     * Returns a working copy of this snapshot.
     *
     * A settings page edits the copy and hands both the original and the copy to
     * [MkDocsMaterialConfig.write], which is what makes writing only the changed keys possible.
     */
    fun toMutable(): Mutable = Mutable(
        paletteMode = paletteMode,
        light = light.toMutable(),
        dark = dark.toMutable(),
        features = features.toMutableSet(),
        fontText = fontText,
        fontCode = fontCode,
        fontEnabled = fontEnabled,
        language = language,
        direction = direction,
        logo = logo,
        favicon = favicon,
        customDir = customDir,
        extensions = extensions.toMutableSet(),
        editable = editable,
    )

    /**
     * The working copy of a [MkDocsMaterialSettings], writable field by field.
     *
     * Kept as a class of plain fields rather than a data class: the IntelliJ UI DSL binds a control to a
     * property reference, and a property of a copied-on-change value is not something it can write into.
     *
     * @property paletteMode how many palettes the theme is configured with
     * @property light the single or the light palette
     * @property dark the dark palette
     * @property features the identifiers of `theme.features`
     * @property fontText the family of `theme.font.text`
     * @property fontCode the family of `theme.font.code`
     * @property fontEnabled `false` for `font: false`
     * @property language the value of `theme.language`
     * @property direction the value of `theme.direction`
     * @property logo the value of `theme.logo`
     * @property favicon the value of `theme.favicon`
     * @property customDir the value of `theme.custom_dir`
     * @property extensions the identifiers listed under `markdown_extensions`
     * @property editable `false` if the palette of the file cannot be represented
     */
    class Mutable(
        var paletteMode: PaletteMode = PaletteMode.NONE,
        var light: PaletteEntry.Mutable = PaletteEntry().toMutable(),
        var dark: PaletteEntry.Mutable = PaletteEntry(scheme = MkDocsMaterialScheme.SLATE).toMutable(),
        var features: MutableSet<String> = mutableSetOf(),
        var fontText: String? = null,
        var fontCode: String? = null,
        var fontEnabled: Boolean = true,
        var language: String? = null,
        var direction: String? = null,
        var logo: String? = null,
        var favicon: String? = null,
        var customDir: String? = null,
        var extensions: MutableSet<String> = mutableSetOf(),
        var editable: Boolean = true,
    ) {

        /**
         * Returns the immutable snapshot the fields currently describe.
         */
        fun toImmutable(): MkDocsMaterialSettings = MkDocsMaterialSettings(
            paletteMode = paletteMode,
            light = light.toImmutable(),
            dark = dark.toImmutable(),
            features = features.toSet(),
            fontText = fontText,
            fontCode = fontCode,
            fontEnabled = fontEnabled,
            language = language,
            direction = direction,
            logo = logo,
            favicon = favicon,
            customDir = customDir,
            extensions = extensions.toSet(),
            editable = editable,
        )
    }

    companion object {

        /**
         * The snapshot of a site carrying none of these settings.
         *
         * What a file without a `theme` block reads as, and the starting point of a wizard page.
         */
        val EMPTY: MkDocsMaterialSettings = MkDocsMaterialSettings()
    }
}
