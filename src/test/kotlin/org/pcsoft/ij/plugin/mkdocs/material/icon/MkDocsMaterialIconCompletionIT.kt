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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the suggestion lists of the icon names in an `mkdocs.yml`: at which keys of the configuration file they
 * appear, and at which they must not. What the index reads out of an installed theme is covered by
 * `MkDocsMaterialIconIndexTest`; that the names reach the popup at exactly the three places the theme addresses
 * an icon is decided by the registered contributor and by the shape of the file, and only a run of the real
 * completion answers it.
 *
 * Every site is written with an installed theme below its own root, because that is where the index looks: the
 * icons of a site are a property of the package installed next to it, not of the IDE.
 */
class MkDocsMaterialIconCompletionIT : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same index. The sites
        // of two tests share a path, so what one of them found would otherwise answer for the other.
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
    }

    /**
     * Use case: `theme.icon.repo`, the icon of the link to the repository of the project. The most common of the
     * icon keys, and the one the documentation of the theme opens its chapter with.
     */
    fun `test offers the installed icons for the repository icon`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: <caret>
            """
        )

        assertContainsElements(offered, ICON_NAMES)
    }

    /**
     * Use case: `theme.icon.logo`, the icon standing in for the logo of the site. A sibling of the key above and
     * covered on its own, because the contributor decides on the path rather than on the single key.
     */
    fun `test offers the installed icons for the logo icon`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                logo: <caret>
            """
        )

        assertContainsElements(offered, ICON_NAMES)
    }

    /**
     * Use case: `theme.palette[].toggle.icon`, the icon on the button switching between the light and the dark
     * palette. The key sits below a sequence, so the path is built across an entry without a name of its own.
     */
    fun `test offers the installed icons for the palette toggle`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - scheme: default
                  toggle:
                    icon: <caret>
            """
        )

        assertContainsElements(offered, ICON_NAMES)
    }

    /**
     * Use case: `extra.social[].icon`, the icons of the links in the footer. The third place a configuration file
     * names an icon, and the one below `extra` rather than below `theme`.
     */
    fun `test offers the installed icons for a social link`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              social:
                - icon: <caret>
            """
        )

        assertContainsElements(offered, ICON_NAMES)
    }

    /**
     * Use case: a set that splits into subsets, as `fontawesome` does. The nesting is part of the name the
     * configuration file writes, so the entry has to carry every level of it — an entry offering `github` alone
     * would write a name the theme cannot resolve.
     */
    fun `test offers a nested set with every level of its name`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: <caret>
            """
        )

        assertContainsElements(offered, listOf(ICON_NESTED))
        assertDoesntContain(offered, "github")
    }

    /**
     * Use case: the two mappings below `theme.icon` whose keys the author invents — one icon per admonition
     * type and one per tag. The key says nothing there, so only the path can decide.
     */
    fun `test offers the installed icons for an admonition and for a tag`() {
        val admonition = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                admonition:
                  note: <caret>
            """
        )
        assertContainsElements(admonition, ICON_NAMES)

        val tag = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                tag:
                  html: <caret>
            """
        )
        assertContainsElements(tag, ICON_NAMES)
    }

    /**
     * Use case: the icon of a rating of the feedback widget, the deepest icon key of the configuration file.
     */
    fun `test offers the installed icons for a feedback rating`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              analytics:
                feedback:
                  ratings:
                    - icon: <caret>
            """
        )

        assertContainsElements(offered, ICON_NAMES)
    }

    /**
     * Use case: another key of the very same `theme` block. `theme.name` names the theme rather than an icon, and
     * an icon name written there produces a site that does not build.
     */
    fun `test offers no icon at a key that names no icon`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: <caret>
            """
        )

        assertDoesntContain(offered, ICON_NESTED)
    }

    /**
     * Use case: a key spelled `icon` outside the paths the theme reads one at. The contributor decides on the
     * whole path, not on the name of the nearest key alone — offering the thousands of icon names below a key of
     * a plugin would bury whatever that plugin offers there itself.
     */
    fun `test offers no icon at a key named icon elsewhere`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              status:
                icon: <caret>
            """
        )

        assertDoesntContain(offered, ICON_NESTED)
    }

    /**
     * Use case: a fresh checkout without a virtual environment. Nothing is offered and nothing goes wrong: which
     * icons exist is a property of the installed package, and until it is installed the answer is empty.
     */
    fun `test offers nothing without an installation`() {
        // A site of its own, in a directory the installation of the other tests is not below: the light fixture
        // hands every test of a class the same project, so the root already carries an installed theme.
        val text = """
            site_name: Plain
            theme:
              name: material
              icon:
                repo: material
        """.trimIndent() + "\n"
        val file = myFixture.addFileToProject("plain/mkdocs.yml", text).virtualFile
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
        MkDocsModuleService.getInstance(project).sync()

        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.caretModel.moveToOffset(text.indexOf(VALUE_MATERIAL) + VALUE_MATERIAL.length)
        myFixture.completeBasic()

        assertDoesntContain(myFixture.lookupElementStrings.orEmpty(), ICON_CHECK)
    }

    /**
     * Use case: a YAML file of the project that is not a configuration file of MkDocs, holding the very content
     * that gets the icons under the name of one. Its name decides, exactly as everywhere else in the plugin.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: <caret>
            """,
            name = "other.yml"
        )

        assertDoesntContain(offered, ICON_NESTED)
    }

    /**
     * Runs completion in a configuration file holding [text] and returns what it offers.
     *
     * The installed theme is written before the file itself, so the index finds it on the first ask. The site is
     * detected afterwards, which is what attaches the facet the mark of the theme is decided on.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @param name the file name to write the content under
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String, name: String = "mkdocs.yml"): List<String> {
        ICON_NAMES.forEach { icon ->
            if (myFixture.findFileInTempDir("$INSTALLED/$icon.svg") == null) {
                myFixture.addFileToProject("$INSTALLED/$icon.svg", SVG)
            }
        }
        MkDocsMaterialIconIndex.getInstance(project).invalidate()

        myFixture.configureByText(name, text.trimIndent() + "\n")
        MkDocsModuleService.getInstance(project).sync()

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private companion object {

        /** The path of the icon sets inside an installed package, below the site root. */
        const val INSTALLED = ".venv/Lib/site-packages/material/templates/.icons"

        /** An icon of a nested set, which the configuration file names with every level in front of it. */
        const val ICON_NESTED = "fontawesome/brands/github"

        /** An icon of the flat `material` set. */
        const val ICON_CHECK = "material/check"

        /** The value the caret of the test without an installation sits behind, the prefix of [ICON_CHECK]. */
        const val VALUE_MATERIAL = "repo: material"

        /** The icons of the installed theme the tests complete against. */
        val ICON_NAMES = listOf(ICON_CHECK, "material/alert", ICON_NESTED)

        /** The drawing every installed icon is written with; the popup renders it next to the entry. */
        const val SVG =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>"""
    }
}
