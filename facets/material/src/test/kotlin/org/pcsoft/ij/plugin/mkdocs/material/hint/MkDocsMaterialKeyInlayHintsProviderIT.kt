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

package org.pcsoft.ij.plugin.mkdocs.material.hint

import com.intellij.codeInsight.hints.BlockConstraints
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLLanguage

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the icon the editor puts in front of the keys a configuration file owes to *Material for MkDocs*:
 * which files get it at all, and which keys of such a file carry it.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialKeyInlayHintsProviderIT : BasePlatformTestCase() {

    private val provider = MkDocsMaterialKeyInlayHintsProvider()

    /**
     * Use case: the configuration file of a Material site. Every key the theme alone reads carries the mark,
     * and it sits in front of the key rather than somewhere on the line.
     */
    fun `test marks every key of the theme`() {
        val text = """
            site_name: Handbook
            theme:
              name: material
              features:
                - navigation.tabs
              palette:
                primary: indigo
            extra:
              generator: false
        """.trimIndent()

        val marked = markedKeysOf(text)

        assertEquals(setOf("features:", "palette:", "generator:"), marked)
    }

    /**
     * Use case: the keys of MkDocs itself in the same file. Marking them would tell the author that a change
     * of theme takes them away, which is exactly wrong.
     */
    fun `test leaves the keys of MkDocs unmarked`() {
        val marked = markedKeysOf(
            """
            site_name: Handbook
            docs_dir: docs
            theme:
              name: material
              logo: assets/logo.png
              custom_dir: overrides
            markdown_extensions:
              - admonition
            """.trimIndent()
        )

        assertTrue(marked.isEmpty())
    }

    /**
     * Use case: a site on another theme. None of these keys belongs to Material there, so the hint has to
     * stay away from the file entirely instead of judging its keys one by one.
     */
    fun `test collects nothing for a site that is not on the Material theme`() {
        val file = myFixture.configureByText(
            "mkdocs.yml",
            "site_name: Handbook\ntheme:\n  name: readthedocs\n  features:\n    - navigation.tabs\n"
        )

        assertNull(collectorFor(file))
    }

    /**
     * Use case: a YAML file that is not an MkDocs configuration file. Its name decides, exactly as everywhere
     * else in the plugin.
     */
    fun `test collects nothing for a YAML file that is not a configuration file`() {
        val file = myFixture.configureByText(
            "other.yml",
            "site_name: Handbook\ntheme:\n  name: material\n  features:\n    - navigation.tabs\n"
        )

        assertNull(collectorFor(file))
    }

    /**
     * Use case: the languages the hint offers itself for. It reads YAML paths, so it has nothing to say about
     * any other language.
     */
    fun `test offers itself for YAML only`() {
        assertTrue(provider.isLanguageSupported(YAMLLanguage.INSTANCE))
        assertFalse(provider.isLanguageSupported(Language.ANY))
    }

    /**
     * Returns the text the marks of [text] sit in front of.
     *
     * @param text the content of the configuration file
     */
    private fun markedKeysOf(text: String): Set<String> {
        val file = myFixture.configureByText("mkdocs.yml", text + "\n")
        val sink = RecordingSink()
        val collector = collectorFor(file, sink)
            ?: error("no collector for a Material configuration file")
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                collector.collect(element, myFixture.editor, sink)
                super.visitElement(element)
            }
        })
        // The mark sits at the start of the key, so the text behind it says which key it belongs to.
        return sink.offsets
            .map { file.text.substring(it).substringBefore('\n').trim().substringBefore(' ') }
            .toSet()
    }

    /**
     * Returns the collector the provider offers for [file], or `null` if it offers none.
     *
     * @param file the file the hints would be painted in
     * @param sink where the collector would report its hints
     */
    private fun collectorFor(file: PsiFile, sink: InlayHintsSink = RecordingSink()) =
        provider.getCollectorFor(file, myFixture.editor, NoSettings(), sink)

    /**
     * An [InlayHintsSink] remembering where it was asked to paint, instead of painting.
     */
    private class RecordingSink : InlayHintsSink {

        /** The offsets of the marks, in the order they were reported. */
        val offsets: MutableList<Int> = mutableListOf()

        override fun addInlineElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            presentation: InlayPresentation,
            placeAtTheEndOfLine: Boolean,
        ) {
            offsets += offset
        }

        override fun addInlineElement(
            offset: Int,
            presentation: RootInlayPresentation<*>,
            constraints: HorizontalConstraints?,
        ) {
            offsets += offset
        }

        override fun addBlockElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            showAbove: Boolean,
            priority: Int,
            presentation: InlayPresentation,
        ) = Unit

        override fun addBlockElement(
            logicalLine: Int,
            showAbove: Boolean,
            presentation: RootInlayPresentation<*>,
            constraints: BlockConstraints?,
        ) = Unit
    }
}
