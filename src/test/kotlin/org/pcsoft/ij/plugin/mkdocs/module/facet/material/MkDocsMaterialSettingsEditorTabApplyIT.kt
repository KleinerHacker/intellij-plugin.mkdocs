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

package org.pcsoft.ij.plugin.mkdocs.module.facet.material

import com.intellij.facet.Facet
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.FacetsProvider
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureFlag
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPages
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the Material settings pages in the host that writes: the tabs of the Angular Material facet. Two
 * promises are checked, and both are what makes the tabs safe to use on a file somebody wrote by hand — the
 * tab reads the configuration file rather than the remembered facet state, and applying it touches the keys
 * that changed and nothing else.
 */
class MkDocsMaterialSettingsEditorTabApplyIT : HeavyPlatformTestCase() {

    /** The tabs, in the order [MkDocsMaterialSettingsPages] builds them. */
    private enum class Tab { APPEARANCE, FEATURES, ASSETS, EXTENSIONS }

    /** A site whose file carries a comment, a palette, an extension and a key of no interest here. */
    private val siteText: String = """
        # The handbook of the project
        site_name: Handbook
        site_author: Someone
        theme:
          name: material
          palette:
            primary: indigo
        markdown_extensions:
          - admonition
    """.trimIndent() + "\n"

    /**
     * Use case: the facet tab is opened on a site that declares a palette. The pages have to show what the
     * file says — the facet remembers nothing but the theme name — and nothing may count as modified before
     * the user touched anything.
     */
    fun `test reads the configuration file and starts unmodified`() {
        createSite(siteText)
        val page = pages()

        val shown = page.set.applyTo(MkDocsMaterialSettings.EMPTY)
        assertEquals(MkDocsMaterialColor.INDIGO, shown.light.primary)
        assertEquals(MkDocsMaterialSettings.PaletteMode.SINGLE, shown.paletteMode)
        assertEquals(setOf("admonition"), shown.extensions)
        page.tabs.forEach { assertFalse("nothing may be modified after reset", it.isModified) }
    }

    /**
     * Use case: the user picks another primary colour and applies. Only the colour may change — the comment
     * of the author, the keys this model never saw and the extension list have to come out of the write
     * untouched, because an *Apply* that rewrites a file nobody asked it to touch is unusable.
     */
    fun `test applying a changed colour writes only that key`() {
        val configFile = createSite(siteText)
        val page = pages()

        page.set.appearance.setPrimaryForTest(MkDocsMaterialColor.TEAL)

        val tab = page.tabs[Tab.APPEARANCE.ordinal]
        assertTrue("the tab must notice the change", tab.isModified)
        tab.apply()

        val text = text(configFile)
        assertTrue("the colour must have changed", text.contains("primary: teal"))
        assertFalse("and the old one must be gone", text.contains("indigo"))
        assertTrue("the comment must survive", text.contains("# The handbook of the project"))
        assertTrue("an unrelated key must survive", text.contains("site_author: Someone"))
        assertTrue("the extensions must survive", text.contains("- admonition"))
        assertFalse("nothing may be modified any more", tab.isModified)
    }

    /**
     * Use case: the user ticks a feature flag and applies. `theme.features` has to appear next to the palette
     * without the palette being rewritten.
     */
    fun `test applying a ticked feature adds it to the file`() {
        val configFile = createSite(siteText)
        val page = pages()

        page.set.features.setSelectedForTest(MkDocsMaterialFeatureFlag.NAVIGATION_TABS, true)

        val tab = page.tabs[Tab.FEATURES.ordinal]
        assertTrue(tab.isModified)
        tab.apply()

        val text = text(configFile)
        assertTrue("the flag must be listed", text.contains("navigation.tabs"))
        assertTrue("the palette must survive", text.contains("primary: indigo"))
        assertTrue("the comment must survive", text.contains("# The handbook of the project"))
    }

    /**
     * Use case: the dialog is opened and closed without anything being typed. Applying every tab must leave
     * the file exactly as it was, down to the last byte — a site relying on the defaults of the theme must
     * not suddenly carry them in writing.
     */
    fun `test applying untouched tabs changes nothing`() {
        val configFile = createSite(siteText)
        val page = pages()

        page.tabs.forEach { it.apply() }

        assertEquals(siteText, text(configFile))
    }

    /**
     * Builds the four settings tabs over one set of pages, the way the facet configuration does, and fills
     * them from the file.
     */
    private fun pages(): Pages {
        val context = TestFacetEditorContext(moduleNamed("Handbook"))
        val set = MkDocsMaterialSettingsPages(project)
        val tabs = set.pages.map { MkDocsMaterialSettingsEditorTab(it, context) }
        tabs.forEach { it.reset() }
        return Pages(set, tabs)
    }

    /**
     * The pages under test and the tabs hosting them.
     *
     * @property set the four pages, wired to each other
     * @property tabs the tabs showing them, in the same order
     */
    private class Pages(
        val set: MkDocsMaterialSettingsPages,
        val tabs: List<MkDocsMaterialSettingsEditorTab>,
    )

    /**
     * Writes an `mkdocs.yml` with [text] and lets the detection pick it up, so the module and its facets
     * exist before the test builds a tab on them.
     *
     * @param text the content of the configuration file
     */
    private fun createSite(text: String): VirtualFile {
        val configFile = VfsTestUtil.createFile(getOrCreateProjectBaseDir(), "handbook/mkdocs.yml", text)
        MkDocsModuleService.getInstance(project).sync()
        return configFile
    }

    /**
     * The module the detection created for the site.
     *
     * @param name the name of the module, which is the site name
     */
    private fun moduleNamed(name: String): Module {
        val module = ModuleManager.getInstance(project).findModuleByName(name)
        assertNotNull("expected a module named '$name'", module)
        return module!!
    }

    /**
     * The current content of [configFile].
     *
     * @param configFile the configuration file of the site
     */
    private fun text(configFile: VirtualFile): String =
        MkDocsConfig.yamlFileOf(project, configFile)!!.text

    /**
     * The context of an edited facet, reduced to what a Material settings tab asks of it.
     *
     * The tab reads the module and the project and nothing else; everything a real Project Structure dialog
     * offers beyond that is out of reach of the tab and therefore left unimplemented instead of faked into
     * something that only looks right.
     *
     * @param module the module the edited facet sits on
     */
    private class TestFacetEditorContext(private val module: Module) : UserDataHolderBase(), FacetEditorContext {

        override fun getProject(): Project = module.project

        override fun getModule(): Module = module

        override fun getRootModel(): ModuleRootModel = ModuleRootManager.getInstance(module)

        override fun isNewFacet(): Boolean = false

        override fun getFacetName(): String = "Angular Material"

        override fun getParentFacet(): Facet<*>? = null

        override fun findLibrary(name: String): Library? = null

        override fun getLibraries(): Array<Library> = emptyArray()

        override fun getLibraryFiles(library: Library?, rootType: OrderRootType?): Array<VirtualFile> = emptyArray()

        override fun getFacet(): Facet<*> = unsupported("getFacet")

        override fun getFacetsProvider(): FacetsProvider = unsupported("getFacetsProvider")

        override fun getModulesProvider(): ModulesProvider = unsupported("getModulesProvider")

        override fun getModifiableRootModel(): ModifiableRootModel = unsupported("getModifiableRootModel")

        override fun createProjectLibrary(
            name: String?,
            roots: Array<out VirtualFile>?,
            sources: Array<out VirtualFile>?,
        ): Library = unsupported("createProjectLibrary")

        private fun unsupported(member: String): Nothing =
            throw UnsupportedOperationException("$member is not used by the Material settings tab")
    }
}
