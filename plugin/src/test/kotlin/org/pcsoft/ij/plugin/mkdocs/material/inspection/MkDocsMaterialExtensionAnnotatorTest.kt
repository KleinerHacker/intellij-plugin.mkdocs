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

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the banner reporting the Markdown extensions a Material configuration forces but does not list:
 * which files get one, when it stays away, and what its fix writes into the file.
 */
class MkDocsMaterialExtensionAnnotatorTest : BasePlatformTestCase() {

    /**
     * Use case: a site that ticked `content.code.annotate` on the features page. That flag renders nothing
     * without `pymdownx.superfences`, so exactly that one is reported — and nothing else: `attr_list` and
     * `md_in_html` are a choice of the author, not something the flag breaks without.
     */
    fun `test reports every extension a feature forces`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.code.annotate
            """.trimIndent()
        )

        assertEquals(1, banners.size)
        assertTrue(banners.any { it.description.contains("pymdownx.superfences") })
    }

    /**
     * Use case: the same site after the extension was added. Nothing forces anything that is missing, so
     * the banner has to disappear entirely rather than keep reporting what is already there.
     */
    fun `test stays quiet once the extensions are listed`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.code.annotate
            markdown_extensions:
              - pymdownx.superfences
            """.trimIndent()
        )

        assertTrue(banners.isEmpty())
    }

    /**
     * Use case: a Material site that ticked no feature at all — the state the wizard leaves behind. The theme
     * renders a plain site without a single extension, so nothing is forced and nothing may be reported as an
     * error.
     */
    fun `test stays quiet on a site without features`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: material
            """.trimIndent()
        )

        assertTrue(banners.isEmpty())
    }

    /**
     * Use case: a site on another theme whose configuration happens to name a flag of the same spelling.
     * Nothing here is a Material feature, so none of these checks apply.
     */
    fun `test ignores a site that is not on the Material theme`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: readthedocs
              features:
                - content.code.annotate
            """.trimIndent()
        )

        assertTrue(banners.isEmpty())
    }

    /**
     * Use case: a YAML file that is not an MkDocs configuration file at all. Its name decides, and a file
     * called something else must never be annotated no matter what it contains.
     */
    fun `test ignores a YAML file that is not a configuration file`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.code.annotate
            """.trimIndent(),
            name = "other.yml",
        )

        assertTrue(banners.isEmpty())
    }

    /**
     * Use case: the banner reports a broken site, not a matter of taste — a feature without its extension
     * renders nothing — so it has to carry the severity that says so.
     */
    fun `test the banner is an error covering the whole file`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.footnote.tooltips
        """.trimIndent()
        val file = myFixture.configureByText("mkdocs.yml", text)
        val banners = myFixture.doHighlighting().filter { it.description?.contains("footnotes") == true }

        assertTrue(banners.isNotEmpty())
        assertTrue(banners.all { it.severity == HighlightSeverity.ERROR })
        assertTrue(banners.all { it.startOffset == 0 && it.endOffset == file.textLength })
    }

    /**
     * Use case: the fix of an extension without options. `footnotes` is configured by its presence alone, so
     * a plain sequence entry is everything that may be written.
     */
    fun `test the fix adds a plain extension`() {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.footnote.tooltips
            """.trimIndent() + "\n"
        )

        myFixture.launchAction(myFixture.findSingleIntention("Add Markdown extension 'footnotes'"))

        assertTrue(myFixture.file.text.contains("markdown_extensions:"))
        assertTrue(myFixture.file.text.contains("- footnotes"))
    }

    /**
     * Use case: the fix of an extension that needs options. `pymdownx.tabbed` without `alternate_style`
     * renders the old tab style, so adding the identifier alone would leave the author with a site that
     * looks broken — the options belong into the same change.
     */
    fun `test the fix adds the default options of an extension`() {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - content.tabs.link
            """.trimIndent() + "\n"
        )

        myFixture.launchAction(myFixture.findSingleIntention("Add Markdown extension 'pymdownx.tabbed'"))

        val text = myFixture.file.text
        assertTrue(text.contains("- pymdownx.tabbed:"))
        assertTrue(text.contains("alternate_style: true"))
    }

    /**
     * Returns the file level messages of the annotator for [text].
     *
     * @param text the content of the configuration file
     * @param name the file name to write it under
     */
    private fun bannersOf(text: String, name: String = "mkdocs.yml"): List<HighlightInfo> {
        myFixture.configureByText(name, text + "\n")
        return myFixture.doHighlighting().filter { it.description?.contains("is required by") == true }
    }
}
