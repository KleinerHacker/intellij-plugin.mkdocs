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

import org.junit.Assert.*
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The service is constructed directly rather than looked up with `service<…>()`: it only reads bundled
 * classpath resources, so a running application is not needed to exercise what these tests are about.
 */
class MkDocsMarkdownExtensionTest {

    private val data = MkDocsMaterialDataService()
    private val extensions = data.extensions

    /**
     * Use case: the resource is bundled with the plugin, so a missing or unreadable file would silently
     * disable every recommendation, the annotator and the quick fix at once.
     */
    @Test
    fun `the bundled resource is read`() {
        assertTrue(extensions.all.isNotEmpty())
        assertNotNull(extensions.byId("pymdownx.superfences"))
    }

    /**
     * Use case: an extension is looked up by the identifier read from `markdown_extensions`; a duplicate
     * identifier would hide one of the two entries.
     */
    @Test
    fun `identifiers are unique`() {
        val ids = extensions.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Use case: the quick fix installing an extension tells the user which package to install, so every
     * `pymdownx.*` extension has to name the package it comes from, and the built in ones must not.
     */
    @Test
    fun `pymdown extensions name their package`() {
        extensions.all.forEach { extension ->
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
        extensions.all.forEach { extension ->
            assertTrue(extension.id, extension.docUrl.startsWith("https://"))
        }
    }

    /**
     * Use case: the default options the quick fix writes come from the resource as key and value pairs; an
     * entry with a blank key would produce a configuration file the site cannot load.
     */
    @Test
    fun `default options carry a key and a value`() {
        extensions.all.forEach { extension ->
            extension.defaultOptions.forEach { (key, value) ->
                assertTrue(extension.id, key.isNotBlank())
                assertTrue(extension.id, value.isNotBlank())
            }
        }
        val toc = extensions.byId("toc")
        assertEquals(listOf("permalink" to "true"), toc?.defaultOptions)
    }

    /**
     * Use case: the core rule of the model — the theme renders without any extension, so a site that enables
     * no feature and writes no icon must never be reported as missing one.
     */
    @Test
    fun `nothing is required unconditionally`() {
        assertTrue(extensions.requiredBy(emptySet(), false).isEmpty())
    }

    /**
     * Use case: code annotations render inside a code block with super fences alone. `attr_list` and
     * `md_in_html` are what annotations outside of code blocks would additionally need, which is a choice made
     * on a page — a site ticking the feature without them is correct and must not be reported.
     */
    @Test
    fun `code annotations force super fences only`() {
        val required = extensions.requiredBy(setOf("content.code.annotate"), false)
        assertEquals(setOfExtensions("pymdownx.superfences"), required)
    }

    /**
     * Use case: `content.tooltips` restyles tooltips that are already on the page, so it forces no extension
     * of its own — a site enabling it without `attr_list` renders exactly as intended.
     */
    @Test
    fun `tooltips force no extension`() {
        assertTrue(extensions.requiredBy(setOf("content.tooltips"), false).isEmpty())
    }

    /**
     * Use case: the copy button works on the highlighted markup the highlight extension produces, so the
     * feature forces that one extension and nothing else.
     */
    @Test
    fun `code copy forces the highlight extension`() {
        assertEquals(
            setOfExtensions("pymdownx.highlight"),
            extensions.requiredBy(setOf("content.code.copy"), false)
        )
    }

    /**
     * Use case: linked content tabs need the tab markup itself and the super fences that carry it.
     */
    @Test
    fun `linked content tabs force the tab extensions`() {
        assertEquals(
            setOfExtensions("pymdownx.tabbed", "pymdownx.superfences"),
            extensions.requiredBy(setOf("content.tabs.link"), false)
        )
    }

    /**
     * Use case: icon shorthands such as `:material-check:` are resolved by the emoji extension, so a site
     * using them forces it even when it enables no feature at all.
     */
    @Test
    fun `icons force the emoji extension`() {
        assertEquals(
            setOfExtensions("pymdownx.emoji"),
            extensions.requiredBy(emptySet(), true)
        )
    }

    /**
     * Use case: exactly one extension answers the icon shorthands. Marking a second one would make every
     * site using an icon report two missing extensions instead of one.
     */
    @Test
    fun `exactly one extension serves the icon shorthands`() {
        assertEquals(1, extensions.all.count { it.iconShorthand })
    }

    /**
     * Use case: `theme.features` is written by hand and may hold a typo or a flag of a newer theme version.
     * An unknown entry must be skipped instead of failing the whole computation.
     */
    @Test
    fun `unknown flags are ignored`() {
        assertTrue(extensions.requiredBy(setOf("navigation.nonsense"), false).isEmpty())
    }

    /**
     * Use case: several features together force the union of their extensions, without duplicates.
     */
    @Test
    fun `requirements of several features are merged`() {
        val required = extensions.requiredBy(
            setOf("content.code.annotate", "content.tabs.link"), false
        )
        assertTrue(required.contains(extensions.byId("pymdownx.tabbed")))
        assertTrue(required.contains(extensions.byId("pymdownx.superfences")))
        assertEquals(2, required.size)
    }

    /**
     * Use case: the inspection reporting recommendations must never report something a feature can force —
     * that case is already an error of the annotator.
     */
    @Test
    fun `recommended extensions are never forced by a feature`() {
        val forceable = data.featureFlags.all
            .flatMap { it.requiredExtensions }
            .mapNotNull { extensions.byId(it) }
            .toSet()
        extensions.recommended().forEach { extension ->
            assertFalse(extension.id, forceable.contains(extension))
        }
    }

    /**
     * Use case: an extension a feature can force has to be marked as such, otherwise the annotator would
     * report it while the settings page still calls it a recommendation.
     */
    @Test
    fun `forced extensions are marked as required by a feature`() {
        data.featureFlags.all.flatMap { it.requiredExtensions }.forEach { id ->
            val extension = extensions.byId(id)
            assertNotNull(id, extension)
            assertEquals(id, MkDocsMarkdownExtensionLevel.REQUIRED_BY_FEATURE, extension?.level)
        }
    }

    /**
     * Use case: reading `markdown_extensions` resolves each entry back to an extension; an entry the theme
     * does not care about stays unresolved.
     */
    @Test
    fun `byId round-trips and rejects unknown identifiers`() {
        extensions.all.forEach { extension ->
            assertEquals(extension, extensions.byId(extension.id))
        }
        assertNull(extensions.byId("pymdownx.nonsense"))
    }

    /** The extensions written as [ids], for comparing against what [MkDocsMarkdownExtensions.requiredBy] returns. */
    private fun setOfExtensions(vararg ids: String): Set<MkDocsMarkdownExtension> =
        ids.mapNotNull { extensions.byId(it) }.toSet()
}
