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
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Drives the completion popup of a real `mkdocs.yml`. Which values `markdown_extensions` offers is decided by
 * `MkDocsMaterialExtensionCompletionContributor`, but *whether* they reach the popup is decided by the platform
 * — the file has to be recognised as a configuration file of a Material site, and the caret has to sit where
 * the contributor says an extension is named. Only a run of the real completion answers that.
 *
 * `theme.features` is completed here as well, and deliberately so. Those values come from the refined JSON
 * schema rather than from the contributor, so a run where they are missing too says that the fixture never got
 * that far — and that the failure of every other test of this class says nothing about the contributor at all.
 * That control is what showed the first attempt at this feature to be the wrong one: the schema was bound and
 * `theme.features` was offered, while `markdown_extensions` — described there through a chain of `anyOf`
 * branches below a key the base MkDocs schema constrains as well — offered nothing in any of its shapes.
 */
class MkDocsMaterialExtensionCompletionIT : BasePlatformTestCase() {

    /** The theme description the offered entries are built from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /**
     * Use case: the reference point. Completion below `theme.features` offers the feature flags of the theme,
     * which is what tells a failure of the tests below apart from a fixture that never got the schema.
     */
    fun `test offers the feature flags of the theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - <caret>
            """
        )

        assertContainsElements(offered, data.featureFlags.all.map { it.id })
    }

    /**
     * Use case: the shape the documentation of the theme is written in — `markdown_extensions` as a sequence,
     * the author on a fresh entry. Every extension the theme builds upon has to be offered there.
     */
    fun `test offers the markdown extensions in the sequence form`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>
            """
        )

        assertContainsElements(offered, data.extensions.all.map { it.id })
    }

    /**
     * Use case: the same sequence, with entries already written above the caret. The values must not depend on
     * what the list already holds — a completion reached only for the first entry would be worse than none.
     */
    fun `test offers the markdown extensions below an entry that is already written`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - admonition
              - <caret>
            """
        )

        assertContainsElements(offered, data.extensions.all.map { it.id })
    }

    /**
     * Use case: the other shape MkDocs accepts, `markdown_extensions` as a single mapping of extension name to
     * options. The caret sits on a key there rather than on an item of a sequence, so it has to be covered on
     * its own.
     */
    fun `test offers the markdown extensions in the mapping form`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              <caret>
            """
        )

        assertContainsElements(offered, data.extensions.all.map { it.id })
    }

    /**
     * Use case: the mapping form with a configured extension already written above the caret. The entry above
     * carries options of its own, so the caret sits below a block that is deeper than the key it belongs to —
     * and the values must not depend on that any more than they do in the sequence form.
     */
    fun `test offers the markdown extensions in the mapping form below a configured entry`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              toc:
                permalink: true
              <caret>
            """
        )

        assertContainsElements(offered, data.extensions.all.map { it.id })
    }

    /**
     * Use case: an entry of the sequence that carries options, so the extension is the key of a mapping rather
     * than a bare item. That is how the theme documents `toc` and `pymdownx.highlight`, and the caret sits on a
     * key there — a different place in the tree than the two shapes above.
     */
    fun `test offers the markdown extensions on an entry carrying options`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>:
                  permalink: true
            """
        )

        assertContainsElements(offered, data.extensions.all.map { it.id })
    }

    /**
     * Use case: the author has typed the beginning of an extension name. The platform filters the entries
     * against what stands in front of the caret, so exactly the extensions of that package have to survive —
     * and the ones of another package must be gone, otherwise the prefix would be meaningless.
     */
    fun `test keeps the extensions of a typed package and drops the others`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - pymdownx.<caret>
            """
        )

        val expected = data.extensions.all.map { it.id }.filter { it.startsWith(PREFIX_PYMDOWNX) }
        assertFalse("the theme must build on extensions of that package at all", expected.isEmpty())
        assertContainsElements(offered, expected)
        assertDoesntContain(offered, EXTENSION_ADMONITION)
    }

    /**
     * Use case: an extension the list already holds, completed a second time further down. It stays in the
     * popup: hiding it would mean reading the whole list on every keystroke to take away an entry the author
     * is about to overwrite anyway.
     */
    fun `test offers an extension that is already listed again`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - admonition
              - toc
              - <caret>
            """
        )

        assertContainsElements(offered, listOf(EXTENSION_ADMONITION, EXTENSION_TOC))
    }

    /**
     * Use case: reading the popup. An extension asking for an installation of its own has to be told from one
     * Python Markdown already ships, because listing the former without installing it breaks the build of the
     * site — so the package is shown as the type of the entry, and the fallback names Python Markdown.
     */
    fun `test shows the package an extension comes from as its type`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>
            """
        )

        val superfences = data.extensions.all.first { it.id == EXTENSION_SUPERFENCES }
        assertNotNull("the extension must come from a package of its own", superfences.pipPackage)
        assertEquals(superfences.pipPackage, presentationOf(EXTENSION_SUPERFENCES).typeText)

        val admonition = data.extensions.all.first { it.id == EXTENSION_ADMONITION }
        assertNull("the extension must be one Python Markdown ships", admonition.pipPackage)
        assertEquals(PYTHON_MARKDOWN, presentationOf(EXTENSION_ADMONITION).typeText)
    }

    /**
     * Use case: the same entry read for its description. What an extension does is the question an author
     * actually has in front of a list of dotted identifiers, so the one line description of the bundle is
     * shown behind the name.
     */
    fun `test shows the description of an extension as its tail`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>
            """
        )

        val admonition = data.extensions.all.first { it.id == EXTENSION_ADMONITION }
        val description = MkDocsMaterialBundle.messageOrDefault(admonition.descriptionKey, admonition.id)
        assertNotNull("the bundle must describe the extension", description)
        assertEquals("  $description", presentationOf(EXTENSION_ADMONITION).tailText)
    }

    /**
     * Use case: the options of an extension that is already named, one level deeper. `permalink` belongs to
     * `toc` and an extension name is not an answer there — offering one would put a line into the file that
     * MkDocs cannot read.
     */
    fun `test stays out of the options of an extension`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  <caret>
            """
        )

        assertDoesntContain(offered, EXTENSION_SUPERFENCES)
    }

    /**
     * Use case: another list of the very same configuration file. `plugins` holds the plugins of MkDocs, and a
     * Markdown extension written there is read by nothing — the key decides, not the shape of the list.
     */
    fun `test stays out of another list of the configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            plugins:
              - <caret>
            """
        )

        assertDoesntContain(offered, EXTENSION_SUPERFENCES)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very content
     * that gets the extensions under the name of one. Its name decides, exactly as everywhere else in the
     * plugin — a workflow file listing a key of that name is not a site.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>
            """,
            name = "other.yml"
        )

        assertDoesntContain(offered, EXTENSION_SUPERFENCES)
    }

    /**
     * Use case: a site that is not rendered with the Material theme. The extensions offered here are the ones
     * that theme builds upon, and a site on another one has no business being told about them.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: readthedocs
            markdown_extensions:
              - <caret>
            """
        )

        assertDoesntContain(offered, EXTENSION_SUPERFENCES)
    }

    /**
     * Returns how the popup renders the entry offering [lookupString], of the completion run last.
     *
     * @param lookupString the identifier of the extension the entry offers
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
     * The site is detected before the caret is asked, because `theme.features` is completed from the schema and
     * that schema is bound to the facet the detection attaches. The cached answer of the schema provider is
     * dropped afterwards — the light fixture hands every test of a class the same project, and with it the
     * answer given for the file of the test before.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @param name the file name to write the content under
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String, name: String = "mkdocs.yml"): List<String> {
        myFixture.configureByText(name, text.trimIndent() + "\n")
        MkDocsModuleService.getInstance(project).sync()
        MkDocsMaterialSchemaCache.invalidate(project)

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private companion object {

        /** The type shown for an extension no separate package has to be installed for. */
        const val PYTHON_MARKDOWN = "Python Markdown"

        /** The package prefix of the extensions PyMdown Extensions ships. */
        const val PREFIX_PYMDOWNX = "pymdownx."

        /** An extension of that package, which no site gets without installing it. */
        const val EXTENSION_SUPERFENCES = "pymdownx.superfences"

        /** An extension Python Markdown itself ships. */
        const val EXTENSION_ADMONITION = "admonition"

        /** Another extension Python Markdown itself ships, written with options by the theme. */
        const val EXTENSION_TOC = "toc"
    }
}
