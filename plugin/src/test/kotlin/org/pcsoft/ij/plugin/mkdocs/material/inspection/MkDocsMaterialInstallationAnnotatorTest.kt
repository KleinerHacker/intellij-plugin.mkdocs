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
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the banner reporting that no installation of *Material for MkDocs* can be found: which files get
 * one, when it stays away, and that it is the error it claims to be. The state itself is invisible otherwise
 * — the icon completion is simply empty — which is why it is reported at all.
 */
class MkDocsMaterialInstallationAnnotatorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and pip must not answer for the
        // machine the build runs on: an installed mkdocs-material would take the banner away.
        MkDocsMaterialInstalledTheme.uninstall(project)
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstalledTheme.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a fresh checkout whose environment has not been created yet. Nothing the feature reads out of
     * the package is available, and the configuration file is the one place the site can be told about it.
     */
    fun `test reports a site without an installation`() {
        assertTrue(bannersOf(MATERIAL_SITE).isNotEmpty())
    }

    /**
     * Use case: the theme is installed and configured. There is nothing to report, and a banner staying up
     * next to a working installation would be the worse error of the two.
     */
    fun `test stays quiet once the theme is installed`() {
        MkDocsMaterialInstalledTheme.install(project, listOf("material/check.svg"))

        assertTrue(bannersOf(MATERIAL_SITE).isEmpty())
    }

    /**
     * Use case: a site on another theme. It reads nothing out of `mkdocs-material`, so whether that package
     * is installed is none of its business.
     */
    fun `test ignores a site that is not on the Material theme`() {
        val banners = bannersOf(
            """
            site_name: Handbook
            theme:
              name: readthedocs
            """.trimIndent()
        )

        assertTrue(banners.isEmpty())
    }

    /**
     * Use case: a YAML file that is not an MkDocs configuration file at all. Its name decides, and a file
     * called something else must never be annotated no matter what it contains.
     */
    fun `test ignores a YAML file that is not a configuration file`() {
        assertTrue(bannersOf(MATERIAL_SITE, name = "other.yml").isEmpty())
    }

    /**
     * Use case: the banner reports a site whose icons, hints and shorthands all stay empty, so it carries the
     * severity saying so and covers the whole file rather than a place inside it.
     */
    fun `test the banner is an error covering the whole file`() {
        myFixture.configureByText("mkdocs.yml", "$MATERIAL_SITE\n")
        val file = myFixture.file
        val banners = myFixture.doHighlighting().filter { it.description?.contains("cannot be found") == true }

        assertTrue(banners.isNotEmpty())
        assertTrue(banners.all { it.severity == HighlightSeverity.ERROR })
        assertTrue(banners.all { it.startOffset == 0 && it.endOffset == file.textLength })
    }

    /**
     * Use case: the way out of the banner. Nothing inside the file can be changed to fix this, so what is
     * offered is the page the installation is chosen on.
     */
    fun `test the banner offers the settings page`() {
        myFixture.configureByText("mkdocs.yml", "$MATERIAL_SITE\n")

        assertNotNull(myFixture.findSingleIntention("Choose the Material for MkDocs installation"))
    }

    /**
     * Returns the file level messages of the annotator for [text].
     *
     * @param text the content of the configuration file
     * @param name the file name to write it under
     */
    private fun bannersOf(text: String, name: String = "mkdocs.yml"): List<HighlightInfo> {
        myFixture.configureByText(name, "$text\n")
        return myFixture.doHighlighting().filter { it.description?.contains("cannot be found") == true }
    }

    private companion object {

        /** The smallest configuration file of a site rendered with the Material theme. */
        private val MATERIAL_SITE = """
            site_name: Handbook
            theme:
              name: material
        """.trimIndent()
    }
}
