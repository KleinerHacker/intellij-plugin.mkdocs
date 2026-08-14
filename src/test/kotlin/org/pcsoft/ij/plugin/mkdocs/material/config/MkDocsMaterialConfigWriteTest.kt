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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers [MkDocsMaterialConfig.write]. The point of every test here is not only that the changed value
 * arrives, but that nothing else moves: the model is a lossy view of the file, so a write touching more than
 * the keys that actually differ would destroy what the model never saw — the comments above all.
 */
class MkDocsMaterialConfigWriteTest : BasePlatformTestCase() {

    /**
     * Use case: the user changes nothing but the interface language and applies. Every other key, and every
     * comment in the file, has to come out exactly as it went in.
     */
    fun `test writing the language leaves everything else untouched`() {
        val file = configFile("language/mkdocs.yml", RICH)
        val from = read(file)

        write(file, from, from.copy(language = "de"))

        assertEquals(RICH.replace("language: en", "language: de"), text(file))
    }

    /**
     * Use case: the user ticks a feature flag and unticks another one. Only the two lines change; the flag in
     * between, and the comment behind it, stay.
     */
    fun `test adds and removes a feature flag`() {
        val file = configFile(
            "features/mkdocs.yml",
            "theme:\n" +
                "  name: material\n" +
                "  features:\n" +
                "    - navigation.tabs\n" +
                "    - navigation.top  # back to top\n",
        )
        val from = read(file)

        write(file, from, from.copy(features = setOf("navigation.top", "content.code.copy")))

        assertEquals(
            "theme:\n" +
                "  name: material\n" +
                "  features:\n" +
                "    - navigation.top  # back to top\n" +
                "    - content.code.copy\n",
            text(file),
        )
    }

    /**
     * Use case: the user asks for the light and dark switch on a site that had no palette at all. The whole
     * two entry sequence has to be written, including the toggles — a palette without them cannot be switched,
     * which is the one thing the user asked for.
     */
    fun `test writes the full palette when the toggle is switched on`() {
        val file = configFile("palette/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")
        val from = read(file)

        write(file, from, from.copy(paletteMode = MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE))

        assertEquals(
            "site_name: Handbook\n" +
                "theme:\n" +
                "  name: material\n" +
                "  palette:\n" +
                "    - media: '(prefers-color-scheme: light)'\n" +
                "      scheme: default\n" +
                "      toggle:\n" +
                "        icon: material/brightness-7\n" +
                "        name: Switch to dark mode\n" +
                "    - media: '(prefers-color-scheme: dark)'\n" +
                "      scheme: slate\n" +
                "      toggle:\n" +
                "        icon: material/brightness-4\n" +
                "        name: Switch to light mode\n",
            text(file),
        )
        assertEquals(
            MkDocsMaterialSettings.PaletteMode.LIGHT_DARK_TOGGLE,
            read(file).paletteMode,
        )
    }

    /**
     * Use case: the user switches the palette off again. The key goes, the rest of the theme stays.
     */
    fun `test removes the palette when it is switched off`() {
        val file = configFile(
            "off/mkdocs.yml",
            "theme:\n" +
                "  name: material\n" +
                "  palette:\n" +
                "    - media: '(prefers-color-scheme: light)'\n" +
                "      scheme: default\n" +
                "      toggle:\n" +
                "        icon: material/brightness-7\n" +
                "    - media: '(prefers-color-scheme: dark)'\n" +
                "      scheme: slate\n" +
                "      toggle:\n" +
                "        icon: material/brightness-4\n" +
                "  language: en\n",
        )
        val from = read(file)

        write(file, from, from.copy(paletteMode = MkDocsMaterialSettings.PaletteMode.NONE))

        assertEquals("theme:\n  name: material\n  language: en\n", text(file))
    }

    /**
     * Use case: the user empties the logo field. The key has to be removed — a site stating `logo: ''` is not
     * the same as a site stating no logo, it asks the theme for an image that is not there.
     */
    fun `test clearing an optional value removes its key`() {
        val file = configFile(
            "clear/mkdocs.yml",
            "theme:\n  name: material\n  logo: assets/logo.png\n  favicon: assets/favicon.png\n",
        )
        val from = read(file)

        write(file, from, from.copy(logo = ""))

        assertEquals("theme:\n  name: material\n  favicon: assets/favicon.png\n", text(file))
    }

    /**
     * Use case: the file carries a palette the model cannot represent. The page is read-only, and an apply
     * triggered by another page must not touch the palette — not even to write back what it believes it read.
     */
    fun `test never writes a palette it could not read`() {
        val file = configFile(
            "readonly/mkdocs.yml",
            "theme:\n" +
                "  name: material\n" +
                "  palette:\n" +
                "    - media: '(prefers-color-scheme)'\n" +
                "      toggle:\n" +
                "        icon: material/brightness-auto\n" +
                "    - media: '(prefers-color-scheme: light)'\n" +
                "      scheme: default\n" +
                "    - media: '(prefers-color-scheme: dark)'\n" +
                "      scheme: slate\n" +
                "  language: en\n",
        )
        val from = read(file)
        assertFalse(from.editable)

        write(file, from, from.copy(language = "de"))

        assertEquals(
            "theme:\n" +
                "  name: material\n" +
                "  palette:\n" +
                "    - media: '(prefers-color-scheme)'\n" +
                "      toggle:\n" +
                "        icon: material/brightness-auto\n" +
                "    - media: '(prefers-color-scheme: light)'\n" +
                "      scheme: default\n" +
                "    - media: '(prefers-color-scheme: dark)'\n" +
                "      scheme: slate\n" +
                "  language: de\n",
            text(file),
        )
    }

    /**
     * Use case: the user opens the settings page and applies without having changed anything — the case an
     * *OK* on an untouched dialog produces. Not a single character of the file may move.
     */
    fun `test a round trip without a change leaves the file alone`() {
        val file = configFile("roundtrip/mkdocs.yml", RICH)
        val from = read(file)

        write(file, from, read(file))

        assertEquals(RICH, text(file))
        assertEquals(from, read(file))
    }

    /**
     * Use case: a site behind a firewall switches the Google Fonts off. The mapping of families has nowhere to
     * live in `font: false`, so the whole key is replaced by the scalar.
     */
    fun `test disabling the fonts replaces the font mapping`() {
        val file = configFile(
            "fontoff/mkdocs.yml",
            "theme:\n  name: material\n  font:\n    text: Inter\n    code: Fira Code\n  language: en\n",
        )
        val from = read(file)

        write(file, from, from.copy(fontEnabled = false, fontText = null, fontCode = null))

        assertEquals("theme:\n  name: material\n  font: false\n  language: en\n", text(file))
        assertFalse(read(file).fontEnabled)
    }

    /**
     * Use case: the same site switches the fonts back on and picks a family. The scalar has to give way to the
     * mapping, otherwise the family would be written below a `false`.
     */
    fun `test enabling the fonts replaces the scalar`() {
        val file = configFile("fonton/mkdocs.yml", "theme:\n  name: material\n  font: false\n")
        val from = read(file)

        write(file, from, from.copy(fontEnabled = true, fontText = "Inter"))

        assertEquals("theme:\n  name: material\n  font:\n    text: Inter\n", text(file))
        val result = read(file)
        assertTrue(result.fontEnabled)
        assertEquals("Inter", result.fontText)
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun read(file: VirtualFile): MkDocsMaterialSettings =
        runReadActionBlocking { MkDocsMaterialConfig.read(project, file) }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }

    private fun write(file: VirtualFile, from: MkDocsMaterialSettings, to: MkDocsMaterialSettings) {
        WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
            MkDocsMaterialConfig.write(project, file, from, to)
        }
    }

    private companion object {

        /** A configuration file carrying comments and keys the Material model does not represent. */
        private const val RICH: String =
            "site_name: Handbook  # the title\n" +
                "\n" +
                "# the appearance of the site\n" +
                "theme:\n" +
                "  name: material\n" +
                "  language: en  # interface language\n" +
                "  features:\n" +
                "    - navigation.tabs\n" +
                "markdown_extensions:\n" +
                "  - admonition\n" +
                "  - toc:\n" +
                "      permalink: true\n"
    }
}
