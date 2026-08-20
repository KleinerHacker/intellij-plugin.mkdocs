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

package org.pcsoft.ij.plugin.mkdocs.material.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Drives the completion popup of a real `mkdocs.yml` below `theme.palette`. The colours themselves come from
 * the refined JSON schema; that each of them is rendered with a swatch of the shade it stands for is done by
 * `MkDocsMaterialOriginCompletionContributor`, which decorates whatever the schema produced. Whether the two
 * meet is decided by the platform, so only a run of the real completion answers it.
 */
class MkDocsMaterialPaletteColorCompletionIT : BasePlatformTestCase() {

    /** The theme description the expected entries are read from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /**
     * Use case: the reference point. `theme.palette.primary` offers exactly the colours the theme accepts for
     * it — a failure here says the schema never reached the file, and makes the assertions below meaningless.
     */
    fun `test offers the primary colours of the theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: <caret>
            """
        )

        assertContainsElements(offered, data.colors.primaries().map { it.id })
    }

    /**
     * Use case: reading the popup. A colour is offered by its name, and a name paints nothing an author can
     * see — so every colour carries the square of the shade it stands for, and two different colours must not
     * end up with the same one.
     */
    fun `test paints every colour with the swatch of its shade`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: <caret>
            """
        )

        data.colors.primaries().filterNot { it.custom }.forEach { color ->
            assertSame(
                "the entry '${color.id}' must carry the swatch of its shade",
                MkDocsMaterialIcons.color(color.hex),
                presentationOf(color.id).icon,
            )
        }
    }

    /**
     * Use case: the `custom` placeholder. It is no colour of the palette — the site defines it in its own
     * style sheet through the `--md-*` properties — so it carries the plain mark of the theme, and a square
     * showing a shade that appears nowhere in the built site would be a lie.
     */
    fun `test leaves custom with the plain mark of the theme`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: <caret>
            """
        )

        val custom = data.colors.custom
        assertNotNull("the theme description must carry the custom placeholder", custom)
        assertSame(MkDocsMaterialIcons.Badge, presentationOf(custom!!.id).icon)
    }

    /**
     * Use case: the accent colour, which is the other half of the same palette. It is a different set of
     * colours and a different key, and the swatches have to arrive there just the same.
     */
    fun `test paints the accent colours as well`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                accent: <caret>
            """
        )

        val pink = data.colors.accents().first { it.id == "pink" }
        assertSame(MkDocsMaterialIcons.color(pink.hex), presentationOf(pink.id).icon)
    }

    /**
     * Use case: the palette written as a sequence, which is how a colour scheme toggle is configured. The
     * swatch must not depend on the shape the palette is written in.
     */
    fun `test paints the colours of a palette written as a sequence`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "(prefers-color-scheme: dark)"
                  scheme: slate
                  primary: <caret>
            """
        )

        val teal = data.colors.primaries().first { it.id == "teal" }
        assertSame(MkDocsMaterialIcons.color(teal.hex), presentationOf(teal.id).icon)
    }

    /**
     * Use case: the ground the palette is painted on. `default` and `slate` are no colours, so they keep the
     * plain mark of the theme — a square would claim a shade the value does not stand for.
     */
    fun `test leaves the scheme with the plain mark of the theme`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertSame(MkDocsMaterialIcons.Badge, presentationOf("slate").icon)
    }

    /**
     * Use case: a key of the same name at another place of the file. `primary` below `extra` is none of the
     * theme's, and a colour written there is a word like any other — no swatch, and nothing claiming the
     * value belongs to the palette.
     */
    fun `test paints nothing below a key of the same name elsewhere`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              primary: <caret>
            """
        )

        val indigo = (myFixture.lookupElements ?: emptyArray<LookupElement>())
            .firstOrNull { it.lookupString == "indigo" }
        if (indigo != null) {
            val icon = LookupElementPresentation.renderElement(indigo).icon
            assertNotSame(MkDocsMaterialIcons.color(data.colors.byId("indigo")!!.hex), icon)
        }
    }

    /**
     * Returns how the popup renders the entry offering [lookupString], of the completion run last.
     *
     * @param lookupString the identifier of the colour the entry offers
     */
    private fun presentationOf(lookupString: String): LookupElementPresentation {
        val element = (myFixture.lookupElements ?: emptyArray<LookupElement>())
            .firstOrNull { it.lookupString == lookupString }
        assertNotNull("the popup must offer $lookupString", element)
        return LookupElementPresentation.renderElement(element!!)
    }

    /**
     * Runs completion in an `mkdocs.yml` holding [text] and returns what it offers.
     *
     * The site is detected before the caret is asked, because the colours come from the refined schema and
     * that schema is bound to the facet the detection attaches. The cached answer of the schema provider is
     * dropped afterwards — the light fixture hands every test of a class the same project, and with it the
     * answer given for the file of the test before.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String): List<String> {
        myFixture.configureByText("mkdocs.yml", text.trimIndent() + "\n")
        project.service<MkDocsModuleService>().sync()
        MkDocsMaterialSchemaCache.invalidate(project)

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }
}
