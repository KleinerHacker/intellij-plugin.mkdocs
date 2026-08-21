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

import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the link from `theme.palette.scheme` to the rule painting that ground. Whether a reference exists at
 * an offset is decided by the platform walking the contributors registered for YAML, so it can only be driven
 * through a real file.
 */
class MkDocsMaterialSchemeReferenceIT : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and pip must not answer for the
        // machine the build runs on: a developer with mkdocs-material installed would otherwise get grounds
        // out of a real package and a different answer than the build server.
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
     * Use case: *Ctrl+Click* on a ground the style sheet of the site paints. The reference has to lead into
     * that style sheet, onto the selector naming it.
     */
    fun `test resolves the scheme to the selector painting it`() {
        val reference = referenceAt("slate")

        assertNotNull("the ground must carry a reference", reference)
        val target = reference!!.resolve()
        assertNotNull("the reference must resolve", target)
        assertEquals("extra.css", target!!.containingFile.name)
        assertTrue(target.text.contains("slate"))
    }

    /**
     * Use case: a ground no style sheet paints. The reference leads nowhere, and it stays soft whatever the
     * IDE knows: YAML draws nothing for an unresolved reference, so a hard one here would put no mark on the
     * value and only risk a second one next to what the annotator reports.
     */
    fun `test leaves a scheme no style sheet paints unresolved and soft`() {
        val reference = referenceAt("teal")

        assertNotNull("the ground must carry a reference", reference)
        assertNull(reference!!.resolve())
        assertTrue("the mark belongs to the annotator, not to this reference", reference.isSoft)
    }

    /**
     * Use case: a ground the installed theme paints itself, with the site adding nothing of its own. It has
     * to resolve into the style sheet the package ships, and must not be marked.
     */
    fun `test resolves a ground of the installed theme into its style sheet`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)
        myFixture.addFileToProject("docs/stylesheets/extra.css", ".md-header { color: red; }")
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: ${MkDocsMaterialInstalledTheme.SCHEMES.last()}
            """.trimIndent() + "\n",
        )
        val offset = myFixture.file.text.indexOf("scheme: ") + "scheme: ".length
        val reference = myFixture.file.findReferenceAt(offset)

        assertNotNull("the ground must carry a reference", reference)
        assertEquals("palette.css", reference!!.resolve()?.containingFile?.name)
    }

    /**
     * Use case: the grounds the reference offers. They are the ones the style sheets paint, which is the same
     * answer the completion gives — the two must never disagree.
     */
    fun `test offers the painted schemes as its variants`() {
        val variants = referenceAt("slate")!!.variants.map { it.toString() }

        assertTrue(variants.any { it.contains("slate") })
        assertTrue(variants.any { it.contains("default") })
    }

    /**
     * Use case: a ground the theme paints itself while no installation can be read — which is the state of a
     * fresh checkout, and of this test run. The name is a ground all the same and is offered as a variant, but
     * there is no file to lead to, so the reference stays unresolved and marks nothing.
     */
    fun `test offers a ground of the theme without a file to lead to`() {
        myFixture.addFileToProject("docs/stylesheets/extra.css", ".md-header { color: red; }")
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: slate
            """.trimIndent() + "\n",
        )
        val offset = myFixture.file.text.indexOf("scheme: slate") + "scheme: ".length
        val reference = myFixture.file.findReferenceAt(offset)

        assertNotNull("the ground must carry a reference", reference)
        assertNull(reference!!.resolve())
        assertTrue(reference.variants.map { it.toString() }.any { it.contains("slate") })
    }

    /**
     * Use case: a colour of the same palette. Only the ground is a name of the CSS; `primary` is a value of
     * the theme and must carry no reference of this kind.
     */
    fun `test puts no reference on a colour of the palette`() {
        configure("slate")
        val offset = myFixture.file.text.indexOf("indigo")
        val reference = myFixture.file.findReferenceAt(offset)

        assertFalse(reference is MkDocsMaterialSchemeReference)
    }

    /**
     * Returns the reference sitting on the ground [scheme] of a site whose style sheet paints `default` and
     * `slate`.
     *
     * @param scheme the value written behind `theme.palette.scheme`
     */
    private fun referenceAt(scheme: String): PsiReference? {
        configure(scheme)
        val offset = myFixture.file.text.indexOf("scheme: $scheme") + "scheme: ".length
        return myFixture.file.findReferenceAt(offset)
    }

    /**
     * Writes the site of this test, with [scheme] as the ground of its palette.
     *
     * @param scheme the value written behind `theme.palette.scheme`
     */
    private fun configure(scheme: String) {
        myFixture.addFileToProject(
            "docs/stylesheets/extra.css",
            """
            [data-md-color-scheme="default"] { --md-primary-fg-color: #EE0F0F; }
            [data-md-color-scheme="slate"] { --md-primary-fg-color: #101010; }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: $scheme
                primary: indigo
            """.trimIndent() + "\n",
        )
    }
}
