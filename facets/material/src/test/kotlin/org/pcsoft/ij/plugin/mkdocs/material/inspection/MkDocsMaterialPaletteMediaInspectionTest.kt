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
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the warning about a media query of a palette the theme is not built around: when it appears, when it
 * stays away, and that it offers nothing to act on.
 */
class MkDocsMaterialPaletteMediaInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MkDocsMaterialPaletteMediaInspection())
    }

    /**
     * Use case: the palette of a colour scheme toggle, written as the documentation of the theme writes it.
     * Both queries are the theme's own, so nothing is reported — this is what a correct file looks like.
     */
    fun `test says nothing about the queries of the theme`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "${MkDocsMaterialConfig.MEDIA_LIGHT}"
                  scheme: default
                - media: "${MkDocsMaterialConfig.MEDIA_DARK}"
                  scheme: slate
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: the three palette setup, whose first entry follows the system preference. That query carries
     * no appearance of its own and is easily taken for a mistake — it is none.
     */
    fun `test says nothing about the query of the system preference`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - media: "${MkDocsMaterialConfig.MEDIA_SYSTEM}"
                  toggle:
                    icon: material/brightness-auto
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: a query outside the three. It is legal CSS, but the toggle of the theme cannot act on it, so
     * the author is told — once, on the value.
     */
    fun `test reports a query the theme is not built around`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-contrast: more)"
                scheme: slate
            """
        )

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().description!!.contains("(prefers-contrast: more)"))
    }

    /**
     * Use case: reading the report. Nothing here is broken — the file builds and the site renders — so this is
     * a warning and not an error, and it can be switched off.
     */
    fun `test reports as a warning`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-contrast: more)"
            """
        )

        assertEquals(HighlightSeverity.WARNING, warnings.single().severity)
    }

    /**
     * Use case: the same report asked for a quick fix. Which of the three queries was meant is not something
     * the file says, so there is deliberately nothing to apply.
     */
    fun `test offers no quick fix`() {
        warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-contrast: more)"
            """
        )

        assertEmpty(myFixture.filterAvailableIntentions("Material"))
    }

    /**
     * Use case: white space inside the query. YAML keeps it and a browser does not, so a value written without
     * the space behind the colon is the same query — and must not be reported as another one.
     */
    fun `test accepts a query written without the space of the documentation`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-color-scheme:dark)"
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: the key written without a value. That is a file being typed, not a wrong query — reporting it
     * would put a warning under the caret of an author who has not finished the line.
     */
    fun `test says nothing about a key without a value`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: ""
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: a key of the same name somewhere else in the file. `media` below `extra` belongs to whoever
     * reads it, and the theme has no say over its value.
     */
    fun `test ignores a key of the same name elsewhere`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              media: "(prefers-contrast: more)"
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: a site on another theme. The three queries are what Material is built around, and a site not
     * using it must not be judged by them.
     */
    fun `test ignores a site that is not on the Material theme`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: readthedocs
              palette:
                media: "(prefers-contrast: more)"
            """
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very
     * content that gets reported under the name of one. Its name decides, as everywhere else in the plugin.
     */
    fun `test ignores a YAML file that is not a configuration file`() {
        val warnings = warningsOf(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                media: "(prefers-contrast: more)"
            """,
            name = "other.yml",
        )

        assertTrue(warnings.isEmpty())
    }

    /**
     * Returns the warnings of the inspection for [text].
     *
     * @param text the content of the configuration file, indented as source
     * @param name the file name to write the content under
     */
    private fun warningsOf(text: String, name: String = "mkdocs.yml"): List<HighlightInfo> {
        myFixture.configureByText(name, text.trimIndent() + "\n")
        return myFixture.doHighlighting().filter { it.description?.contains("media query") == true }
    }
}
