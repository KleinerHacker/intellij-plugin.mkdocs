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

package org.pcsoft.ij.plugin.mkdocs.material.inspection

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the quick documentation of a value of `theme.palette` already written into the file. The provider is
 * driven directly rather than through the platform: what it answers is its own business, whether it is asked
 * at all is a question of the registration and belongs to the plugin project.
 */
class MkDocsMaterialPaletteDocumentationProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsMaterialPaletteDocumentationProvider()

    /** The theme description the expected texts are read from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /**
     * Use case: *Ctrl+Q* on the primary colour of a palette. The popup has to name the colour, say what it
     * paints, name the role it plays and show the shade the swatch stands for.
     */
    fun `test explains the primary colour`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """,
            "indigo",
        )

        assertNotNull(doc)
        val indigo = data.colors.byId("indigo")!!
        assertTrue(doc!!.contains("indigo"))
        assertTrue(doc.contains(MkDocsMaterialBundle.message(indigo.descriptionKey)))
        assertTrue(doc.contains(MkDocsMaterialBundle.message("material.palette.color.type.primary")))
        assertTrue("the shade of the swatch must be shown", doc.contains("#3F51B5"))
    }

    /**
     * Use case: the accent colour of the same palette. The role shown has to be the accent one — the two keys
     * do not accept the same set, and a popup naming the wrong role would say the colour is usable where it
     * is not.
     */
    fun `test explains the accent colour with its own role`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                accent: pink
            """,
            "pink",
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains(MkDocsMaterialBundle.message("material.palette.color.type.accent")))
    }

    /**
     * Use case: the `custom` placeholder. It explains itself like every other value, but carries no shade: the
     * site defines that colour in its own style sheet, and a square painted here would show a colour that
     * appears nowhere in the built site.
     */
    fun `test explains custom without a shade`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: custom
            """,
            "custom",
        )

        assertNotNull(doc)
        val custom = data.colors.custom!!
        assertTrue(doc!!.contains(MkDocsMaterialBundle.message(custom.descriptionKey)))
        assertFalse(doc.contains(MkDocsMaterialBundle.message("material.palette.doc.shade")))
    }

    /**
     * Use case: the ground the palette is painted on. `default` and `slate` say nothing about which of them is
     * the dark one, which is the question the popup answers.
     */
    fun `test explains the scheme`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                scheme: slate
            """,
            "slate",
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("slate"))
        assertTrue(doc.contains(MkDocsMaterialBundle.message("material.scheme.slate.description")))
    }

    /**
     * Use case: the media query of the same palette. It is an ordinary CSS media query and the browser decides
     * what it means, so this provider deliberately has nothing to say about it.
     */
    fun `test says nothing about the media query`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-color-scheme: dark)"
            """,
            "prefers-color-scheme: dark",
        )

        assertNull(doc)
    }

    /**
     * Use case: a value the theme does not know, below a key of the palette. Either a typo, which the schema
     * reports on its own, or something a later version of the theme brought along — inventing a description
     * for it would state something nobody checked.
     */
    fun `test says nothing about an unknown colour`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: chartreuse
            """,
            "chartreuse",
        )

        assertNull(doc)
    }

    /**
     * Use case: a colour the theme accepts for the other role only. `white` exists for the primary colour, and
     * naming it below `accent` produces a site the theme cannot build — so there is nothing to explain.
     */
    fun `test says nothing about a colour the role does not accept`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                accent: white
            """,
            "white",
        )

        assertNull(doc)
    }

    /**
     * Use case: a key of the same name at another place of the file. `primary` below `extra` belongs to
     * whoever reads it, and the path decides — not the name of the nearest key.
     */
    fun `test says nothing about a key of the same name elsewhere`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              primary: indigo
            """,
            "indigo",
        )

        assertNull(doc)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs. Its name decides,
     * exactly as everywhere else in the plugin.
     */
    fun `test says nothing in a YAML file that is not a configuration file`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """,
            "indigo",
            name = "other.yml",
        )

        assertNull(doc)
    }

    /**
     * Returns what the provider says about the value standing at the first occurrence of [marker].
     *
     * @param text the content of the configuration file, indented as source
     * @param marker the text the value to ask about begins at
     * @param name the file name to write the content under
     */
    private fun docAt(text: String, marker: String, name: String = "mkdocs.yml"): String? =
        provider.generateDoc(null, elementAt(text, marker, name))

    /**
     * Returns the element of [text] that stands at the first occurrence of [marker].
     *
     * @param text the content of the configuration file, indented as source
     * @param marker the text the element to return begins at
     * @param name the file name to write the content under
     */
    private fun elementAt(text: String, marker: String, name: String): PsiElement {
        val file = myFixture.configureByText(name, text.trimIndent() + "\n")
        val offset = file.text.indexOf(marker)
        assertTrue("the fixture must hold '$marker'", offset >= 0)
        val element = file.findElementAt(offset)
        assertNotNull("there must be an element at '$marker'", element)
        return element!!
    }
}
