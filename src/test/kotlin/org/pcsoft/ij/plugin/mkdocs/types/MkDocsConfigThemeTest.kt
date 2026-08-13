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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers reading and writing the `theme` key, the single piece of the configuration the Angular Material
 * facet is made of. Needs the platform because both directions go through the PSI of the bundled YAML plugin.
 */
class MkDocsConfigThemeTest : BasePlatformTestCase() {

    /**
     * Use case: the shape MkDocs documents and the Material theme itself requires — `theme` as a mapping
     * carrying `name`. This is what the detection has to recognise.
     */
    fun `test reads the theme name from a mapping`() {
        val file = configFile("mapping/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")

        assertEquals("material", readThemeName(file))
        assertTrue(isMaterialTheme(file))
    }

    /**
     * Use case: MkDocs also accepts `theme` written as a plain scalar. A site written that way is no less a
     * Material site, so the scalar has to be read as the theme name as well.
     */
    fun `test reads the theme name written as a scalar`() {
        val file = configFile("scalar/mkdocs.yml", "site_name: Handbook\ntheme: material\n")

        assertEquals("material", readThemeName(file))
        assertTrue(isMaterialTheme(file))
    }

    /**
     * Use case: a site on the built-in theme, or on any other one. Neither may end up carrying the Angular
     * Material facet.
     */
    fun `test reports another theme as not material`() {
        val other = configFile("other/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: readthedocs\n")
        val none = configFile("none/mkdocs.yml", "site_name: Handbook\n")

        assertEquals("readthedocs", readThemeName(other))
        assertFalse(isMaterialTheme(other))
        assertNull(readThemeName(none))
        assertFalse(isMaterialTheme(none))
    }

    /**
     * Use case: the theme name is compared against what MkDocs accepts, and MkDocs is not case sensitive
     * about it. A site declaring `Material` is a Material site.
     */
    fun `test recognises the theme regardless of case`() {
        val file = configFile("case/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: Material\n")

        assertTrue(isMaterialTheme(file))
    }

    /**
     * Use case: the user adds the facet to a site that has no theme yet. The key has to be appended to the
     * top level mapping without disturbing anything that was already in the file.
     */
    fun `test adds the theme to a file without one`() {
        val file = configFile("add/mkdocs.yml", "site_name: Handbook\n")

        setThemeName(file, MkDocsConfig.THEME_MATERIAL)

        assertTrue(isMaterialTheme(file))
        assertTrue("site_name must survive the write", text(file).contains("site_name: Handbook"))
    }

    /**
     * Use case: the site already configures the Material theme with options of its own. Switching the theme
     * must replace the name only — the options next to it are the user's work and have to stay.
     */
    fun `test replaces the name inside an existing theme mapping`() {
        val file = configFile(
            "replace/mkdocs.yml",
            "site_name: Handbook\ntheme:\n  name: readthedocs\n  highlightjs: true\n",
        )

        setThemeName(file, MkDocsConfig.THEME_MATERIAL)

        assertEquals("material", readThemeName(file))
        assertTrue("settings next to the name must survive", text(file).contains("highlightjs: true"))
    }

    /**
     * Use case: the theme is written as a scalar and the facet is added. A scalar has nowhere to keep the
     * name, so the key has to be turned into a mapping rather than being left as it is.
     */
    fun `test turns a scalar theme into a mapping`() {
        val file = configFile("scalar-write/mkdocs.yml", "site_name: Handbook\ntheme: readthedocs\n")

        setThemeName(file, MkDocsConfig.THEME_MATERIAL)

        assertEquals("material", readThemeName(file))
        assertTrue(isMaterialTheme(file))
    }

    /**
     * Use case: the user removes the facet in the Project Structure dialog. The whole `theme` key goes with
     * it, so MkDocs falls back to its built-in theme, while the rest of the configuration stays untouched.
     */
    fun `test removes the theme key`() {
        val file = configFile("remove/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\nsite_url: https://x.y\n")

        removeTheme(file)

        assertNull(readThemeName(file))
        assertFalse(isMaterialTheme(file))
        assertTrue("site_name must survive the removal", text(file).contains("site_name: Handbook"))
        assertTrue("site_url must survive the removal", text(file).contains("site_url: https://x.y"))
    }

    /**
     * Use case: the site configures the Material theme with settings of its own — a palette, a font, a list
     * of features. Removing the facet must take the theme name out and nothing else: those settings are the
     * user's work, and throwing them away because a checkbox was unticked would destroy far more than was
     * asked for.
     */
    fun `test keeps the settings next to the removed theme name`() {
        val file = configFile(
            "keep/mkdocs.yml",
            "site_name: Handbook\ntheme:\n  name: material\n  palette:\n    primary: indigo\n",
        )

        removeTheme(file)

        assertNull("the theme name must be gone", readThemeName(file))
        val content = text(file)
        assertTrue("the theme key itself must stay behind its settings", content.contains("theme:"))
        assertTrue("the palette must survive", content.contains("primary: indigo"))
    }

    /**
     * Use case: the facet is removed on a site that never declared a theme. There is nothing to take out, and
     * the file must come out of it byte for byte the same.
     */
    fun `test leaves a file without a theme untouched`() {
        val file = configFile("untouched/mkdocs.yml", "site_name: Handbook\n")

        removeTheme(file)

        assertEquals("site_name: Handbook\n", text(file))
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun readThemeName(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readThemeName(project, file) }

    private fun isMaterialTheme(file: VirtualFile): Boolean =
        runReadActionBlocking { MkDocsConfig.isMaterialTheme(project, file) }

    private fun setThemeName(file: VirtualFile, name: String) =
        WriteCommandAction.runWriteCommandAction(project) {
            MkDocsConfigWriter.setThemeName(project, file, name)
        }

    private fun removeTheme(file: VirtualFile) =
        WriteCommandAction.runWriteCommandAction(project) {
            MkDocsConfigWriter.removeTheme(project, file)
        }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }
}
