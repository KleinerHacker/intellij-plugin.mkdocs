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

package org.pcsoft.ij.plugin.mkdocs.material.config

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers [MkDocsMaterialConfig.read]: every shape a Material configuration is written in has to arrive in the
 * snapshot the settings pages bind to, and every shape the snapshot cannot represent has to be recognised as
 * such instead of being approximated.
 */
class MkDocsMaterialConfigReadTest : BasePlatformTestCase() {

    /**
     * Use case: a site without any theme at all is opened in the Material pages. Nothing is configured, so the
     * snapshot has to be the empty one — and it has to be editable, otherwise the user could never start.
     */
    fun `test reads an absent theme as the empty settings`() {
        val settings = read(configFile("absent/mkdocs.yml", "site_name: Handbook\n"))

        assertEquals(MkDocsMaterialSettings.EMPTY, settings)
        assertTrue(settings.editable)
    }

    /**
     * Use case: the site writes its theme in the shorthand `theme: material`. There is no mapping carrying any
     * setting, so the snapshot is empty — the writer promotes the scalar as soon as the first key is set.
     */
    fun `test reads the scalar theme shorthand as the empty settings`() {
        val settings = read(configFile("shorthand/mkdocs.yml", "site_name: Handbook\ntheme: material\n"))

        assertEquals(MkDocsMaterialSettings.EMPTY, settings)
    }

    /**
     * Use case: the site carries one palette, written as a mapping. That is the shape the theme documents for
     * a site not offering a switch, and it maps onto [MkDocsMaterialSettings.PaletteMode.SINGLE].
     */
    fun `test reads a single mapping palette`() {
        val settings = read(
            configFile(
                "single/mkdocs.yml",
                "theme:\n" +
                    "  name: material\n" +
                    "  palette:\n" +
                    "    scheme: slate\n" +
                    "    primary: deep-purple\n" +
                    "    accent: lime\n",
            ),
        )

        assertEquals(MkDocsMaterialSettings.PaletteMode.SINGLE, settings.paletteMode)
        assertEquals(MkDocsMaterialScheme.SLATE, settings.light.scheme)
        assertEquals(MkDocsMaterialColor.DEEP_PURPLE, settings.light.primary)
        assertEquals(MkDocsMaterialColor.LIME, settings.light.accent)
        assertTrue(settings.editable)
    }

    /**
     * Use case: the site offers the light and dark switch the theme documents — two entries carrying `media`
     * and `toggle`. Both palettes and both toggles have to arrive, otherwise applying the page would rewrite
     * them from defaults.
     */
    fun `test reads the standard light and dark palette`() {
        val settings = read(
            configFile(
                "toggle/mkdocs.yml",
                "theme:\n" +
                    "  name: material\n" +
                    "  palette:\n" +
                    "    - media: '(prefers-color-scheme: light)'\n" +
                    "      scheme: default\n" +
                    "      primary: indigo\n" +
                    "      accent: pink\n" +
                    "      toggle:\n" +
                    "        icon: material/brightness-7\n" +
                    "        name: Switch to dark mode\n" +
                    "    - media: '(prefers-color-scheme: dark)'\n" +
                    "      scheme: slate\n" +
                    "      primary: blue\n" +
                    "      toggle:\n" +
                    "        icon: material/brightness-4\n" +
                    "        name: Switch to light mode\n",
            ),
        )

        assertEquals(MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE, settings.paletteMode)
        assertTrue(settings.editable)
        assertEquals(MkDocsMaterialScheme.DEFAULT, settings.light.scheme)
        assertEquals(MkDocsMaterialColor.INDIGO, settings.light.primary)
        assertEquals(MkDocsMaterialColor.PINK, settings.light.accent)
        assertEquals("material/brightness-7", settings.light.toggleIcon)
        assertEquals("Switch to dark mode", settings.light.toggleName)
        assertEquals(MkDocsMaterialScheme.SLATE, settings.dark.scheme)
        assertEquals(MkDocsMaterialColor.BLUE, settings.dark.primary)
        assertNull(settings.dark.accent)
        assertEquals("material/brightness-4", settings.dark.toggleIcon)
        assertEquals("Switch to light mode", settings.dark.toggleName)
    }

    /**
     * Use case: the site follows the setup with three palettes — system preference, light, dark. The model
     * knows two, so the page has to go read-only rather than throw the third palette away on the next apply.
     */
    fun `test refuses a palette of three entries`() {
        val settings = read(
            configFile(
                "three/mkdocs.yml",
                "theme:\n" +
                    "  name: material\n" +
                    "  palette:\n" +
                    "    - media: '(prefers-color-scheme)'\n" +
                    "      toggle:\n" +
                    "        icon: material/brightness-auto\n" +
                    "    - media: '(prefers-color-scheme: light)'\n" +
                    "      scheme: default\n" +
                    "    - media: '(prefers-color-scheme: dark)'\n" +
                    "      scheme: slate\n",
            ),
        )

        assertFalse(settings.editable)
        assertEquals(MkDocsMaterialSettings.PaletteMode.NONE, settings.paletteMode)
        assertEquals(MkDocsMaterialSettings.PaletteEntry(), settings.light)
    }

    /**
     * Use case: a site built behind a firewall switches the Google Fonts off with `font: false`. That is a
     * scalar where the model otherwise expects a mapping, and it has to read as "fonts disabled", not as
     * "no font configured".
     */
    fun `test reads the disabled font`() {
        val settings = read(configFile("nofont/mkdocs.yml", "theme:\n  name: material\n  font: false\n"))

        assertFalse(settings.fontEnabled)
        assertNull(settings.fontText)
        assertNull(settings.fontCode)
    }

    /**
     * Use case: the site picks its own families for body and code text.
     */
    fun `test reads the configured fonts`() {
        val settings = read(
            configFile(
                "font/mkdocs.yml",
                "theme:\n  name: material\n  font:\n    text: Inter\n    code: Fira Code\n",
            ),
        )

        assertTrue(settings.fontEnabled)
        assertEquals("Inter", settings.fontText)
        assertEquals("Fira Code", settings.fontCode)
    }

    /**
     * Use case: the site runs a newer theme than the plugin knows and lists a feature flag the plugin has
     * never heard of. The flag has to survive in the snapshot — dropping it here would remove it from the file
     * on the next apply.
     */
    fun `test keeps a feature flag it does not know`() {
        val settings = read(
            configFile(
                "features/mkdocs.yml",
                "theme:\n" +
                    "  name: material\n" +
                    "  features:\n" +
                    "    - navigation.tabs\n" +
                    "    - content.code.copy\n" +
                    "    - navigation.crystal.ball\n",
            ),
        )

        assertEquals(
            setOf("navigation.tabs", "content.code.copy", "navigation.crystal.ball"),
            settings.features,
        )
    }

    /**
     * Use case: the Markdown extensions of a real site are a mix — plain entries, and entries carrying their
     * options as a mapping. Both name an enabled extension, so both have to arrive in the snapshot.
     */
    fun `test reads markdown extensions in both shapes`() {
        val settings = read(
            configFile(
                "extensions/mkdocs.yml",
                "markdown_extensions:\n" +
                    "  - admonition\n" +
                    "  - pymdownx.highlight:\n" +
                    "      anchor_linenums: true\n" +
                    "  - toc:\n" +
                    "      permalink: true\n",
            ),
        )

        assertEquals(setOf("admonition", "pymdownx.highlight", "toc"), settings.extensions)
    }

    /**
     * Use case: the remaining single value keys of the theme are read into the assets page.
     */
    fun `test reads the asset keys of the theme`() {
        val settings = read(
            configFile(
                "assets/mkdocs.yml",
                "theme:\n" +
                    "  name: material\n" +
                    "  language: de\n" +
                    "  direction: rtl\n" +
                    "  logo: assets/logo.png\n" +
                    "  favicon: assets/favicon.png\n" +
                    "  custom_dir: overrides\n",
            ),
        )

        assertEquals("de", settings.language)
        assertEquals("rtl", settings.direction)
        assertEquals("assets/logo.png", settings.logo)
        assertEquals("assets/favicon.png", settings.favicon)
        assertEquals("overrides", settings.customDir)
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun read(file: VirtualFile): MkDocsMaterialSettings =
        runReadActionBlocking { MkDocsMaterialConfig.read(project, file) }
}
