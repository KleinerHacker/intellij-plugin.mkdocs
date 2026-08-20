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
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstallationFixture

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the drawing the editor puts in front of an icon name in `mkdocs.yml`: which files get it, which
 * values of such a file carry it, and where the drawing sits.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialIconInlayHintsProviderIT : BasePlatformTestCase() {

    private val provider = MkDocsMaterialIconInlayHintsProvider()

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same index. What one
        // test found would otherwise answer for the next. Where the theme lies is asked of pip, so it is
        // installed here rather than written into the project.
        MkDocsMaterialInstallationFixture.install(project, ICON_NAMES.map { "$it.svg" })
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstallationFixture.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: the icons of the theme itself. Every value naming one carries the drawing, and it sits in
     * front of the name rather than at the end of the line.
     */
    fun `test paints the drawing in front of every icon of the theme`() {
        val marked = markedValuesOf(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: $ICON_NESTED
                edit: $ICON_CHECK
            """.trimIndent()
        )

        assertEquals(setOf(ICON_NESTED, ICON_CHECK), marked)
    }

    /**
     * Use case: the icon of a social link and the icon of a rating of the feedback widget, the two places
     * below `extra` naming an icon.
     */
    fun `test paints the drawing below extra as well`() {
        val marked = markedValuesOf(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              social:
                - icon: $ICON_NESTED
                  link: https://example.org
              analytics:
                feedback:
                  ratings:
                    - icon: $ICON_CHECK
                      name: Helpful
            """.trimIndent()
        )

        assertEquals(setOf(ICON_NESTED, ICON_CHECK), marked)
    }

    /**
     * Use case: a value that is not an icon, and a name the installed theme does not offer. Neither may get a
     * drawing — the first names no icon at all, the second names one that does not resolve.
     */
    fun `test paints nothing for a value naming no installed icon`() {
        val marked = markedValuesOf(
            """
            site_name: Handbook
            theme:
              name: material
              logo: $ICON_CHECK
              icon:
                repo: material/does-not-exist
            """.trimIndent()
        )

        assertTrue(marked.isEmpty())
    }

    /**
     * Use case: a site on another theme. It has no icons of this theme, so the hint stays away from the file
     * entirely instead of judging its values one by one.
     */
    fun `test collects nothing for a site that is not on the Material theme`() {
        val file = myFixture.configureByText(
            "mkdocs.yml",
            "site_name: Handbook\ntheme:\n  name: readthedocs\n  icon:\n    repo: $ICON_CHECK\n"
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
            "site_name: Handbook\ntheme:\n  name: material\n  icon:\n    repo: $ICON_CHECK\n"
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
     * Returns the values the drawings of [text] sit in front of.
     *
     * The theme is installed by the set-up of the class.
     *
     * @param text the content of the configuration file
     */
    private fun markedValuesOf(text: String): Set<String> {
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
        // The drawing sits at the start of the value, so the text behind it says which name it belongs to.
        return sink.offsets
            .map { file.text.substring(it).substringBefore('\n').trim() }
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

        /** An icon of a nested set, which the configuration file names with every level in front of it. */
        const val ICON_NESTED = "fontawesome/brands/github"

        /** An icon of the flat `material` set. */
        const val ICON_CHECK = "material/check"

        /** The icons of the installed theme the tests are written against. */
        val ICON_NAMES = listOf(ICON_CHECK, ICON_NESTED)
    }
}
