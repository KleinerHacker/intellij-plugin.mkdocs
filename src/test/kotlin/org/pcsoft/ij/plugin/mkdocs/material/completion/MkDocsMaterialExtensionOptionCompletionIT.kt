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
import org.jetbrains.yaml.YAMLLanguage
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Drives the completion popup one level below `markdown_extensions`, where an extension is configured: `- toc:`
 * and then `permalink` under it. Which entries are offered is decided by
 * `MkDocsMaterialExtensionOptionCompletionContributor`, but *whether* they reach the popup is decided by the
 * platform — that level is described by no schema at all, which is why it stayed empty before.
 *
 * The expected entries are read from the data service rather than written down here: an option added to
 * `material/spec/markdown-extensions.yaml` has to arrive in the popup without this test being touched.
 */
class MkDocsMaterialExtensionOptionCompletionIT : BasePlatformTestCase() {

    /** The theme description the offered entries are built from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /** The options of the extension the theme documents with options. */
    private val tocOptions get() = data.extensions.byId(EXTENSION_TOC)!!.options.map { it.key }

    /**
     * Use case: the shape the documentation of the theme is written in — the extension as an entry of the
     * sequence, the caret on the first option below it. Every option of that extension has to be offered.
     */
    fun `test offers the options of an extension in the sequence form`() {
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

        assertContainsElements(offered, tocOptions)
    }

    /**
     * Use case: the other shape MkDocs accepts, `markdown_extensions` as a single mapping. The extension is a
     * key there rather than an item of a sequence, so the options sit at a different place in the tree.
     */
    fun `test offers the options of an extension in the mapping form`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              toc:
                <caret>
            """
        )

        assertContainsElements(offered, tocOptions)
    }

    /**
     * Use case: an option already written above the caret. The entries must not depend on what the mapping
     * already holds — a completion reached only for the first option would be worse than none.
     */
    fun `test offers the options below an option that is already written`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  permalink: true
                  <caret>
            """
        )

        assertContainsElements(offered, tocOptions)
    }

    /**
     * Use case: the value of a flag. `true` and `false` are the only two things that belong behind such an
     * option, and typing them by hand is exactly what a popup is for.
     */
    fun `test offers the values of a flag`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  permalink: <caret>
            """
        )

        assertContainsElements(offered, listOf("true", "false"))
    }

    /**
     * Use case: the value of an option taking a fixed set. Which values those are is written in the resource,
     * and nothing in the file itself says them.
     */
    fun `test offers the values of a choice`() {
        val option = data.extensions.byId(EXTENSION_CRITIC)!!.optionByKey(OPTION_MODE)!!
        assertFalse("the option must take a fixed set of values", option.values.isEmpty())

        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - pymdownx.critic:
                  mode: <caret>
            """
        )

        assertContainsElements(offered, option.values)
    }

    /**
     * Use case: reading the popup. What an option takes is shown as the type of the entry; what it does is not
     * written behind the name, because a popup whose every entry carries a sentence is read by nobody.
     */
    fun `test shows the kind of an option and no description`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  <caret>
            """
        )

        val option = data.extensions.byId(EXTENSION_TOC)!!.optionByKey(OPTION_PERMALINK)!!
        val presentation = presentationOf(OPTION_PERMALINK)
        assertEquals(option.kind.name.lowercase(), presentation.typeText)
        assertNull(presentation.tailText)
    }

    /**
     * Use case: *Ctrl+Q* inside the popup. The platform asks the offered entry for the element behind it before
     * it asks anyone for documentation, so an entry made of a bare string leaves that key without an answer.
     */
    fun `test carries the element its documentation is generated for`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  <caret>
            """
        )

        val element = (myFixture.lookupElements ?: emptyArray<LookupElement>())
            .firstOrNull { it.lookupString == OPTION_PERMALINK }
        assertNotNull("the popup must offer $OPTION_PERMALINK", element)
        val target = element!!.psiElement
        assertNotNull("the entry must carry an element to document", target)

        // The language of that element decides which providers are asked at all: measured, an element without
        // one reaches no provider of the facet, and the popup shows the bare name of the element instead.
        assertEquals(YAMLLanguage.INSTANCE, target!!.language)
    }

    /**
     * Use case: the level of the extension itself. An option written where an extension name belongs would
     * produce a line MkDocs reads as an extension that does not exist.
     */
    fun `test stays out of the level of the extension`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - <caret>
            """
        )

        assertDoesntContain(offered, OPTION_PERMALINK)
    }

    /**
     * Use case: an option of another extension. The options belong to the extension above them — offering the
     * ones of `toc` below `pymdownx.critic` would put a key there that the extension never reads.
     */
    fun `test offers no option of another extension`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - pymdownx.critic:
                  <caret>
            """
        )

        assertContainsElements(offered, listOf(OPTION_MODE))
        assertDoesntContain(offered, OPTION_PERMALINK)
    }

    /**
     * Use case: a key of the same name in another block of the configuration file. `toc` below `plugins` is a
     * plugin, and what stands below it has nothing to do with the Markdown extension.
     */
    fun `test stays out of another block of the configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            plugins:
              - toc:
                  <caret>
            """
        )

        assertDoesntContain(offered, OPTION_PERMALINK)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very content
     * that gets the options under the name of one. Its name decides, exactly as everywhere else in the plugin.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  <caret>
            """,
            name = "other.yml"
        )

        assertDoesntContain(offered, OPTION_PERMALINK)
    }

    /**
     * Use case: a site that is not rendered with the Material theme. The extensions these options belong to are
     * the ones that theme builds upon, and a site on another one has no business being told about them.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: readthedocs
            markdown_extensions:
              - toc:
                  <caret>
            """
        )

        assertDoesntContain(offered, OPTION_PERMALINK)
    }

    /**
     * Returns how the popup renders the entry offering [lookupString], of the completion run last.
     *
     * @param lookupString the name of the option the entry offers
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
     * The site is detected before the caret is asked, and the cached answer of the schema provider is dropped
     * afterwards — the light fixture hands every test of a class the same project, and with it the answer given
     * for the file of the test before.
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

    private companion object {

        /** The extension the theme documents with options of its own. */
        const val EXTENSION_TOC = "toc"

        /** An extension whose only option takes a fixed set of values. */
        const val EXTENSION_CRITIC = "pymdownx.critic"

        /** An option of that extension. */
        const val OPTION_MODE = "mode"

        /** The option of `toc` the theme recommends. */
        const val OPTION_PERMALINK = "permalink"
    }
}
