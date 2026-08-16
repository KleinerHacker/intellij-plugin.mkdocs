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

package org.pcsoft.ij.plugin.mkdocs.material

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the decision which keys of a configuration file belong to *Material for MkDocs* and which to MkDocs
 * itself. Both the inlay hint and the completion mark ask here, so a wrong answer shows up twice.
 */
class MkDocsMaterialKeysTest : BasePlatformTestCase() {

    /**
     * Use case: the five keys below `theme` the theme alone reads. These are what the hint has to mark — a
     * site changing its theme loses every one of them.
     */
    fun `test recognises the keys of the theme below theme`() {
        val marked = markedKeysOf(
            """
            theme:
              features:
                - navigation.tabs
              palette:
                primary: indigo
              font:
                text: Roboto
              icon:
                repo: fontawesome/brands/github
              direction: ltr
            """.trimIndent()
        )

        assertEquals(setOf("features", "palette", "font", "icon", "direction"), marked)
    }

    /**
     * Use case: the keys around them that MkDocs reads itself. `theme.name` names the theme, the other three
     * are part of the theme contract of MkDocs and work with any theme — marking them would claim the theme
     * owns what it merely uses.
     */
    fun `test leaves the keys of MkDocs alone`() {
        val marked = markedKeysOf(
            """
            site_name: Handbook
            docs_dir: docs
            theme:
              name: material
              logo: assets/logo.png
              favicon: assets/favicon.png
              custom_dir: overrides
            markdown_extensions:
              - admonition
            """.trimIndent()
        )

        assertTrue(marked.isEmpty())
    }

    /**
     * Use case: `theme.language` — read by MkDocs and by the built in themes as well, so it is none of the
     * theme's own keys even though the theme uses it.
     */
    fun `test leaves the language of the theme alone`() {
        assertTrue(markedKeysOf("theme:\n  language: de").isEmpty())
    }

    /**
     * Use case: the keys below `extra` the theme reads, next to one it does not. `social` and `generator` are
     * the theme's; `version` belongs to Mike, which is a plugin of its own and not part of this theme.
     */
    fun `test recognises the extra keys of the theme only`() {
        val marked = markedKeysOf(
            """
            extra:
              generator: false
              social:
                - icon: fontawesome/brands/github
                  link: https://example.org
              version:
                provider: mike
            """.trimIndent()
        )

        assertTrue(marked.contains("generator"))
        assertTrue(marked.contains("social"))
        assertFalse(marked.contains("version"))
    }

    /**
     * Use case: a key spelled like one of the theme's, but sitting somewhere else entirely. The path decides,
     * not the name — `features` below a plugin is that plugin's business.
     */
    fun `test judges a key by its path and not by its name`() {
        val marked = markedKeysOf(
            """
            plugins:
              search:
                features:
                  - navigation.tabs
                palette: dark
            """.trimIndent()
        )

        assertTrue(marked.isEmpty())
    }

    /**
     * Use case: what lies below a marked key. The mark belongs to the key that would disappear with the
     * theme; putting it on every level below as well would cover half the file with icons.
     */
    fun `test marks the key of the theme but nothing below it`() {
        val marked = markedKeysOf(
            """
            extra:
              social:
                - icon: fontawesome/brands/github
                  link: https://example.org
            """.trimIndent()
        )

        assertEquals(setOf("social"), marked)
    }

    /**
     * Use case: the identifiers the completion falls back on when it cannot place an entry by position — a
     * feature flag and a Markdown extension of the theme are the theme's wherever they turn up, an ordinary
     * word is not.
     */
    fun `test recognises the identifiers only the theme knows`() {
        assertTrue(MkDocsMaterialKeys.isMaterialId("navigation.tabs"))
        assertTrue(MkDocsMaterialKeys.isMaterialId("pymdownx.superfences"))
        assertFalse(MkDocsMaterialKeys.isMaterialId("site_name"))
        assertFalse(MkDocsMaterialKeys.isMaterialId("blue"))
    }

    /**
     * Use case: a value being completed below `theme.features`. Everything offered at that position is the
     * theme's, whatever it is called, so the completion marks it without looking at the entry itself.
     */
    fun `test credits an entry offered below a key of the theme`() {
        assertTrue(isMaterialLookupAtCaret("theme:\n  features:\n    - <caret>", "navigation.tabs"))
        assertTrue(isMaterialLookupAtCaret("theme:\n  palette:\n    primary: <caret>", "indigo"))
    }

    /**
     * Use case: the name of a key being typed inside `theme`. The key that would land there is what decides —
     * `features` is the theme's, `logo` is read by MkDocs and works with any theme.
     */
    fun `test credits the name of a key of the theme being typed`() {
        assertTrue(isMaterialLookupAtCaret("theme:\n  <caret>", "features"))
        assertFalse(isMaterialLookupAtCaret("theme:\n  <caret>", "logo"))
    }

    /**
     * Use case: a value being completed below a key of MkDocs. Neither the position nor the entry belongs to
     * the theme, so the entry has to stay as the contributor behind it built it.
     */
    fun `test leaves an entry of a key of MkDocs alone`() {
        assertFalse(isMaterialLookupAtCaret("docs_dir: <caret>", "docs"))
    }

    /**
     * Use case: an identifier of the theme offered somewhere the position says nothing — the fallback. A
     * feature flag is the theme's wherever it turns up; an ordinary word is not.
     */
    fun `test falls back to the identifier when the position says nothing`() {
        assertTrue(isMaterialLookupAtCaret("plugins:\n  - <caret>", "navigation.tabs"))
        assertFalse(isMaterialLookupAtCaret("plugins:\n  - <caret>", "search"))
    }

    /**
     * Returns whether the entry [lookupString] at the caret of [text] is credited to the theme.
     *
     * @param text the content of a configuration file, with the caret marked
     * @param lookupString what the entry would insert
     */
    private fun isMaterialLookupAtCaret(text: String, lookupString: String): Boolean {
        // What the decision sees during completion is a file the platform has written its dummy identifier
        // into, which is what makes the position a key or a value in the first place. A file with nothing at
        // the caret parses differently and would answer a question nobody asks.
        val offset = text.indexOf(CARET)
        val file = myFixture.configureByText(
            "mkdocs.yml",
            text.replace(CARET, CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED) + "\n",
        )
        val position = file.findElementAt(offset) ?: error("no element at the caret")
        return MkDocsMaterialKeys.isMaterialLookup(position, lookupString)
    }

    private companion object {

        /** The marker naming the position in the sources of the tests. */
        const val CARET = "<caret>"
    }

    /**
     * Returns the names of the keys of [text] the theme is credited with.
     *
     * @param text the content of a configuration file
     */
    private fun markedKeysOf(text: String): Set<String> {
        val file = myFixture.configureByText("mkdocs.yml", text + "\n")
        val marked = mutableSetOf<String>()
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val keyValue = element as? YAMLKeyValue
                if (keyValue != null && MkDocsMaterialKeys.isMaterialKey(keyValue)) {
                    marked += keyValue.keyText.trim()
                }
                super.visitElement(element)
            }
        })
        return marked
    }
}
