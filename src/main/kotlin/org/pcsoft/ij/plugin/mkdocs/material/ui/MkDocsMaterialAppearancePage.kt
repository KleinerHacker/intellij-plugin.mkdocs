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

package org.pcsoft.ij.plugin.mkdocs.material.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFont
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import java.awt.Color
import javax.swing.JCheckBox
import javax.swing.JList

/**
 * The page deciding what the site looks like: its palette and its fonts.
 *
 * The colours are offered as the closed set the theme accepts, each with a swatch of the shade it stands for —
 * `indigo` and `deep-purple` say very little on their own. The fonts are not a closed set: the theme loads
 * whatever family is named from Google Fonts, so the drop downs are editable and a family outside the curated
 * list is kept exactly as it was typed.
 *
 * A palette this plugin cannot represent — three entries, a media query of its own — switches every palette
 * control off and states why. The alternative would be to show an approximation and write it back on the next
 * *Apply*, which takes a working setup apart; see [MkDocsMaterialSettings.editable].
 */
class MkDocsMaterialAppearancePage : MkDocsMaterialPageBase(ID, "material.page.appearance.title") {

    companion object {

        /** The identifier of this page. */
        const val ID: String = "material.appearance"
    }

    /** The snapshot the page was last reset with; what everything not shown here is taken from. */
    private var shown: MkDocsMaterialSettings = MkDocsMaterialSettings.EMPTY

    private val paletteModeCombo = ComboBox(MkDocsMaterialSettings.PaletteMode.entries.toTypedArray()).apply {
        renderer = object : SimpleListCellRenderer<MkDocsMaterialSettings.PaletteMode>() {
            override fun customize(
                list: JList<out MkDocsMaterialSettings.PaletteMode>,
                value: MkDocsMaterialSettings.PaletteMode?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.let { MkDocsBundle.message(paletteModeKey(it)) }.orEmpty()
            }
        }
        addActionListener {
            updateEnabled()
            fireChanged()
        }
    }

    /** The colours, fonts and schemes the theme offers, read from the bundled `facets/material` resources. */
    private val data = service<MkDocsMaterialDataService>()

    private val lightScheme = schemeCombo()

    private val lightPrimary = colorCombo(data.colors.primaries())

    private val lightAccent = colorCombo(data.colors.accents())

    private val darkScheme = schemeCombo()

    private val darkPrimary = colorCombo(data.colors.primaries())

    private val darkAccent = colorCombo(data.colors.accents())

    private val fontEnabledBox = JCheckBox(MkDocsBundle.message("material.page.appearance.font.enabled")).apply {
        addActionListener {
            updateEnabled()
            fireChanged()
        }
    }

    private val fontTextCombo = fontCombo(data.fonts.textFonts())

    private val fontCodeCombo = fontCombo(data.fonts.codeFonts())

    /** The rows holding the dark palette, hidden while the site has no second palette. */
    private val darkRows = mutableListOf<Row>()

    /** The row explaining why the palette cannot be edited, shown only when it cannot be. */
    private var readOnlyRow: Row? = null

    override fun createContent(): DialogPanel {
        darkRows.clear()
        return panel {
            row(MkDocsBundle.message("material.page.appearance.paletteMode")) {
                cell(paletteModeCombo)
            }
            readOnlyRow = row {
                comment(MkDocsBundle.message("material.page.appearance.readOnly"))
            }
            group(MkDocsBundle.message("material.page.appearance.light")) {
                row(MkDocsBundle.message("material.page.appearance.scheme")) { cell(lightScheme) }
                row(MkDocsBundle.message("material.page.appearance.primary")) { cell(lightPrimary) }
                row(MkDocsBundle.message("material.page.appearance.accent")) { cell(lightAccent) }
            }
            group(MkDocsBundle.message("material.page.appearance.dark")) {
                darkRows += row(MkDocsBundle.message("material.page.appearance.scheme")) { cell(darkScheme) }
                darkRows += row(MkDocsBundle.message("material.page.appearance.primary")) { cell(darkPrimary) }
                darkRows += row(MkDocsBundle.message("material.page.appearance.accent")) { cell(darkAccent) }
            }
            group(MkDocsBundle.message("material.page.appearance.fonts")) {
                row { cell(fontEnabledBox) }
                row(MkDocsBundle.message("material.page.appearance.font.text")) {
                    cell(fontTextCombo).align(AlignX.FILL)
                }
                row(MkDocsBundle.message("material.page.appearance.font.code")) {
                    cell(fontCodeCombo).align(AlignX.FILL)
                }
                row {
                    comment(MkDocsBundle.message("material.page.appearance.font.hint"))
                }
            }
        }.also { updateEnabled() }
    }

    override fun reset(settings: MkDocsMaterialSettings) {
        shown = settings
        paletteModeCombo.selectedItem = settings.paletteMode
        lightScheme.selectedItem = settings.light.scheme
        lightPrimary.selectedItem = settings.light.primary
        lightAccent.selectedItem = settings.light.accent
        darkScheme.selectedItem = settings.dark.scheme
        darkPrimary.selectedItem = settings.dark.primary
        darkAccent.selectedItem = settings.dark.accent
        fontEnabledBox.isSelected = settings.fontEnabled
        fontTextCombo.selectedItem = settings.fontText.orEmpty()
        fontCodeCombo.selectedItem = settings.fontCode.orEmpty()
        updateEnabled()
    }

    override fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings {
        val enabled = fontEnabledBox.isSelected
        val fonts = settings.copy(
            fontEnabled = enabled,
            fontText = if (enabled) typedFont(fontTextCombo) else null,
            fontCode = if (enabled) typedFont(fontCodeCombo) else null,
        )
        // A palette that could not be read is a palette that must not be written: neither snapshot describes
        // what the file actually says, so anything written here would be a guess at the author's setup.
        if (!settings.editable) return fonts
        return fonts.copy(
            paletteMode = selectedMode(),
            light = settings.light.copy(
                scheme = lightScheme.selectedItem as MkDocsMaterialScheme,
                primary = lightPrimary.selectedItem as MkDocsMaterialColor?,
                accent = lightAccent.selectedItem as MkDocsMaterialColor?,
            ),
            dark = settings.dark.copy(
                scheme = darkScheme.selectedItem as MkDocsMaterialScheme,
                primary = darkPrimary.selectedItem as MkDocsMaterialColor?,
                accent = darkAccent.selectedItem as MkDocsMaterialColor?,
            ),
        )
    }

    /** The palette shape currently selected. */
    private fun selectedMode(): MkDocsMaterialSettings.PaletteMode =
        paletteModeCombo.selectedItem as? MkDocsMaterialSettings.PaletteMode
            ?: MkDocsMaterialSettings.PaletteMode.NONE

    /**
     * Switches the controls in line with what is currently selected.
     *
     * Three rules: an unrepresentable palette disables every palette control and explains itself, the palette
     * controls are pointless while the theme paints in its own colours, and the second palette only exists in
     * the two palette shape. The fonts follow the tick that loads them at all.
     */
    private fun updateEnabled() {
        val editable = shown.editable
        val mode = selectedMode()
        paletteModeCombo.isEnabled = editable
        readOnlyRow?.visible(!editable)

        val single = editable && mode != MkDocsMaterialSettings.PaletteMode.NONE
        listOf(lightScheme, lightPrimary, lightAccent).forEach { it.isEnabled = single }

        val two = editable && mode == MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE
        listOf(darkScheme, darkPrimary, darkAccent).forEach { it.isEnabled = two }
        darkRows.forEach { it.visible(two) }

        val fonts = fontEnabledBox.isSelected
        fontTextCombo.isEnabled = fonts
        fontCodeCombo.isEnabled = fonts
    }

    /** The family currently in [combo], or `null` if it names none. */
    private fun typedFont(combo: ComboBox<String>): String? =
        (combo.editor?.item ?: combo.selectedItem)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    /** The bundle key of the label of [mode]. */
    private fun paletteModeKey(mode: MkDocsMaterialSettings.PaletteMode): String = when (mode) {
        MkDocsMaterialSettings.PaletteMode.NONE -> "material.page.appearance.paletteMode.none"
        MkDocsMaterialSettings.PaletteMode.SINGLE -> "material.page.appearance.paletteMode.single"
        MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE -> "material.page.appearance.paletteMode.toggle"
    }

    /** A drop down over the two schemes of the theme. */
    private fun schemeCombo(): ComboBox<MkDocsMaterialScheme> =
        ComboBox(MkDocsMaterialScheme.entries.toTypedArray()).apply {
            renderer = object : SimpleListCellRenderer<MkDocsMaterialScheme>() {
                override fun customize(
                    list: JList<out MkDocsMaterialScheme>,
                    value: MkDocsMaterialScheme?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    text = value?.let { MkDocsBundle.messageOrDefault(it.titleKey, it.id) ?: it.id }.orEmpty()
                }
            }
            addActionListener { fireChanged() }
        }

    /**
     * A drop down over [colors], each shown with a swatch of the shade it stands for.
     *
     * The first entry is no colour at all, which is what leaves the key out and lets the theme decide.
     *
     * @param colors the colours accepted for the role the drop down fills
     */
    private fun colorCombo(colors: List<MkDocsMaterialColor>): ComboBox<MkDocsMaterialColor?> {
        val items: Array<MkDocsMaterialColor?> = (listOf<MkDocsMaterialColor?>(null) + colors).toTypedArray()
        return ComboBox(items).apply {
            renderer = object : SimpleListCellRenderer<MkDocsMaterialColor?>() {
                override fun customize(
                    list: JList<out MkDocsMaterialColor?>,
                    value: MkDocsMaterialColor?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {
                    if (value == null) {
                        icon = null
                        text = MkDocsBundle.message("material.page.appearance.color.default")
                    } else {
                        icon = ColorIcon(JBUI.scale(12), Color(value.hex))
                        text = value.id
                    }
                }
            }
            addActionListener { fireChanged() }
        }
    }

    /**
     * Selects [mode] as if the user had picked it from the drop down.
     *
     * @param mode the palette shape to select
     */
    @TestOnly
    internal fun setPaletteModeForTest(mode: MkDocsMaterialSettings.PaletteMode) {
        paletteModeCombo.selectedItem = mode
    }

    /**
     * Selects [color] as the primary colour of the single or the light palette.
     *
     * @param color the colour to select, or `null` to leave the choice to the theme
     */
    @TestOnly
    internal fun setPrimaryForTest(color: MkDocsMaterialColor?) {
        lightPrimary.selectedItem = color
    }

    /**
     * Types [family] into the drop down of the body font.
     *
     * @param family the family to type
     */
    @TestOnly
    internal fun setFontTextForTest(family: String) {
        fontTextCombo.selectedItem = family
    }

    /** Tells whether the palette controls accept input at all. */
    @TestOnly
    internal fun isPaletteEditableForTest(): Boolean = paletteModeCombo.isEnabled

    /**
     * An editable drop down over [fonts].
     *
     * Editable because the list is a selection rather than a rule — every family hosted by Google Fonts works,
     * and a site naming one outside the list must not lose it by being opened here.
     *
     * @param fonts the curated families offered for the role the drop down fills
     */
    private fun fontCombo(fonts: List<MkDocsMaterialFont>): ComboBox<String> {
        val items = (listOf("") + fonts.map { it.id }.filter { it.isNotEmpty() }).toTypedArray()
        return ComboBox(items).apply {
            isEditable = true
            addActionListener { fireChanged() }
        }
    }
}
