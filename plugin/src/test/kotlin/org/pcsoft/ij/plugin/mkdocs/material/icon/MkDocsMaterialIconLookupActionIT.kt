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

import com.intellij.codeInsight.lookup.LookupElementAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Consumer
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the way the installation is read again from the completion popup: the footer menu of the popup, which
 * `LookupActionProvider` fills, and NOT an entry of the list of names. Which of the two it is cannot be
 * decided on the provider alone — it takes a real popup, built by the registered contributor, to say whether
 * the entries carry what the provider recognises and whether the list stayed free of anything that is not an
 * icon.
 *
 * The theme is installed for the whole class rather than written into the project: where it lies is asked of
 * pip, and a test must depend neither on the machine it runs on nor on a directory of its fixture.
 */
class MkDocsMaterialIconLookupActionIT : BasePlatformTestCase() {

    private val provider = MkDocsMaterialIconLookupActionProvider()

    override fun setUp() {
        super.setUp()
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
     * Use case: the user standing on an icon of the popup, wondering why the one they just installed is not in
     * the list. The footer menu of the popup carries the way to read the installation again.
     */
    fun `test offers reading the installation again on a set entry`() {
        completeIcons()
        val element = myFixture.lookupElements?.firstOrNull { it.lookupString in SETS }
            ?: error("the popup offered none of the installed sets")

        assertSize(1, actionsFor(element))
    }

    /**
     * Use case: the same wondering, one level further down. The walk through the sets ends on the icons, and the
     * list of a set is where a freshly installed icon is missed just as much as in the list of the sets.
     */
    fun `test offers reading the installation again on an icon entry`() {
        completeIcons(SET_MATERIAL)
        val element = myFixture.lookupElements?.firstOrNull { it.lookupString == ICON_SEGMENT }
            ?: error("the popup offered none of the icons of the set")

        assertSize(1, actionsFor(element))
    }

    /**
     * Use case: the same popup, seen from the list. The way to read the installation again must not be one of
     * the names — an entry there would be a name that inserts no name, and the list belongs to the icons.
     */
    fun `test adds no entry of its own to the list of names`() {
        completeIcons()

        val offered = myFixture.lookupElementStrings.orEmpty()

        assertContainsElements(offered, SETS)
        assertEmpty(offered.filterNot { it in SETS })
    }

    /**
     * Use case: the entries of the popup that come from anywhere else. The footer menu is shared by everything
     * the IDE completes, so an entry that is not an icon of the theme must be left alone.
     */
    fun `test leaves an entry of another completion alone`() {
        completeIcons()

        val foreign = LookupElementBuilder.create("site_name")

        assertEmpty(actionsFor(foreign))
    }

    /**
     * Returns the actions the provider offers for [element] in the popup standing open.
     *
     * @param element the entry the user is standing on
     */
    private fun actionsFor(element: com.intellij.codeInsight.lookup.LookupElement): List<LookupElementAction> {
        val lookup = LookupManager.getActiveLookup(myFixture.editor) ?: error("no popup is open")
        val actions = mutableListOf<LookupElementAction>()
        provider.fillActions(element, lookup, Consumer { actions += it })
        return actions
    }

    /**
     * Opens the completion popup on the icon of the link to the repository.
     *
     * @param written what already stands in the value, which decides the level the popup offers
     */
    private fun completeIcons(written: String = "") {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: $written<caret>
            """.trimIndent() + "\n",
        )
        project.service<MkDocsModuleService>().sync()
        myFixture.completeBasic()
    }

    private companion object {

        /** The icons the installation of this test ships. */
        val ICON_NAMES = listOf("fontawesome/brands/github", "material/check", "material/pencil")

        /** The `material` set, as an entry of the top level writes it. */
        const val SET_MATERIAL = "material/"

        /** An icon below that set, as the level of the set writes it. */
        const val ICON_SEGMENT = "check"

        /** The sets of the installation, which the top level of the popup offers. */
        val SETS = listOf("fontawesome/", SET_MATERIAL)
    }
}
