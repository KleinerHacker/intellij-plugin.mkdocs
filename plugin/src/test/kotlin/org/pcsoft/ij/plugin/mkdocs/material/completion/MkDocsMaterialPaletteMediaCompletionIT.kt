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

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Drives the completion popup of a real `mkdocs.yml` at `theme.palette.media`. Which queries are offered is
 * decided by `MkDocsMaterialPaletteMediaCompletionContributor`, but *whether* they reach the popup is decided
 * by the platform: the file has to be recognised as the configuration file of a Material site, the caret has
 * to sit in the value of that key, and the JSON schema describing the key as a plain string must not swallow
 * the entries. Only a run of the real completion answers that.
 */
class MkDocsMaterialPaletteMediaCompletionIT : BasePlatformTestCase() {

    /** The queries the popup has to offer, read from the one place that decides them. */
    private val expected get() = MkDocsMaterialPaletteKeys.MEDIA_QUERIES.map { it.query }

    /**
     * Use case: the shape the documentation of the theme is written in — the palette as a sequence, the caret
     * on the `media` of a fresh entry. All three queries have to be offered there.
     */
    fun `test offers the queries in the sequence form`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: <caret>
            """
        )

        assertContainsElements(offered, expected)
    }

    /**
     * Use case: the other shape the key accepts, a palette written as a single mapping. The caret sits below
     * `palette` directly rather than in an item of a sequence, so it has to be covered on its own.
     */
    fun `test offers the queries in the mapping form`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: <caret>
            """
        )

        assertContainsElements(offered, expected)
    }

    /**
     * Use case: an entry that already carries other keys, and a second entry written above it. The queries
     * must not depend on what the palette already holds — a completion reached only for the first entry would
     * be worse than none.
     */
    fun `test offers the queries below an entry that is already written`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "${MkDocsMaterialConfig.MEDIA_LIGHT}"
                  scheme: default
                - scheme: slate
                  media: <caret>
            """
        )

        assertContainsElements(offered, expected)
    }

    /**
     * Use case: the author has typed the beginning of the query inside the quotes. A media query carries
     * parentheses, a colon and a space — none of them part of an identifier — so without a prefix of its own
     * the popup would match the entries against nothing and offer all three however far the author has got.
     */
    fun `test keeps the queries matching what has been typed and drops the others`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-color-scheme: <caret>"
            """
        )

        assertContainsElements(
            offered,
            listOf(MkDocsMaterialConfig.MEDIA_LIGHT, MkDocsMaterialConfig.MEDIA_DARK),
        )
        assertDoesntContain(offered, MkDocsMaterialConfig.MEDIA_SYSTEM)
    }

    /**
     * Use case: reading the popup. A query says nothing about the appearance it stands for, so each entry
     * carries the sentence describing when its palette applies, and the mark of the theme it comes from.
     */
    fun `test shows what a query stands for`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: <caret>
            """
        )

        val presentation = presentationOf(MkDocsMaterialConfig.MEDIA_DARK)
        assertEquals("media query", presentation.typeText)
        assertNotNull("the entry must say when its palette applies", presentation.tailText)
        assertTrue(presentation.tailText!!.contains("dark"))
        assertNotNull("the entry must carry the mark of the theme", presentation.icon)
    }

    /**
     * Use case: taking an entry. `(prefers-color-scheme: dark)` carries a colon followed by a space, which
     * ends a plain scalar — written unquoted, YAML reads the rest as a mapping and the file stops parsing. So
     * the value has to arrive in the file quoted.
     */
    fun `test writes the query into the file in quotes`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: <caret>
            """
        )
        take(MkDocsMaterialConfig.MEDIA_DARK)

        assertTrue(myFixture.file.text.contains("""media: "${MkDocsMaterialConfig.MEDIA_DARK}""""))
    }

    /**
     * Use case: the same entry taken where the author has already typed the quotes. A second pair around the
     * value would be as broken as none, so an existing quote is kept.
     */
    fun `test adds no second pair of quotes`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-color-scheme: d<caret>"
            """
        )
        take(MkDocsMaterialConfig.MEDIA_DARK)

        assertTrue(myFixture.file.text.contains("""media: "${MkDocsMaterialConfig.MEDIA_DARK}""""))
        assertFalse("the value must not end up wrapped twice", myFixture.file.text.contains("\"\""))
    }

    /**
     * Use case: the toggle of the same palette, one level deeper. `icon` and `name` are what a toggle carries
     * and a media query is no answer there — offering one would put a line into the file the theme cannot
     * read.
     */
    fun `test stays out of the toggle of a palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "${MkDocsMaterialConfig.MEDIA_LIGHT}"
                  toggle:
                    icon: <caret>
            """
        )

        assertDoesntContain(offered, MkDocsMaterialConfig.MEDIA_DARK)
    }

    /**
     * Use case: a key of the same name at another place of the very same file. `media` below `extra` belongs
     * to whoever reads it — the path decides, not the name of the nearest key.
     */
    fun `test stays out of a key of the same name elsewhere`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              media: <caret>
            """
        )

        assertDoesntContain(offered, MkDocsMaterialConfig.MEDIA_DARK)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very
     * content that gets the queries under the name of one. Its name decides, exactly as everywhere else in
     * the plugin.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: <caret>
            """,
            name = "other.yml",
        )

        assertDoesntContain(offered, MkDocsMaterialConfig.MEDIA_DARK)
    }

    /**
     * Use case: a site that is not rendered with the Material theme. `theme.palette` is the theme's own key,
     * and a site on another one has no business being told what to write there.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: readthedocs
              palette:
                media: <caret>
            """
        )

        assertDoesntContain(offered, MkDocsMaterialConfig.MEDIA_DARK)
    }

    /**
     * Puts the entry offering [lookupString] into the file, of the completion run last.
     *
     * Both ways an entry reaches the file are covered, because which one happens is not the test's to decide:
     * a prefix narrow enough to leave a single match is taken by the platform on its own and closes the popup
     * before this is reached, while several matches leave the popup open and the entry has to be chosen.
     *
     * @param lookupString the query the entry offers
     */
    private fun take(lookupString: String) {
        val lookup = myFixture.lookup
        if (lookup == null) {
            // Taken on its own — nothing to choose, and the file already holds the result.
            assertTrue(
                "the completion must have inserted $lookupString",
                myFixture.file.text.contains(lookupString),
            )
            return
        }
        val element = lookup.items.firstOrNull { it.lookupString == lookupString }
        assertNotNull("the popup must offer $lookupString", element)
        lookup.currentItem = element
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    /**
     * Returns how the popup renders the entry offering [lookupString], of the completion run last.
     *
     * @param lookupString the query the entry offers
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
     * The site is detected before the caret is asked, because the refined schema of the theme is bound to the
     * facet the detection attaches. The cached answer of the schema provider is dropped afterwards — the light
     * fixture hands every test of a class the same project, and with it the answer given for the file of the
     * test before.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @param name the file name to write the content under
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String, name: String = "mkdocs.yml"): List<String> {
        myFixture.configureByText(name, text.trimIndent() + "\n")
        project.service<MkDocsModuleService>().sync()
        MkDocsMaterialSchemaCache.invalidate(project)

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }
}
