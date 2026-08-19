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

package org.pcsoft.ij.plugin.mkdocs.material.markdown

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
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the drawing the editor puts in front of an icon shorthand on a page: which files get it, which
 * shorthands carry it, and where the drawing sits.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialShorthandInlayHintsProviderIT : BasePlatformTestCase() {

    private val provider = MkDocsMaterialShorthandInlayHintsProvider()

    /** How many pages this test class has written, which keeps their names apart. */
    private var pages = 0

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same index. What one
        // test found would otherwise answer for the next.
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
    }

    /**
     * Use case: a page of a Material site naming an icon of a flat set and one of a nested set. Both carry
     * the drawing, and it sits in front of the shorthand rather than at the end of the line.
     */
    fun `test paints the drawing in front of every shorthand`() {
        val marked = markedShorthandsOf("The forecast is :material-check: and :fontawesome-brands-github: today.")

        assertEquals(setOf(":material-check:", ":fontawesome-brands-github:"), marked)
    }

    /**
     * Use case: a shorthand the installed theme offers no icon for, and an ordinary piece of text between
     * colons. Neither may get a drawing: nothing would resolve, and inventing one would claim the site
     * renders an icon there.
     */
    fun `test paints nothing for a shorthand naming no installed icon`() {
        val marked = markedShorthandsOf("Neither :material-does-not-exist: nor :whatever: is an icon.")

        assertTrue(marked.isEmpty())
    }

    /**
     * Use case: a file of a site that is not a page. Only the Markdown pages of the documentation carry
     * shorthands, and what a file is is decided by its name, exactly as everywhere else in the plugin.
     */
    fun `test collects nothing for a file that is not a page`() {
        site()
        val file = myFixture.configureByText("notes.txt", "Text with :material-check: in it.\n")

        assertNull(collectorFor(file))
    }

    /**
     * Use case: a page of a site on another theme, which renders no shorthand at all. The page carries a
     * configuration file of its own next to it, because the nearest one is the site a page belongs to.
     */
    fun `test collects nothing for a site that is not on the Material theme`() {
        myFixture.addFileToProject("plain/mkdocs.yml", "site_name: Plain\ntheme:\n  name: readthedocs\n")
        val page = myFixture.addFileToProject("plain/page.md", "Text with :material-check: in it.\n")
        myFixture.configureFromExistingVirtualFile(page.virtualFile)

        assertNull(collectorFor(page))
    }

    /**
     * Use case: the languages the hint offers itself for. It reads the shorthands of a page, so it has
     * nothing to say about the configuration file or any other language.
     */
    fun `test offers itself for Markdown only`() {
        assertFalse(provider.isLanguageSupported(YAMLLanguage.INSTANCE))
        assertFalse(provider.isLanguageSupported(Language.ANY))
    }

    /**
     * Returns the shorthands the drawings of [text] sit in front of.
     *
     * @param text the content of the page
     */
    private fun markedShorthandsOf(text: String): Set<String> {
        site()

        val file = myFixture.addFileToProject("docs/" + pageName(), text + "\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val sink = RecordingSink()
        val collector = collectorFor(file, sink) ?: error("no collector for a page of a Material site")
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                collector.collect(element, myFixture.editor, sink)
                super.visitElement(element)
            }
        })
        // The drawing sits at the start of the shorthand, so the text behind it says which one it belongs to.
        return sink.offsets
            .map { offset -> file.text.substring(offset) }
            .map { rest -> ":" + rest.drop(1).substringBefore(':') + ":" }
            .toSet()
    }

    /**
     * Returns a page name no other test of the class has used.
     *
     * The light fixture hands every test of a class the same project and keeps what was written into it, so a
     * page written under the same name twice would fail.
     */
    private fun pageName(): String = "page-" + pages++ + ".md"

    /**
     * Writes a site using the Material theme, with the icons of an installed theme next to it.
     *
     * Written once per fixture: the light fixture hands every test of a class the same project, and a file
     * that is already there must not be written a second time.
     */
    private fun site() {
        ICON_NAMES.forEach { icon ->
            if (myFixture.findFileInTempDir("$INSTALLED/$icon.svg") == null) {
                myFixture.addFileToProject("$INSTALLED/$icon.svg", SVG)
            }
        }
        if (myFixture.findFileInTempDir("mkdocs.yml") == null) {
            myFixture.addFileToProject("mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")
        }
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
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

        /** The offsets of the drawings, in the order they were reported. */
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

    private companion object {

        /** The path of the icon sets inside an installed package, below the site root. */
        const val INSTALLED = ".venv/Lib/site-packages/material/templates/.icons"

        /** The icons of the installed theme the tests are written against. */
        val ICON_NAMES = listOf("material/check", "fontawesome/brands/github")

        /** The drawing every installed icon is written with. */
        const val SVG =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>"""
    }
}
