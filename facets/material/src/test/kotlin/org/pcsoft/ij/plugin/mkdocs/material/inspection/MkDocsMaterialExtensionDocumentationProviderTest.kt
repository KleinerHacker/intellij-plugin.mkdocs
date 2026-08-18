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
import com.intellij.psi.PsiManager
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the quick documentation offered for the entries of `markdown_extensions`: what it says, and which
 * elements it refuses to say anything about.
 */
class MkDocsMaterialExtensionDocumentationProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsMaterialExtensionDocumentationProvider()

    /**
     * Use case: the popup on a plain entry. The author's question in front of that list is whether they need
     * the entry, so the description and the link to its own documentation are what has to appear.
     */
    fun `test explains a plain entry`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - admo<caret>nition
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("admonition"))
        assertTrue(doc.contains("Call-out blocks"))
        assertTrue(doc.contains("python-markdown.github.io"))
    }

    /**
     * Use case: an entry written as a mapping because it carries options. The identifier is the key of that
     * mapping, and the popup has to find it there just as it does on the scalar form.
     */
    fun `test explains an entry configured with options`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - pymdownx.high<caret>light:
                  anchor_linenums: true
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("Pygments"))
        assertTrue(doc.contains("pymdown-extensions"))
    }

    /**
     * Use case: an entry of another sequence that happens to spell an extension. Only `markdown_extensions`
     * is answered — everywhere else the same word means something entirely different.
     */
    fun `test says nothing about another sequence`() {
        val doc = docAt(
            """
            site_name: Handbook
            plugins:
              - admo<caret>nition
            """.trimIndent()
        )

        assertNull(doc)
    }

    /**
     * Use case: an extension this plugin does not know. Saying nothing leaves whoever else has something to
     * say about it a chance to answer.
     */
    fun `test says nothing about an unknown extension`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - some.other.exten<caret>sion
            """.trimIndent()
        )

        assertNull(doc)
    }

    /**
     * Use case: the popup on an option below an entry. The level no schema describes: what the option does,
     * what it takes and what the extension falls back to without it is the whole question there.
     */
    fun `test explains an option of an entry`() {
        val option = service<MkDocsMaterialDataService>().extensions.byId("toc")!!.optionByKey("toc_depth")!!
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - toc:
                  toc_de<caret>pth: 2-4
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("toc.toc_depth"))
        assertTrue(doc.contains(MkDocsMaterialBundle.message(option.descriptionKey)))
        assertTrue(doc.contains(option.defaultValue!!))
    }

    /**
     * Use case: an option taking a fixed set of values. Which values those are is what an author cannot read
     * off the file, so the popup lists them.
     */
    fun `test lists the values of an option`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - pymdownx.critic:
                  mo<caret>de: view
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("accept"))
        assertTrue(doc.contains("reject"))
    }

    /**
     * Use case: the same option written in the mapping form of `markdown_extensions`, without the sequence
     * item in between. The popup has to find it there just as it does below a sequence entry.
     */
    fun `test explains an option in the mapping form`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              toc:
                perma<caret>link: true
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("toc.permalink"))
    }

    /**
     * Use case: an option this plugin does not know, written below an extension it does. Saying nothing leaves
     * whoever else has something to say about it a chance to answer.
     */
    fun `test says nothing about an unknown option`() {
        val doc = docAt(
            """
            site_name: Handbook
            markdown_extensions:
              - toc:
                  not_an_op<caret>tion: true
            """.trimIndent()
        )

        assertNull(doc)
    }

    /**
     * Use case: *Ctrl+Q* inside the completion popup, on an entry offering an extension. The entry is a plain
     * string that is not in the file yet, so the platform asks for a PSI element first — without one the popup
     * stays empty while the very same name answers once it is written down.
     */
    fun `test explains an extension offered in the popup`() {
        val doc = lookupDocAt(
            """
            site_name: Handbook
            markdown_extensions:
              - <caret>
            """,
            offered = "pymdownx.superfences"
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("pymdownx.superfences"))
        assertTrue(doc.contains("pymdown-extensions"))
    }

    /**
     * Use case: the same key inside the popup one level deeper, on an entry offering an option. What the option
     * does is what was taken out of the popup itself, so this is where it has to be readable.
     */
    fun `test explains an option offered in the popup`() {
        val option = service<MkDocsMaterialDataService>().extensions.byId("toc")!!.optionByKey("permalink")!!
        val doc = lookupDocAt(
            """
            site_name: Handbook
            markdown_extensions:
              - toc:
                  pe<caret>rm
            """,
            offered = "permalink"
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("toc.permalink"))
        assertTrue(doc.contains(MkDocsMaterialBundle.message(option.descriptionKey)))
    }

    /**
     * Use case: an entry of the popup that comes from somewhere else — a value of another contributor, or an
     * option this plugin does not know. Answering it would put the documentation of a stranger on it.
     */
    fun `test says nothing about an offered entry it does not know`() {
        val doc = lookupDocAt(
            """
            site_name: Handbook
            markdown_extensions:
              - <caret>
            """,
            offered = "some.other.extension"
        )

        assertNull(doc)
    }

    /**
     * Use case: the same popup entry in a block that is not `markdown_extensions`. The list decides, not the
     * word — a plugin of that name is not the Markdown extension of that name.
     */
    fun `test says nothing about an offered entry of another block`() {
        val doc = lookupDocAt(
            """
            site_name: Handbook
            plugins:
              - <caret>
            """,
            offered = "toc"
        )

        assertNull(doc)
    }

    /**
     * Returns the documentation the provider generates for the popup entry [offered], asked at the caret of
     * [text] the way the platform asks it while the popup is open.
     *
     * @param text the content of the configuration file, carrying a `<caret>` marker
     * @param offered the lookup string of the entry the popup is opened on
     */
    private fun lookupDocAt(text: String, offered: String): String? {
        myFixture.configureByText("mkdocs.yml", text.trimIndent() + "\n")
        val context: PsiElement = myFixture.file.findElementAt(myFixture.caretOffset)
            ?: error("no element at caret, found ${myFixture.file.elementType}")
        val target = provider.getDocumentationElementForLookupItem(
            PsiManager.getInstance(project),
            offered,
            context,
        ) ?: return null
        return provider.generateDoc(target, context)
    }

    /**
     * Returns the documentation the provider generates at the caret of [text].
     *
     * @param text the content of the configuration file, carrying a `<caret>` marker
     */
    private fun docAt(text: String): String? {
        myFixture.configureByText("mkdocs.yml", text + "\n")
        val element: PsiElement = myFixture.file.findElementAt(myFixture.caretOffset)
            ?: error("no element at caret, found ${myFixture.file.elementType}")
        return provider.generateDoc(element, element)
    }
}
