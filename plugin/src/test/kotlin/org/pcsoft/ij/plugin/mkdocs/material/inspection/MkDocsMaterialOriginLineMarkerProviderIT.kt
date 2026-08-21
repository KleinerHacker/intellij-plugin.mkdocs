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

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the mark the gutter carries next to everything in `mkdocs.yml` that comes from
 * *Material for MkDocs*: which keys and values get it, which must not, and that a line can carry more than one
 * of them.
 *
 * Lives in the plugin project rather than in the facet: whether a marker reaches the gutter is decided by the
 * platform running the registered provider, and a module project registers nothing in its test run.
 *
 * The expected identifiers are read from the theme description, never written out. A feature flag or a
 * Markdown extension added to `material/spec` then has to arrive in the gutter without this test being
 * touched.
 */
class MkDocsMaterialOriginLineMarkerProviderIT : BasePlatformTestCase() {

    /** The theme description the marked identifiers are taken from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /**
     * Use case: a Material site configuring the palette. `theme.palette` is a key of the theme, and the keys
     * below it exist for the same reason — every line of the block is marked, so that none of them looks like
     * a setting that survives a change of theme.
     */
    fun `test marks a key of the theme and every key below it`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
                scheme: slate
            """.trimIndent()
        )

        assertTrue("palette was not marked", "palette" in marked)
        assertTrue("primary was not marked", "primary" in marked)
        assertTrue("scheme was not marked", "scheme" in marked)
    }

    /**
     * Use case: the same site, looked at for what must stay unmarked. `site_name` and `docs_dir` are read by
     * MkDocs, `theme.name` names the theme itself and `theme.logo` is part of the theme contract of MkDocs —
     * marking any of them would claim the theme owns what it merely uses.
     */
    fun `test leaves the keys of MkDocs alone`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            docs_dir: docs
            theme:
              name: material
              logo: assets/logo.png
            """.trimIndent()
        )

        assertFalse("site_name was marked", "site_name" in marked)
        assertFalse("docs_dir was marked", "docs_dir" in marked)
        assertFalse("theme.name was marked", "name" in marked)
        assertFalse("theme.logo was marked", "logo" in marked)
    }

    /**
     * Use case: a value below `theme.palette.primary`. `indigo` is a colour and nothing else; that the setting
     * belongs to the theme is said by the key above it, and saying it a second time on the same line states
     * nothing new.
     */
    fun `test leaves a value alone whose key already carries the mark`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """.trimIndent()
        )

        assertTrue("primary was not marked", "primary" in marked)
        assertFalse("the colour was marked as well", "indigo" in marked)
    }

    /**
     * Use case: a feature flag below `theme.features`. The entry is a value of a sequence and carries the
     * theme in itself, so it is marked next to the key of the sequence.
     */
    fun `test marks a feature flag of the theme`() {
        val flag = data.featureFlags.all.first().id
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - $flag
            """.trimIndent()
        )

        assertTrue("features was not marked", "features" in marked)
        assertTrue("$flag was not marked", flag in marked)
    }

    /**
     * Use case: a Markdown extension of the theme below `markdown_extensions`. The key is a top level key of
     * MkDocs and stays plain, while the value it carries is the theme's — the case the marker exists for, and
     * the one an inlay in front of the key could not have stated.
     */
    fun `test marks an extension of the theme below a key of MkDocs`() {
        val extension = data.extensions.all.first().id
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
            markdown_extensions:
              - $extension
            """.trimIndent()
        )

        assertFalse("markdown_extensions was marked", "markdown_extensions" in marked)
        assertTrue("$extension was not marked", extension in marked)
    }

    /**
     * Use case: a key below `extra` the theme reads. `extra` itself is a key of MkDocs and stays plain; the key
     * below it and everything below that belongs to the theme.
     */
    fun `test marks a key of the theme below extra`() {
        val extraKey = data.extraKeys.all.first().name
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              $extraKey:
                - icon: material/check
            """.trimIndent()
        )

        assertFalse("extra was marked", "extra" in marked)
        assertTrue("$extraKey was not marked", extraKey in marked)
        assertTrue("the key below it was not marked", "icon" in marked)
    }

    /**
     * Use case: a palette written as a flow mapping, which puts three keys of the theme on one line. Each of
     * them gets a mark of its own, and the gutter holds all three next to each other — the markers are plain
     * ones on purpose, so that nothing merges them into a single icon.
     */
    fun `test marks every key of a line written as a flow mapping`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
              palette: {primary: indigo, scheme: slate}
            """.trimIndent()
        )

        assertEquals(listOf("palette", "primary", "scheme"), marked.sorted())
    }

    /**
     * Use case: the very same file on a site that is not on the Material theme. Without the theme none of these
     * keys is the theme's, and a mark there would state something untrue.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: readthedocs
              palette:
                primary: indigo
            """.trimIndent()
        )

        assertTrue("marks were drawn on a site of another theme: $marked", marked.isEmpty())
    }

    /**
     * Use case: a YAML file that is not an MkDocs configuration file, holding the very content that gets the
     * mark under the name of one. Its name decides, exactly as everywhere else in the plugin.
     */
    fun `test stays away from a YAML file that is not a configuration file`() {
        val marked = markedTextsIn(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: indigo
            """.trimIndent(),
            name = "other.yml",
        )

        assertTrue("marks were drawn outside a configuration file: $marked", marked.isEmpty())
    }

    /**
     * Use case: the text the mark carries. The gutter shows the same icon on a key and on a value, so the
     * tooltip is what tells the two apart.
     */
    fun `test tells a key and a value apart in the tooltip`() {
        val flag = data.featureFlags.all.first().id
        configure(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - $flag
            """.trimIndent()
        )

        val tooltips = markers().associate { (it.lineMarkerInfo.element?.text ?: "") to it.tooltipText }
        assertTrue("the key names nothing", tooltips["features"]?.contains("features") == true)
        assertTrue("the value names nothing", tooltips[flag]?.contains(flag) == true)
        assertNotSame(tooltips["features"], tooltips[flag])
    }

    /**
     * Returns the texts of the elements the mark of the theme was drawn next to in a file holding [text].
     *
     * @param text the content of the file
     * @param name the file name to write it under
     */
    private fun markedTextsIn(text: String, name: String = "mkdocs.yml"): List<String> {
        configure(text, name)
        return markers().mapNotNull { it.lineMarkerInfo.element?.text }
    }

    /**
     * Returns the marks of the theme the gutter of the file configured last carries.
     */
    private fun markers(): List<LineMarkerInfo.LineMarkerGutterIconRenderer<*>> =
        myFixture.findAllGutters()
            .filterIsInstance<LineMarkerInfo.LineMarkerGutterIconRenderer<*>>()
            .filter { it.icon === MkDocsMaterialIcons.Badge }

    /**
     * Opens a file holding [text] in the fixture.
     *
     * @param text the content of the file
     * @param name the file name to write it under
     */
    private fun configure(text: String, name: String = "mkdocs.yml") {
        myFixture.configureByText(name, text + "\n")
    }
}
