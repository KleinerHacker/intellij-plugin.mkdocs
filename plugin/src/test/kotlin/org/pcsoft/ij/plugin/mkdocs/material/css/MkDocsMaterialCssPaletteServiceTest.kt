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

package org.pcsoft.ij.plugin.mkdocs.material.css

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Lives in the plugin project rather than in the facet: reading a style sheet needs the CSS language to be
 * registered, and a module project ships no plugin descriptor that would register one.
 *
 * Covers what the style sheets behind `extra_css` are read as. The whole point of reading them is the scope of
 * a definition — `:root` counts for every palette of the site, a rule below `[data-md-color-scheme="…"]` for
 * exactly one — so every case is driven through the scope it produces.
 */
class MkDocsMaterialCssPaletteServiceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and pip must not answer for the
        // machine the build runs on: a developer with mkdocs-material installed would otherwise get the
        // grounds out of a real package and a different answer than the build server.
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
     * Use case: the grounds of the theme once its package is there. They are read out of the style sheet it
     * ships, minified as that file is, and the set of grounds is complete from then on — which is what lets a
     * name none of them carries be marked.
     */
    fun `test reads the grounds of the theme out of the installed style sheet`() {
        val styleSheet = MkDocsMaterialInstalledTheme.installStyleSheets(project)
        val config = site("""[data-md-color-scheme="sepia"] { color: #333333; }""")

        val builtIn = schemesOf(config).filter { it.builtIn }

        assertEquals(MkDocsMaterialInstalledTheme.SCHEMES, builtIn.map { it.name })
        assertEquals(styleSheet, builtIn.first().file)
        assertNotNull("a ground of the theme must lead to the style sheet shipping it", builtIn.first().target)
    }

    /**
     * Use case: the same without an installation. The theme's two grounds are named out of the model instead,
     * so a site standing on one of them is answered for whether the IDE has found the package or not — only
     * without a file to lead to.
     */
    fun `test names the grounds of the theme without an installation`() {
        val config = site("""[data-md-color-scheme="sepia"] { color: #333333; }""")

        val builtIn = schemesOf(config).filter { it.builtIn }

        assertEquals(MkDocsMaterialScheme.entries.map { it.id }, builtIn.map { it.name })
        assertNull(builtIn.first().file)
        assertNull(builtIn.first().target)
    }

    /**
     * Use case: the shape the documentation of the theme writes a custom colour in. A definition below
     * `:root` paints every palette of the site, whatever ground it stands on.
     */
    fun `test reads a definition below root as global`() {
        val config = site(":root { --md-primary-fg-color: #EE0F0F; }")

        val definitions = definitionsOf(config)

        assertSize(1, definitions)
        assertEquals("--md-primary-fg-color", definitions.first().variable)
        assertEquals(MkDocsMaterialCssScope.Global, definitions.first().scope)
    }

    /**
     * Use case: a colour that belongs to one ground only, which is how a site paints the two palettes of a
     * colour scheme toggle differently. The identifier of the attribute is what the scope is made of.
     */
    fun `test reads a definition below a scheme selector as scoped`() {
        val config = site("""[data-md-color-scheme="slate"] { --md-primary-fg-color: #101010; }""")

        val definitions = definitionsOf(config)

        assertSize(1, definitions)
        assertEquals(MkDocsMaterialCssScope.Scheme("slate"), definitions.first().scope)
    }

    /**
     * Use case: a selector naming both, which is what a style sheet written narrowly looks like. It paints
     * that one ground and not the whole site, so the scheme has to win.
     */
    fun `test reads a scheme on the root element as scoped`() {
        val config = site(""":root[data-md-color-scheme="slate"] { --md-primary-fg-color: #101010; }""")

        assertEquals(MkDocsMaterialCssScope.Scheme("slate"), definitionsOf(config).first().scope)
    }

    /**
     * Use case: a definition wrapped in an `@media` block, which is how a site paints for print or for a
     * screen size. The media query says *when* the rule applies, not *which palette* it applies to, so the
     * scope is the one of the selector as everywhere else.
     */
    fun `test keeps the scope of a definition inside a media block`() {
        val config = site("@media print { :root { --md-primary-fg-color: #000000; } }")

        val definitions = definitionsOf(config)

        assertSize(1, definitions)
        assertEquals(MkDocsMaterialCssScope.Global, definitions.first().scope)
    }

    /**
     * Use case: a style sheet where the author styles the pages of the site as well. A rule below a selector
     * of their own says nothing about the palette and must be skipped whole.
     */
    fun `test ignores a rule below a selector of its own`() {
        val config = site(".md-header { --md-primary-fg-color: #EE0F0F; }")

        assertEmpty(definitionsOf(config))
    }

    /**
     * Use case: a custom property of the author, sitting below `:root` next to the ones of the theme. Only
     * the names the theme reads are of any interest here.
     */
    fun `test ignores a custom property that is not the theme's`() {
        val config = site(":root { --brand-color: #EE0F0F; --md-accent-fg-color: #00BCD4; }")

        val definitions = definitionsOf(config)

        assertSize(1, definitions)
        assertEquals("--md-accent-fg-color", definitions.first().variable)
    }

    /**
     * Use case: the grounds the style sheet paints, which is what completion and the reference offer. Every
     * selector naming one counts, and the definitions below it are beside the point.
     */
    fun `test reads every scheme the style sheet paints`() {
        val config = site(
            """
            [data-md-color-scheme="ochre"] { --md-primary-fg-color: #EE0F0F; }
            [data-md-color-scheme="sepia"] { color: #333333; }
            """.trimIndent()
        )

        assertEquals(listOf("ochre", "sepia"), schemesOf(config).filterNot { it.builtIn }.map { it.name })
    }

    /**
     * Use case: a site whose style sheets paint no ground of their own. `default` and `slate` are grounds all
     * the same — the style sheet of the installed theme paints them — so they have to be named, whether the
     * package could be read or not.
     */
    fun `test names the grounds of the theme next to the ones of the site`() {
        val config = site("""[data-md-color-scheme="sepia"] { color: #333333; }""")

        val names = schemesOf(config).map { it.name }

        assertContainsElements(names, MkDocsMaterialScheme.entries.map { it.id })
        assertContainsElements(names, listOf("sepia"))
        assertTrue(schemesOf(config).filter { it.builtIn }.none { it.name == "sepia" })
    }

    /**
     * Use case: a ground the site repaints under a name the theme already uses. It is one ground, named once,
     * and it belongs to the site — that is the file worth navigating to.
     */
    fun `test reports a ground the site repaints as its own`() {
        val config = site("""[data-md-color-scheme="slate"] { --md-primary-fg-color: #101010; }""")

        val slate = schemesOf(config).filter { it.name == "slate" }

        assertSize(1, slate)
        assertFalse(slate.first().builtIn)
        assertEquals("extra.css", slate.first().file?.name)
    }

    /**
     * Use case: a site whose `extra_css` names a file that is not there — a path being typed, or one left
     * behind by a deleted file. Nothing can be read from it, and nothing may be invented either.
     */
    fun `test reads nothing while the named style sheet is missing`() {
        val config = myFixture.addFileToProject(
            "gone/mkdocs.yml",
            "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n",
        ).virtualFile

        assertEmpty(definitionsOf(config))
        assertEmpty(schemesOf(config).filterNot { it.builtIn })
    }

    /**
     * Use case: a style sheet lying in the project but not named by `extra_css`. MkDocs loads only what the
     * key names, so a file next to it paints nothing at all.
     */
    fun `test reads nothing from a style sheet the site does not name`() {
        myFixture.addFileToProject("unnamed/docs/stylesheets/loose.css", ":root { --md-primary-fg-color: #EE0F0F; }")
        val config = myFixture.addFileToProject("unnamed/mkdocs.yml", "site_name: Handbook\n").virtualFile

        assertEmpty(definitionsOf(config))
    }

    /**
     * Use case: asking which definitions paint one palette. That is the question every reader of this service
     * actually has, and it is answered by the scope: the global one counts always, the scoped one only for
     * the ground it names.
     */
    fun `test answers which definitions paint a given scheme`() {
        val config = site(
            """
            :root { --md-accent-fg-color: #00BCD4; }
            [data-md-color-scheme="slate"] { --md-primary-fg-color: #101010; }
            """.trimIndent()
        )

        assertSize(1, definitionsFor(config, "--md-primary-fg-color", "slate"))
        assertEmpty(definitionsFor(config, "--md-primary-fg-color", "default"))
        assertSize(1, definitionsFor(config, "--md-accent-fg-color", "default"))
        assertSize(1, definitionsFor(config, "--md-accent-fg-color", "slate"))
    }

    /**
     * Use case: the author comments the entry out of `extra_css`. MkDocs stops loading that style sheet the
     * moment the key stops naming it, so everything read out of it has to be forgotten — the answer is cached
     * against the PSI, and a cache that outlived the edit would keep answering for a file the site no longer
     * loads.
     */
    fun `test forgets a style sheet the site stops naming`() {
        val config = site("""[data-md-color-scheme="sepia"] { color: #333333; }""")
        assertTrue("the ground must be there while the key names the file", schemesOf(config).any { it.name == "sepia" })

        rewrite(config, "site_name: Handbook\n#extra_css:\n#  - stylesheets/extra.css\n")

        assertFalse("the ground must be gone once the key stops naming it", schemesOf(config).any { it.name == "sepia" })
    }

    /**
     * Use case: the author comments the rule out of the style sheet itself. The file is still loaded, but it
     * paints nothing any more, and the answer has to follow the edit.
     */
    fun `test forgets a ground the style sheet stops painting`() {
        val config = site("""[data-md-color-scheme="sepia"] { color: #333333; }""")
        assertTrue("the ground must be there while the rule is written", schemesOf(config).any { it.name == "sepia" })

        val styleSheet = runReadActionBlocking { MkDocsConfig.resolveExtraCss(project, config) }.single()
        rewrite(styleSheet, """/* [data-md-color-scheme="sepia"] { color: #333333; } */""")

        assertFalse("the ground must be gone once the rule is commented out", schemesOf(config).any { it.name == "sepia" })
    }

    /**
     * Replaces the content of [file] the way an edit in the editor does.
     *
     * Through the document rather than the file system: what the caches hang on is the PSI, and only a change
     * that goes through it is the change a typing author makes.
     *
     * @param file the file being rewritten
     * @param text the new content
     */
    private fun rewrite(file: VirtualFile, text: String) {
        val documents = PsiDocumentManager.getInstance(project)
        val psi = runReadActionBlocking { PsiManager.getInstance(project).findFile(file) }
            ?: error("${file.name} has no PSI")
        WriteCommandAction.runWriteCommandAction(project) {
            val document = documents.getDocument(psi) ?: error("${file.name} has no document")
            document.setText(text)
            documents.commitDocument(document)
        }
    }

    /**
     * Writes a site whose single style sheet holds [css] and returns its configuration file.
     *
     * @param css the content of the style sheet behind `extra_css`
     */
    private fun site(css: String): VirtualFile {
        myFixture.addFileToProject("site/docs/stylesheets/extra.css", css)
        return myFixture.addFileToProject(
            "site/mkdocs.yml",
            "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n",
        ).virtualFile
    }

    private fun definitionsOf(config: VirtualFile): List<MkDocsMaterialCssDefinition> =
        runReadActionBlocking { project.service<MkDocsMaterialCssPaletteService>().definitions(config) }

    private fun schemesOf(config: VirtualFile): List<MkDocsMaterialCssScheme> =
        runReadActionBlocking { project.service<MkDocsMaterialCssPaletteService>().schemes(config) }

    private fun definitionsFor(
        config: VirtualFile,
        variable: String,
        scheme: String,
    ): List<MkDocsMaterialCssDefinition> =
        runReadActionBlocking { project.service<MkDocsMaterialCssPaletteService>().definitionsFor(config, variable, scheme) }
}
