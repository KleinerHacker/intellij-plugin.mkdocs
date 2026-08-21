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

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the one place that decides what a value below `theme.palette` stands for. The completion, the quick
 * documentation and the inspection all ask here, so an answer given wrongly is given wrongly three times.
 */
class MkDocsMaterialPaletteKeysTest : BasePlatformTestCase() {

    /** The theme description the colours are read from. */
    private val colors get() = service<MkDocsMaterialDataService>().colors

    /**
     * Use case: the three queries the theme is built around are the ones offered, in the order the
     * documentation of the theme writes a palette in — light, dark, system preference.
     */
    fun `test offers the three queries of the theme in the documented order`() {
        assertEquals(
            listOf(
                MkDocsMaterialConfig.MEDIA_LIGHT,
                MkDocsMaterialConfig.MEDIA_DARK,
                MkDocsMaterialConfig.MEDIA_SYSTEM,
            ),
            MkDocsMaterialPaletteKeys.MEDIA_QUERIES.map { it.query },
        )
    }

    /**
     * Use case: every offered query carries a text describing when its palette applies. An entry without one
     * would reach the popup as a bare string, and the parentheses of a query say nothing on their own.
     */
    fun `test every query carries a description`() {
        assertTrue(MkDocsMaterialPaletteKeys.MEDIA_QUERIES.all { it.descriptionKey.isNotBlank() })
    }

    /**
     * Use case: the queries of the theme are known, whatever shape they were written in — YAML keeps the white
     * space of a scalar, a browser does not, and `(prefers-color-scheme:dark)` is the same query as the spaced
     * one. Case is no part of a media feature either.
     */
    fun `test knows the queries of the theme regardless of white space and case`() {
        assertTrue(MkDocsMaterialPaletteKeys.isKnownMedia(MkDocsMaterialConfig.MEDIA_LIGHT))
        assertTrue(MkDocsMaterialPaletteKeys.isKnownMedia(MkDocsMaterialConfig.MEDIA_DARK))
        assertTrue(MkDocsMaterialPaletteKeys.isKnownMedia(MkDocsMaterialConfig.MEDIA_SYSTEM))
        assertTrue(MkDocsMaterialPaletteKeys.isKnownMedia("(prefers-color-scheme:dark)"))
        assertTrue(MkDocsMaterialPaletteKeys.isKnownMedia("(PREFERS-COLOR-SCHEME: DARK)"))
    }

    /**
     * Use case: a query outside the three. Legal CSS, but nothing the toggle of the theme acts on — and the
     * near miss is the interesting case, because `prefers-contrast` reads like one of them.
     */
    fun `test does not know a query outside the three`() {
        assertFalse(MkDocsMaterialPaletteKeys.isKnownMedia("(prefers-contrast: more)"))
        assertFalse(MkDocsMaterialPaletteKeys.isKnownMedia("screen and (min-width: 60em)"))
        assertFalse(MkDocsMaterialPaletteKeys.isKnownMedia("light"))
        assertFalse(MkDocsMaterialPaletteKeys.isKnownMedia(""))
    }

    /**
     * Use case: the path the media query stands at. `theme.palette.media` is the query of a palette; the same
     * name below anything else belongs to whoever put it there.
     */
    fun `test accepts only the media path of a palette`() {
        assertTrue(MkDocsMaterialPaletteKeys.isMediaPath("theme.palette.media"))
        assertFalse(MkDocsMaterialPaletteKeys.isMediaPath("media"))
        assertFalse(MkDocsMaterialPaletteKeys.isMediaPath("extra.media"))
        assertFalse(MkDocsMaterialPaletteKeys.isMediaPath("theme.palette.toggle.media"))
    }

    /**
     * Use case: the palette written as a single mapping, which is the shape a site without a toggle uses.
     * Every key of it has to be recognised for what its value stands for.
     */
    fun `test reads the roles of a palette written as a mapping`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-color-scheme: dark)"
                scheme: slate
                primary: indigo
                accent: pink
            """

        assertEquals(MkDocsMaterialPaletteKeys.Role.MEDIA, roleAt(text, "prefers-color-scheme: dark"))
        assertEquals(MkDocsMaterialPaletteKeys.Role.SCHEME, roleAt(text, "slate"))
        assertEquals(MkDocsMaterialPaletteKeys.Role.PRIMARY, roleAt(text, "indigo"))
        assertEquals(MkDocsMaterialPaletteKeys.Role.ACCENT, roleAt(text, "pink"))
    }

    /**
     * Use case: the palette written as a sequence, which is the shape of a colour scheme toggle. The sequence
     * contributes nothing to the path, so both shapes have to reach the same answer.
     */
    fun `test reads the roles of a palette written as a sequence`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "(prefers-color-scheme: light)"
                  scheme: default
                  primary: teal
            """

        assertEquals(MkDocsMaterialPaletteKeys.Role.MEDIA, roleAt(text, "prefers-color-scheme: light"))
        assertEquals(MkDocsMaterialPaletteKeys.Role.SCHEME, roleAt(text, "default"))
        assertEquals(MkDocsMaterialPaletteKeys.Role.PRIMARY, roleAt(text, "teal"))
    }

    /**
     * Use case: keys of the same names somewhere else in the file. `primary` below a plugin is that plugin's,
     * and judging it by its name alone would put the theme's rules on a key it never sees.
     */
    fun `test stays away from keys of the same name elsewhere`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
            extra:
              media: "(prefers-color-scheme: dark)"
              primary: indigo
            """

        assertNull(roleAt(text, "prefers-color-scheme: dark"))
        assertNull(roleAt(text, "indigo"))
    }

    /**
     * Use case: the key of a palette rather than its value. A key being typed is completed by the schema, and
     * answering for it here would put the rules of a value on the name above it.
     */
    fun `test stays away from the key itself`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """

        assertNull(roleAt(text, "primary"))
    }

    /**
     * Use case: an identifier below `primary` and below `accent`. Both roles resolve a colour of the theme,
     * and the identifier of a colour is what the file writes.
     */
    fun `test resolves a colour for both colour roles`() {
        val primary = MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.PRIMARY, "indigo")
        assertNotNull(primary)
        assertEquals("indigo", primary!!.id)

        val accent = MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.ACCENT, "pink")
        assertNotNull(accent)
        assertEquals("pink", accent!!.id)
    }

    /**
     * Use case: a colour the theme accepts for one role only. `white` sits behind white text and exists for
     * the primary colour alone, so asking for it as an accent must find nothing — otherwise the popup and the
     * documentation would name a value the theme refuses.
     */
    fun `test resolves a colour only for the role that accepts it`() {
        val primaryOnly = colors.primaries().map { it.id }.toSet() - colors.accents().map { it.id }.toSet()
        assertFalse("the theme must accept a colour for the primary role only", primaryOnly.isEmpty())

        val id = primaryOnly.first()
        assertNotNull(MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.PRIMARY, id))
        assertNull(MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.ACCENT, id))
    }

    /**
     * Use case: a role that carries no colour, and an identifier that names none. Both have to answer nothing
     * rather than the first colour of the list.
     */
    fun `test resolves no colour for another role or an unknown name`() {
        assertNull(MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.SCHEME, "indigo"))
        assertNull(MkDocsMaterialPaletteKeys.colorOf(null, "indigo"))
        assertNull(MkDocsMaterialPaletteKeys.colorOf(MkDocsMaterialPaletteKeys.Role.PRIMARY, "chartreuse"))
    }

    /**
     * Use case: the value of `scheme`. Two schemes exist, and anything else is not one of them.
     */
    fun `test resolves the schemes of the theme`() {
        assertEquals(MkDocsMaterialScheme.DEFAULT, MkDocsMaterialPaletteKeys.schemeOf("default"))
        assertEquals(MkDocsMaterialScheme.SLATE, MkDocsMaterialPaletteKeys.schemeOf("slate"))
        assertNull(MkDocsMaterialPaletteKeys.schemeOf("dark"))
    }

    /**
     * Use case: the custom property behind a colour role. The theme paints `primary` and `accent` through two
     * names of its own, and a style sheet of the site redefines exactly those — so the two must never be
     * confused, and the other roles must carry none.
     */
    fun `test names the custom property of each colour role`() {
        assertEquals(
            MkDocsMaterialPaletteKeys.VARIABLE_PRIMARY,
            MkDocsMaterialPaletteKeys.variableOf(MkDocsMaterialPaletteKeys.Role.PRIMARY),
        )
        assertEquals(
            MkDocsMaterialPaletteKeys.VARIABLE_ACCENT,
            MkDocsMaterialPaletteKeys.variableOf(MkDocsMaterialPaletteKeys.Role.ACCENT),
        )
        assertNull(MkDocsMaterialPaletteKeys.variableOf(MkDocsMaterialPaletteKeys.Role.SCHEME))
        assertNull(MkDocsMaterialPaletteKeys.variableOf(null))
    }

    /**
     * Use case: the ground a colour of a single palette stands on. Which definitions of a style sheet count
     * for that colour is decided by it, so it has to be read off the mapping the colour belongs to.
     */
    fun `test reads the scheme of a palette written as a mapping`() {
        val element = elementAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                scheme: slate
                primary: indigo
            """,
            "indigo",
        )

        assertEquals("slate", MkDocsMaterialPaletteKeys.schemeNameOf(element))
    }

    /**
     * Use case: the same in the sequence form, where the palettes of a colour scheme toggle stand next to each
     * other. The ground of the neighbouring entry says nothing about this one.
     */
    fun `test reads the scheme of the entry a colour belongs to`() {
        val element = elementAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - scheme: default
                  primary: teal
                - scheme: slate
                  primary: indigo
            """,
            "indigo",
        )

        assertEquals("slate", MkDocsMaterialPaletteKeys.schemeNameOf(element))
    }

    /**
     * Use case: a palette that names no ground at all. The theme paints it on its own default one, and a
     * style sheet addresses that ground under exactly that name.
     */
    fun `test falls back to the default scheme`() {
        val element = elementAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """,
            "indigo",
        )

        assertEquals(MkDocsMaterialScheme.DEFAULT.id, MkDocsMaterialPaletteKeys.schemeNameOf(element))
    }

    /**
     * Returns what the value standing at the first occurrence of [marker] in [text] stands for.
     *
     * @param text the content of the configuration file, indented as source
     * @param marker the text the element to judge begins at
     */
    private fun roleAt(text: String, marker: String): MkDocsMaterialPaletteKeys.Role? =
        MkDocsMaterialPaletteKeys.roleOf(elementAt(text, marker))

    /**
     * Returns the element of [text] that stands at the first occurrence of [marker].
     *
     * @param text the content of the configuration file, indented as source
     * @param marker the text the element to return begins at
     */
    private fun elementAt(text: String, marker: String): PsiElement {
        val file = myFixture.configureByText("mkdocs.yml", text.trimIndent() + "\n")
        val offset = file.text.indexOf(marker)
        assertTrue("the fixture must hold '$marker'", offset >= 0)
        val element = file.findElementAt(offset)
        assertNotNull("there must be an element at '$marker'", element)
        return element!!
    }
}
