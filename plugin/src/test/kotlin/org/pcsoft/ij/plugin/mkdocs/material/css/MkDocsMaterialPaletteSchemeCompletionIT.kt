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

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Drives the completion popup of a real `mkdocs.yml` at `theme.palette.scheme`. Which grounds are offered is
 * decided by `MkDocsMaterialPaletteSchemeCompletionContributor` reading the style sheets a site loads, but
 * *whether* they reach the popup is decided by the platform: the file has to be recognised as the
 * configuration file of a Material site, the caret has to sit in the value of that key, and the JSON schema
 * describing the key as a plain string must not swallow the entries.
 *
 * The grounds of the site are the ones the style sheet of the fixture paints, never a written list — a name
 * added to the CSS has to arrive in the popup without this test being touched. The grounds of the theme are
 * asked of `MkDocsMaterialScheme`, which is what the facet falls back to while no installation can be read;
 * the test run has none.
 */
class MkDocsMaterialPaletteSchemeCompletionIT : BasePlatformTestCase() {

    /** The grounds the installed theme paints, as the facet names them without an installation. */
    private val themeSchemes get() = MkDocsMaterialScheme.entries.map { it.id }

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
     * Use case: the grounds of the theme once its package is there. They are then rules of the style sheet it
     * ships, read out of it — not two names the plugin has been told about — which is what makes a version of
     * the theme adding a third one arrive in the popup by itself.
     */
    fun `test reads the grounds of the theme out of the installed style sheet`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)

        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertContainsElements(offered, MkDocsMaterialInstalledTheme.SCHEMES)
    }

    /**
     * Use case: the shape the documentation of the theme is written in — the palette as a single mapping, the
     * caret on the ground. Every scheme the style sheet of the site paints has to be offered there.
     */
    fun `test offers the schemes of the style sheet in the mapping form`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertContainsElements(offered, SITE_SCHEMES)
    }

    /**
     * Use case: the grounds the theme itself paints. `default` and `slate` are rules of the style sheet the
     * installed package ships, so they belong in the popup next to the ones of the site — a site standing on
     * one of them is the ordinary case.
     */
    fun `test offers the grounds the theme paints as well`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertContainsElements(offered, themeSchemes)
    }

    /**
     * Use case: the other shape the key accepts, the palette as the sequence of a colour scheme toggle. A
     * ground offered in one form but not the other would be an arbitrary difference.
     */
    fun `test offers the schemes in the sequence form`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                - scheme: <caret>
            """
        )

        assertContainsElements(offered, SITE_SCHEMES)
    }

    /**
     * Use case: an entry that already carries other keys, and a second entry written above it. What the
     * palette already holds must not decide whether the grounds are offered.
     */
    fun `test offers the schemes below an entry that is already written`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                - scheme: ${SITE_SCHEMES.first()}
                  primary: indigo
                - primary: cyan
                  scheme: <caret>
            """
        )

        assertContainsElements(offered, SITE_SCHEMES)
    }

    /**
     * Use case: a site whose `extra_css` names several style sheets. A ground is a ground wherever it is
     * painted, so the entries of every named file have to arrive.
     */
    fun `test offers the schemes of every style sheet the site names`() {
        myFixture.addFileToProject(
            "docs/stylesheets/night.css",
            """[data-md-color-scheme="$EXTRA_SCHEME"] { --md-primary-fg-color: #101010; }""",
        )
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
              - stylesheets/night.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertContainsElements(offered, SITE_SCHEMES + EXTRA_SCHEME)
    }

    /**
     * Use case: reading the popup. A ground says nothing about where it is painted, so an entry of the site
     * carries the style sheet it was read from, and the mark of the theme it belongs to.
     */
    fun `test shows which style sheet a scheme of the site comes from`() {
        complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        val presentation = presentationOf(SITE_SCHEMES.first())
        assertEquals("extra.css", presentation.typeText)
        assertNotNull("the entry must carry the mark of the theme", presentation.icon)
    }

    /**
     * Use case: the same for a ground of the theme. Naming the file it lies in would name a minified asset
     * whose name carries a build hash, so the theme is named instead.
     */
    fun `test names the theme behind a ground it paints itself`() {
        complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertEquals("Material for MkDocs", presentationOf(themeSchemes.first()).typeText)
    }

    /**
     * Use case: a site whose style sheets paint no ground of their own. The grounds of the theme are still
     * there — an empty popup at that key would say a site cannot stand anywhere.
     */
    fun `test offers the grounds of the theme while the site paints none`() {
        myFixture.addFileToProject("docs/stylesheets/plain.css", ".md-header { color: red; }")
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/plain.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """,
            styled = false,
        )

        assertContainsElements(offered, themeSchemes)
        assertDoesntContain(offered, SITE_SCHEMES)
    }

    /**
     * Use case: the toggle of the same palette, one level deeper. `icon` and `name` are what a toggle carries,
     * and a ground is no answer there.
     */
    fun `test stays out of the toggle of a palette`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                - scheme: ${SITE_SCHEMES.first()}
                  toggle:
                    icon: <caret>
            """
        )

        assertDoesntContain(offered, SITE_SCHEMES)
    }

    /**
     * Use case: a key of the same name at another place of the very same file. `scheme` below `extra` belongs
     * to whoever reads it — the path decides, not the name of the nearest key.
     */
    fun `test stays out of a key of the same name elsewhere`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
            extra:
              scheme: <caret>
            """
        )

        assertDoesntContain(offered, SITE_SCHEMES)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very
     * content that gets the grounds under the name of one. Its name decides.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: <caret>
            """,
            name = "other.yml",
        )

        assertDoesntContain(offered, SITE_SCHEMES)
    }

    /**
     * Use case: a site that is not rendered with the Material theme. `theme.palette` is the theme's own key,
     * and a site on another one has no business being told what to write there.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val offered = complete(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: readthedocs
              palette:
                scheme: <caret>
            """
        )

        assertDoesntContain(offered, SITE_SCHEMES)
    }

    /**
     * Returns how the popup renders the entry offering [lookupString], of the completion run last.
     *
     * @param lookupString the ground the entry offers
     */
    private fun presentationOf(lookupString: String): LookupElementPresentation {
        val element = (myFixture.lookupElements ?: emptyArray<LookupElement>())
            .firstOrNull { it.lookupString == lookupString }
        assertNotNull("the popup must offer $lookupString", element)
        return LookupElementPresentation.renderElement(element!!)
    }

    /**
     * Runs completion in an `mkdocs.yml` holding [text] and returns what it offers.
     *
     * The site is detected before the caret is asked, because the refined schema of the theme is bound to the
     * facet the detection attaches. The cached answer of the schema provider is dropped afterwards — the light
     * fixture hands every test of a class the same project, and with it the answer given for the file of the
     * test before.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @param name the file name to write the content under
     * @param styled whether the style sheet painting [SITE_SCHEMES] is written next to it
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String, name: String = "mkdocs.yml", styled: Boolean = true): List<String> {
        if (styled) myFixture.addFileToProject("docs/stylesheets/extra.css", STYLE_SHEET)
        myFixture.configureByText(name, text.trimIndent() + "\n")
        project.service<MkDocsModuleService>().sync()
        MkDocsMaterialSchemaCache.invalidate(project)

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private companion object {

        /**
         * The grounds the style sheet of this fixture paints.
         *
         * Deliberately none of the theme's own: those arrive from the other source and would say nothing
         * about whether the style sheet of the site was read at all.
         */
        val SITE_SCHEMES = listOf("ochre", "sepia")

        /** A ground painted by a second style sheet only. */
        const val EXTRA_SCHEME = "midnight"

        /** The style sheet behind `extra_css`, painting exactly [SITE_SCHEMES]. */
        val STYLE_SHEET = SITE_SCHEMES.joinToString("\n") {
            """[data-md-color-scheme="$it"] { --md-primary-fg-color: #EE0F0F; }"""
        }
    }
}
