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

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the weak warning about the Markdown extensions the Material theme builds on: when it appears, when
 * it stays away, and that it never claims something is broken.
 */
class MkDocsMaterialRecommendedExtensionInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MkDocsMaterialRecommendedExtensionInspection())
    }

    /**
     * Use case: a fresh Material site listing no extension at all. Everything the theme builds on is missing,
     * so every recommendation is offered — one warning per extension, each with its own fix.
     */
    fun `test reports every recommended extension of a bare site`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
            """.trimIndent()
        )

        assertEquals(MkDocsMarkdownExtension.recommended().size, warnings.size)
        assertTrue(warnings.any { it.description.contains("admonition") })
    }

    /**
     * Use case: an extension the file already lists must not be recommended again — a warning that cannot be
     * acted on is noise.
     */
    fun `test drops what the file already lists`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - admonition
            """.trimIndent()
        )

        assertTrue(warnings.none { it.description.contains("'admonition'") })
    }

    /**
     * Use case: an extension written as a mapping because it carries options. Only the identifier is read, so
     * `toc` with a `permalink` below it counts as listed just like the plain entry does.
     */
    fun `test reads an extension configured with options`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - toc:
                  permalink: true
            """.trimIndent()
        )

        assertTrue(warnings.none { it.description.contains("'toc'") })
    }

    /**
     * Use case: nothing here is broken — a site keeping its Markdown plain builds and renders. The severity
     * has to say so, otherwise the author is told off for a decision that was theirs to make.
     */
    fun `test reports as a weak warning`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
            """.trimIndent()
        )

        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.all { it.severity == HighlightSeverity.WEAK_WARNING })
    }

    /**
     * Use case: a site on another theme. The recommendations come from Material, so a site not using it must
     * not hear about them.
     */
    fun `test ignores a site that is not on the Material theme`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: readthedocs
            """.trimIndent()
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: the fix of a recommendation writes the same entry the required variant does, including the
     * options the extension needs.
     */
    fun `test the fix adds the extension with its options`() {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - admonition
            """.trimIndent() + "\n"
        )
        // The warnings hang on the list of extensions, and an intention is only offered where the caret is.
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("markdown_extensions") + 1)
        myFixture.doHighlighting()

        myFixture.launchAction(myFixture.findSingleIntention("Add Markdown extension 'toc'"))

        val text = myFixture.file.text
        assertTrue(text.contains("- toc:"))
        assertTrue(text.contains("permalink: true"))
    }

    /**
     * Returns the warnings of the inspection for [text].
     *
     * @param text the content of the configuration file
     */
    private fun warningsOf(text: String): List<HighlightInfo> {
        myFixture.configureByText("mkdocs.yml", text + "\n")
        return myFixture.doHighlighting().filter { it.description?.contains("builds on the Markdown") == true }
    }
}
