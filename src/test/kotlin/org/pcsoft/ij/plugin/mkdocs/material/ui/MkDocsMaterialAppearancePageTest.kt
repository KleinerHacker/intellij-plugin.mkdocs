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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the appearance page as a model: what it is filled with, what it gives back, and what it refuses to
 * touch when the palette of the site is one it cannot represent.
 */
class MkDocsMaterialAppearancePageTest : BasePlatformTestCase() {

    /**
     * Use case: a site with a two entry palette and its own fonts is opened and applied without anything
     * being changed. Nothing may move — a page that alters a snapshot by being looked at would rewrite
     * `mkdocs.yml` on every *Apply*.
     */
    fun `test gives back unchanged what it was filled with`() {
        val page = MkDocsMaterialAppearancePage()
        val settings = MkDocsMaterialSettings(
            paletteMode = MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE,
            light = MkDocsMaterialSettings.PaletteEntry(
                scheme = MkDocsMaterialScheme.DEFAULT,
                primary = MkDocsMaterialColor.INDIGO,
                accent = MkDocsMaterialColor.PINK,
                toggleIcon = "material/brightness-7",
                toggleName = "Switch to dark mode",
            ),
            dark = MkDocsMaterialSettings.PaletteEntry(
                scheme = MkDocsMaterialScheme.SLATE,
                primary = MkDocsMaterialColor.TEAL,
            ),
            fontText = "Inter",
            fontCode = "Fira Code",
        )

        page.reset(settings)

        assertEquals(settings, page.applyTo(settings))
        assertFalse(page.isModified(settings))
    }

    /**
     * Use case: the user picks another primary colour and another palette shape. Both have to reach the
     * snapshot, and the toggle the file carries must survive — the page never shows it, and a page must not
     * drop what it does not show.
     */
    fun `test carries a changed palette into the snapshot`() {
        val page = MkDocsMaterialAppearancePage()
        val settings = MkDocsMaterialSettings(
            paletteMode = MkDocsMaterialSettings.PaletteMode.SINGLE,
            light = MkDocsMaterialSettings.PaletteEntry(
                primary = MkDocsMaterialColor.INDIGO,
                toggleName = "Switch to dark mode",
            ),
        )
        page.reset(settings)

        page.setPrimaryForTest(MkDocsMaterialColor.TEAL)
        page.setPaletteModeForTest(MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE)

        val applied = page.applyTo(settings)
        assertTrue(page.isModified(settings))
        assertEquals(MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE, applied.paletteMode)
        assertEquals(MkDocsMaterialColor.TEAL, applied.light.primary)
        assertEquals("Switch to dark mode", applied.light.toggleName)
    }

    /**
     * Use case: a family outside the curated list is typed. The drop down is editable exactly for that, and
     * the family has to end up in the snapshot as it was typed.
     */
    fun `test keeps a font family outside the curated list`() {
        val page = MkDocsMaterialAppearancePage()
        page.reset(MkDocsMaterialSettings.EMPTY)

        page.setFontTextForTest("Comic Neue")

        assertEquals("Comic Neue", page.applyTo(MkDocsMaterialSettings.EMPTY).fontText)
    }

    /**
     * Use case: the site carries a palette this plugin cannot represent. The controls are switched off, and
     * applying the page leaves every palette field exactly as it was — writing an approximation back would
     * take the author's setup apart.
     */
    fun `test leaves an unrepresentable palette alone`() {
        val page = MkDocsMaterialAppearancePage()
        val settings = MkDocsMaterialSettings(
            paletteMode = MkDocsMaterialSettings.PaletteMode.NONE,
            light = MkDocsMaterialSettings.PaletteEntry(primary = MkDocsMaterialColor.RED),
            editable = false,
        )
        page.reset(settings)

        assertFalse("the palette must not accept input", page.isPaletteEditableForTest())

        page.setPrimaryForTest(MkDocsMaterialColor.TEAL)
        page.setPaletteModeForTest(MkDocsMaterialSettings.PaletteMode.SINGLE)

        val applied = page.applyTo(settings)
        assertEquals(MkDocsMaterialSettings.PaletteMode.NONE, applied.paletteMode)
        assertEquals(MkDocsMaterialColor.RED, applied.light.primary)
        assertFalse(page.isModified(settings))
    }

    /**
     * Use case: the user switches font loading off entirely, which is `font: false`. The families have to go
     * with it — the key cannot hold both.
     */
    fun `test drops the families when no font is loaded at all`() {
        val page = MkDocsMaterialAppearancePage()
        val settings = MkDocsMaterialSettings(fontText = "Inter", fontCode = "Fira Code")
        page.reset(settings)

        page.component()
        val disabled = settings.copy(fontEnabled = false)
        page.reset(disabled)

        val applied = page.applyTo(disabled)
        assertFalse(applied.fontEnabled)
        assertNull(applied.fontText)
        assertNull(applied.fontCode)
    }
}
