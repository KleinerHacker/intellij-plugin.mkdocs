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

package org.pcsoft.ij.plugin.mkdocs.material.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.LayeredIcon
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import javax.swing.Icon

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the mark the completion popup puts on the entries coming from *Material for MkDocs*: where it
 * appears, where it must not, and what happens to an entry that already draws an icon of its own.
 *
 * The entries themselves come from the icon completion, which is the one contributor of this plugin that
 * offers something at a key of the theme without an installed IDE schema behind it. What the mark is put on is
 * decided by `MkDocsMaterialKeys`, which is covered on its own.
 *
 * Every assertion compares against the very same completion on a site that is not on the Material theme,
 * rather than against a fixed icon. What an entry draws depends on whether the platform can render the SVG of
 * the installed package at all — and a comparison of the two runs says what the mark did to the entry no
 * matter how that turns out.
 */
class MkDocsMaterialOriginCompletionContributorIT : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same icon index.
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
    }

    /**
     * Use case: completing an icon name below `extra.social` of a Material site. The entry draws the icon it
     * offers — there the icon is the content of the entry, not a statement about where it comes from — so it
     * keeps its drawing and the mark is badged onto it.
     */
    fun `test badges an entry that draws an icon of its own`() {
        val plain = firstIconOf(socialIconSite(THEME_OTHER))
        val marked = firstIconOf(socialIconSite(THEME_MATERIAL))

        assertTrue("the entry was not badged", marked is LayeredIcon)
        val layers = (marked as LayeredIcon).allLayers
        assertEquals(2, layers.size)
        assertSame("the drawing of the entry was replaced", plain, layers[0])
        assertSame(MkDocsIcons.MaterialOverlay, layers[1])
    }

    /**
     * Use case: the same completion on a site that is not on the Material theme. Without the theme none of
     * these keys is the theme's, so the entry has to reach the popup exactly as its contributor built it.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        val plain = firstIconOf(socialIconSite(THEME_OTHER))

        assertSame(plain, firstIconOf(socialIconSite(THEME_OTHER)))
    }

    /**
     * Use case: a YAML file that is not an MkDocs configuration file, holding the very content that gets the
     * mark under the name of one. Its name decides, exactly as everywhere else in the plugin — nothing is
     * offered there, and so there is nothing to mark either.
     */
    fun `test offers nothing in a YAML file that is not a configuration file`() {
        completeIn(socialIconSite(THEME_MATERIAL), name = "other.yml")

        assertTrue(iconsOfLookup().isEmpty())
    }

    /**
     * Use case: completing a path below `docs_dir` of a Material site. The key is read by MkDocs, the entries
     * are files of the project, and the theme has nothing to do with either — marking them would say that a
     * change of theme takes the directory away.
     */
    fun `test leaves the entries of a key of MkDocs alone`() {
        myFixture.addFileToProject("docs/index.md", "# Handbook\n")
        val site = """
            site_name: Handbook
            theme:
              name: material
            docs_dir: <caret>
        """.trimIndent()

        completeIn(site)

        val icons = iconsOfLookup()
        assertFalse("no entries were offered at all", icons.isEmpty())
        assertFalse(icons.any { it === MkDocsIcons.MaterialBadge })
        assertFalse(icons.any { it is LayeredIcon && it.allLayers.last() === MkDocsIcons.MaterialOverlay })
    }

    /**
     * Returns a site completing an icon name below `extra.social`.
     *
     * @param theme the name to write into `theme.name`
     */
    private fun socialIconSite(theme: String): String = """
        site_name: Handbook
        theme:
          name: $theme
        extra:
          social:
            - icon: <caret>
    """.trimIndent()

    /**
     * Returns the icon of the first entry completion offers in a file holding [text].
     *
     * @param text the content of the file, with the caret marked
     * @param name the file name to write it under
     */
    private fun firstIconOf(text: String, name: String = "mkdocs.yml"): Icon {
        completeIn(text, name)
        val icons = iconsOfLookup()
        assertFalse("no entries were offered at all", icons.isEmpty())
        return icons.first() ?: error("the entry carries no icon at all")
    }

    /**
     * Runs completion in a file holding [text].
     *
     * @param text the content of the file, with the caret marked
     * @param name the file name to write it under
     */
    private fun completeIn(text: String, name: String = "mkdocs.yml") {
        // A test comparing two runs completes twice, and the installation is written for the first of them.
        if (myFixture.findFileInTempDir(ICON_FILE) == null) {
            myFixture.addFileToProject(
                ICON_FILE,
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>"""
            )
        }
        myFixture.configureByText(name, text + "\n")
        myFixture.completeBasic()
    }

    /**
     * Returns the icons the offered entries render with.
     */
    private fun iconsOfLookup(): List<Icon?> =
        (myFixture.lookupElements ?: emptyArray<LookupElement>()).map {
            LookupElementPresentation.renderElement(it).icon
        }

    private companion object {

        /** The theme the mark applies to. */
        const val THEME_MATERIAL = "material"

        /** A theme it does not apply to. */
        const val THEME_OTHER = "readthedocs"

        /** The one icon of the installed theme the completion offers. */
        const val ICON_FILE = ".venv/Lib/site-packages/material/templates/.icons/material/check.svg"
    }
}
