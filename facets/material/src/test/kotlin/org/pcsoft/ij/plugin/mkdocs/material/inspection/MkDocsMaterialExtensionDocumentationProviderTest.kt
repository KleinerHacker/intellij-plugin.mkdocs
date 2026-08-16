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

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
