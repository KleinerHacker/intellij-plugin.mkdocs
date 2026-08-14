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

package org.pcsoft.ij.plugin.mkdocs.material.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsMarkdownExtensionTest {

    /**
     * Use case: an extension is looked up by the identifier read from `markdown_extensions`; a duplicate
     * identifier would hide one of the two entries.
     */
    @Test
    fun `identifiers are unique`() {
        val ids = MkDocsMarkdownExtension.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Use case: the quick fix installing an extension tells the user which package to install, so every
     * `pymdownx.*` extension has to name the package it comes from, and the built in ones must not.
     */
    @Test
    fun `pymdown extensions name their package`() {
        MkDocsMarkdownExtension.entries.forEach { extension ->
            if (extension.id.startsWith("pymdownx.")) {
                assertEquals(extension.id, "pymdown-extensions", extension.pipPackage)
            } else {
                assertNull(extension.id, extension.pipPackage)
            }
        }
    }

    /**
     * Use case: QuickDoc offers the documentation of an extension as a link, so every entry needs a usable
     * address.
     */
    @Test
    fun `every extension carries a documentation address`() {
        MkDocsMarkdownExtension.entries.forEach { extension ->
            assertTrue(extension.id, extension.docUrl.startsWith("https://"))
        }
    }

    /**
     * Use case: the core rule of the model — the theme renders without any extension, so a site that enables
     * no feature and writes no icon must never be reported as missing one.
     */
    @Test
    fun `nothing is required unconditionally`() {
        assertTrue(MkDocsMarkdownExtension.requiredBy(emptySet(), false).isEmpty())
    }

    /**
     * Use case: code annotations only render with super fences plus the two extensions that let the theme
     * attach markup to the block, so enabling the feature forces exactly those three.
     */
    @Test
    fun `code annotations force super fences and its companions`() {
        val required = MkDocsMarkdownExtension.requiredBy(setOf("content.code.annotate"), false)
        assertEquals(
            setOf(
                MkDocsMarkdownExtension.PYMDOWNX_SUPERFENCES,
                MkDocsMarkdownExtension.ATTR_LIST,
                MkDocsMarkdownExtension.MD_IN_HTML
            ),
            required
        )
    }

    /**
     * Use case: the copy button works on the highlighted markup the highlight extension produces, so the
     * feature forces that one extension and nothing else.
     */
    @Test
    fun `code copy forces the highlight extension`() {
        assertEquals(
            setOf(MkDocsMarkdownExtension.PYMDOWNX_HIGHLIGHT),
            MkDocsMarkdownExtension.requiredBy(setOf("content.code.copy"), false)
        )
    }

    /**
     * Use case: linked content tabs need the tab markup itself and the super fences that carry it.
     */
    @Test
    fun `linked content tabs force the tab extensions`() {
        assertEquals(
            setOf(MkDocsMarkdownExtension.PYMDOWNX_TABBED, MkDocsMarkdownExtension.PYMDOWNX_SUPERFENCES),
            MkDocsMarkdownExtension.requiredBy(setOf("content.tabs.link"), false)
        )
    }

    /**
     * Use case: icon shorthands such as `:material-check:` are resolved by the emoji extension, so a site
     * using them forces it even when it enables no feature at all.
     */
    @Test
    fun `icons force the emoji extension`() {
        assertEquals(
            setOf(MkDocsMarkdownExtension.PYMDOWNX_EMOJI),
            MkDocsMarkdownExtension.requiredBy(emptySet(), true)
        )
    }

    /**
     * Use case: `theme.features` is written by hand and may hold a typo or a flag of a newer theme version.
     * An unknown entry must be skipped instead of failing the whole computation.
     */
    @Test
    fun `unknown flags are ignored`() {
        assertTrue(MkDocsMarkdownExtension.requiredBy(setOf("navigation.nonsense"), false).isEmpty())
    }

    /**
     * Use case: several features together force the union of their extensions, without duplicates.
     */
    @Test
    fun `requirements of several features are merged`() {
        val required = MkDocsMarkdownExtension.requiredBy(
            setOf("content.code.annotate", "content.tabs.link"), false
        )
        assertTrue(required.contains(MkDocsMarkdownExtension.PYMDOWNX_TABBED))
        assertTrue(required.contains(MkDocsMarkdownExtension.MD_IN_HTML))
        assertEquals(4, required.size)
    }

    /**
     * Use case: the inspection reporting recommendations must never report something a feature can force —
     * that case is already an error of the annotator.
     */
    @Test
    fun `recommended extensions are never forced by a feature`() {
        val forceable = MkDocsMaterialFeatureFlag.entries
            .flatMap { it.requiredExtensions }
            .mapNotNull { MkDocsMarkdownExtension.byId(it) }
            .toSet()
        MkDocsMarkdownExtension.recommended().forEach { extension ->
            assertFalse(extension.id, forceable.contains(extension))
        }
    }

    /**
     * Use case: reading `markdown_extensions` resolves each entry back to an extension; an entry the theme
     * does not care about stays unresolved.
     */
    @Test
    fun `byId round-trips and rejects unknown identifiers`() {
        MkDocsMarkdownExtension.entries.forEach { extension ->
            assertEquals(extension, MkDocsMarkdownExtension.byId(extension.id))
        }
        assertNull(MkDocsMarkdownExtension.byId("pymdownx.nonsense"))
    }
}
