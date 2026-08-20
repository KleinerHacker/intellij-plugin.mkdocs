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

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the suggestion lists of the icon names in an `mkdocs.yml`: at which keys of the configuration file they
 * appear, at which they must not, and which level of the sets each of them offers. What the index reads out of an
 * installed theme is covered by `MkDocsMaterialIconIndexTest` and the levels themselves by
 * `MkDocsMaterialIconTreeTest`; that they reach the popup at exactly the places the theme addresses an icon is
 * decided by the registered contributor and by the shape of the file, and only a run of the real completion
 * answers it.
 *
 * The theme is installed for the whole class rather than written into the project: where it lies is asked of
 * pip, and a test must depend neither on the machine it runs on nor on a directory of its fixture.
 */
class MkDocsMaterialIconCompletionIT : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same index. The sites
        // of two tests share a path, so what one of them found would otherwise answer for the other.
        MkDocsMaterialInstalledTheme.install(project, ICON_NAMES.map { "$it.svg" })
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstalledTheme.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: `theme.icon.repo`, the icon of the link to the repository of the project. The most common of the
     * icon keys, and the one the documentation of the theme opens its chapter with. An empty value stands at the
     * top of the walk, so what is offered are the sets.
     */
    fun `test offers the sets for the repository icon`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: <caret>
            """
        )

        assertContainsElements(offered, SETS)
    }

    /**
     * Use case: `theme.icon.logo`, the icon standing in for the logo of the site. A sibling of the key above and
     * covered on its own, because the contributor decides on the path rather than on the single key.
     */
    fun `test offers the sets for the logo icon`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                logo: <caret>
            """
        )

        assertContainsElements(offered, SETS)
    }

    /**
     * Use case: `theme.palette[].toggle.icon`, the icon on the button switching between the light and the dark
     * palette. The key sits below a sequence, so the path is built across an entry without a name of its own.
     */
    fun `test offers the sets for the palette toggle`() {
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

        assertContainsElements(offered, SETS)
    }

    /**
     * Use case: `extra.social[].icon`, the icons of the links in the footer. The third place a configuration file
     * names an icon, and the one below `extra` rather than below `theme`.
     */
    fun `test offers the sets for a social link`() {
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

        assertContainsElements(offered, SETS)
    }

    /**
     * Use case: the two mappings below `theme.icon` whose keys the author invents — one icon per admonition
     * type and one per tag. The key says nothing there, so only the path can decide.
     */
    fun `test offers the sets for an admonition and for a tag`() {
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
        assertContainsElements(admonition, SETS)

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
        assertContainsElements(tag, SETS)
    }

    /**
     * Use case: the icon of a rating of the feedback widget, the deepest icon key of the configuration file.
     */
    fun `test offers the sets for a feedback rating`() {
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

        assertContainsElements(offered, SETS)
    }

    /**
     * Use case: a set was chosen and the separator written. What follows are the icons of that set, by their
     * segment alone — the set is already in the file, and an entry repeating it would write it twice.
     */
    fun `test offers the icons of a set below its separator`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: material/<caret>
            """
        )

        assertContainsElements(offered, listOf("alert", "check"))
        assertDoesntContain(offered, SET_MATERIAL)
    }

    /**
     * Use case: a set that splits into subsets, as `fontawesome` does. Its level is that split, not the icons —
     * those lie one level further down and are several hundred entries in an installed theme.
     */
    fun `test offers the nesting of a set as its own level`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: fontawesome/<caret>
            """
        )

        assertContainsElements(offered, listOf("brands/", "regular/"))
        assertDoesntContain(offered, "github")
    }

    /**
     * Use case: the bottom of the walk, below a nested set. The icons stand there and nowhere else.
     */
    fun `test offers the icons below a nested set`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: fontawesome/brands/<caret>
            """
        )

        assertContainsElements(offered, listOf("github", "gitlab"))
    }

    /**
     * Use case: what the entries look like. A set carries the mark of the theme and says that it is one; an icon
     * carries its own drawing and nothing else, because its level already names the set it belongs to.
     */
    fun `test paints a set as a set and an icon as its drawing`() {
        complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: <caret>
            """
        )
        val set = myFixture.lookupElements.orEmpty().first { it.lookupString == SET_MATERIAL }
        val setPresentation = LookupElementPresentation()
        set.renderElement(setPresentation)
        assertEquals(SET_MATERIAL, setPresentation.itemText)
        assertNotNull(setPresentation.icon)
        assertNotNull(setPresentation.typeText)

        complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: material/<caret>
            """
        )
        val icon = myFixture.lookupElements.orEmpty().first { it.lookupString == "check" }
        val iconPresentation = LookupElementPresentation()
        icon.renderElement(iconPresentation)
        assertEquals("check", iconPresentation.itemText)
        assertNotNull(iconPresentation.icon)
        assertNull(iconPresentation.typeText)
    }

    /**
     * Use case: a set that was misspelt. Nothing lies below it, and falling back to the level above would offer
     * entries that cannot follow what stands in the file.
     */
    fun `test offers nothing below a set that is none`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: materail/<caret>
            """
        )

        assertDoesntContain(offered, "check")
        assertDoesntContain(offered, SET_MATERIAL)
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

        assertDoesntContain(offered, SET_MATERIAL)
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

        assertDoesntContain(offered, SET_MATERIAL)
    }

    /**
     * Use case: a fresh checkout where pip reports no installation. Nothing is offered and nothing goes wrong: which
     * icons exist is a property of the installed package, and until it is installed the answer is empty.
     */
    fun `test offers nothing without an installation`() {
        // The set-up of the class installed the theme; here it is taken away again, which is the state of a
        // checkout whose environment carries no mkdocs-material.
        MkDocsMaterialInstalledTheme.uninstall(project)
        val text = """
            site_name: Plain
            theme:
              name: material
              icon:
                repo: material
        """.trimIndent() + "\n"
        val file = myFixture.addFileToProject("plain/mkdocs.yml", text).virtualFile
        project.service<MkDocsModuleService>().sync()

        myFixture.configureFromExistingVirtualFile(file)
        myFixture.editor.caretModel.moveToOffset(text.indexOf(VALUE_MATERIAL) + VALUE_MATERIAL.length)
        myFixture.completeBasic()

        assertDoesntContain(myFixture.lookupElementStrings.orEmpty(), SET_MATERIAL)
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

        assertDoesntContain(offered, SET_MATERIAL)
    }

    /**
     * Runs completion in a configuration file holding [text] and returns what it offers.
     *
     * The theme is installed by the set-up of the class. The site is detected afterwards, which is what
     * attaches the facet the mark of the theme is decided on.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @param name the file name to write the content under
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String, name: String = "mkdocs.yml"): List<String> {
        myFixture.configureByText(name, text.trimIndent() + "\n")
        project.service<MkDocsModuleService>().sync()

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private companion object {

        /** The `material` set, as an entry of the top level writes it. */
        const val SET_MATERIAL = "material/"

        /** The value the caret of the test without an installation sits behind. */
        const val VALUE_MATERIAL = "repo: material"

        /** The sets of the installed theme, as the top level offers them. */
        val SETS = listOf("fontawesome/", SET_MATERIAL)

        /**
         * The icons of the installed theme the tests complete against.
         *
         * Two per level throughout: a level holding a single entry is taken by the completion on its own, and
         * a test of what a popup offers must not run against a popup that closed itself.
         */
        val ICON_NAMES = listOf(
            "material/check",
            "material/alert",
            "fontawesome/brands/github",
            "fontawesome/brands/gitlab",
            "fontawesome/regular/star",
        )
    }
}
