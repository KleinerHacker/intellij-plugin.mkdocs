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
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the quick documentation offered for the entries of `theme.features`: what it says, and which
 * elements it refuses to say anything about.
 */
class MkDocsMaterialFeatureDocumentationProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsMaterialFeatureDocumentationProvider()

    /**
     * Use case: the popup on a plain flag. The question in front of that list is what the flag does, so the
     * description of the flag, the section it changes and the link into the documentation of the theme are
     * what has to appear.
     */
    fun `test explains a flag`() {
        val flag = service<MkDocsMaterialDataService>().featureFlags.byId("navigation.tabs")!!
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - navigation.ta<caret>bs
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("navigation.tabs"))
        assertTrue(doc.contains(MkDocsMaterialBundle.message(flag.descriptionKey)))
        assertTrue(doc.contains(MkDocsMaterialBundle.message(flag.group.titleKey)))
        assertTrue(doc.contains(flag.docUrl))
    }

    /**
     * Use case: a flag that only works together with others. What it needs, what it clashes with and which
     * Markdown extensions it forces is exactly what an author cannot read off the file itself.
     */
    fun `test names the relations of a flag`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - toc.inte<caret>grate
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("toc.follow"))
        assertTrue(doc.contains("navigation.indexes"))
    }

    /**
     * Use case: a flag of an *Insiders* build. Nothing in the file says so, and a site built with the public
     * package silently renders nothing for it — so the popup has to say it.
     */
    fun `test marks a flag of an insiders build`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - navigation.tabs.st<caret>icky
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains(MkDocsMaterialBundle.message("material.page.features.insiders")))
        assertTrue(doc.contains("navigation.tabs"))
    }

    /**
     * Use case: a flag that forces a Markdown extension. The extension is what has to be listed elsewhere in
     * the same file, so the popup names it.
     */
    fun `test names the forced markdown extensions`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.code.cop<caret>y
            """.trimIndent()
        )

        assertNotNull(doc)
        assertTrue(doc!!.contains("pymdownx.highlight"))
    }

    /**
     * Use case: a `features` key of another block. Only the list below `theme` is the feature list of the
     * theme — everywhere else the same word means something entirely different.
     */
    fun `test says nothing about another features key`() {
        val doc = docAt(
            """
            site_name: Handbook
            extra:
              features:
                - navigation.ta<caret>bs
            """.trimIndent()
        )

        assertNull(doc)
    }

    /**
     * Use case: a flag this plugin does not know. Saying nothing leaves whoever else has something to say
     * about it a chance to answer.
     */
    fun `test says nothing about an unknown flag`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - some.other.fla<caret>g
            """.trimIndent()
        )

        assertNull(doc)
    }

    /**
     * Use case: the same list in a file that is not a configuration file of MkDocs. The flag means nothing
     * there, and the popup must not claim otherwise.
     */
    fun `test says nothing outside a configuration file`() {
        val doc = docAt(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - navigation.ta<caret>bs
            """.trimIndent(),
            fileName = "other.yml"
        )

        assertNull(doc)
    }

    /**
     * Returns the documentation the provider generates at the caret of [text].
     *
     * @param text the content of the file, carrying a `<caret>` marker
     * @param fileName the name the file is configured under
     */
    private fun docAt(text: String, fileName: String = "mkdocs.yml"): String? {
        myFixture.configureByText(fileName, text + "\n")
        val element: PsiElement = myFixture.file.findElementAt(myFixture.caretOffset)
            ?: error("no element at caret, found ${myFixture.file.elementType}")
        return provider.generateDoc(element, element)
    }
}
