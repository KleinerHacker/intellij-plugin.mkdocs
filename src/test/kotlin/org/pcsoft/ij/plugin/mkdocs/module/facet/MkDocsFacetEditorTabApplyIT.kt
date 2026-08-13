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

package org.pcsoft.ij.plugin.mkdocs.module.facet

import com.intellij.facet.Facet
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetEditorValidator
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
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
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the facet page as the user meets it: text is typed into the fields of the built form, the page is
 * applied, and what `mkdocs.yml` and the file system look like afterwards is checked. The service doing the
 * work has tests of its own; what is tested here is the wiring in between — which field feeds which value,
 * and whether applying reaches the site at all.
 */
class MkDocsFacetEditorTabApplyIT : BasePlatformTestCase() {

    /** The fields of the page, in the order they are laid out. */
    private enum class Field { SITE_NAME, DOCS_DIR, SITE_DIR, ASSETS_DIR, STYLESHEETS_DIR }

    /**
     * Use case: the user renames the site on the page. The typed name has to reach `site_name` — the facet
     * cannot keep a name of its own, the configuration file is the source of truth.
     */
    fun `test applying a typed site name writes it into the configuration file`() {
        val configFile = site()
        val page = page()

        page.type(Field.SITE_NAME, "Field Manual")
        assertTrue("the page must notice the change", page.tab.isModified)
        page.tab.apply()

        assertEquals("Field Manual", runReadActionBlocking { MkDocsConfig.readSiteName(project, configFile) })
    }

    /**
     * Use case: the user renames the stylesheets directory on the page. Both halves of the promise the page
     * makes have to hold: the directory moves with its content, and the `extra_css` entry pointing into it
     * follows — otherwise the built site silently loses its styling.
     */
    fun `test applying a typed stylesheets directory moves it and rewrites extra_css`() {
        val configFile = site("site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        myFixture.addFileToProject("handbook/docs/stylesheets/extra.css", "body {}\n")
        val page = page()

        page.type(Field.STYLESHEETS_DIR, "css")
        page.tab.apply()

        val docs = configFile.parent.findChild("docs")!!
        assertNull("the old directory must be gone", docs.findChild("stylesheets"))
        assertNotNull("the file must have moved along", docs.findChild("css")?.findChild("extra.css"))
        assertEquals(listOf("css/extra.css"), runReadActionBlocking { MkDocsConfig.readExtraCss(project, configFile) })
    }

    /**
     * Use case: the user points the documentation directory somewhere else. The directory has to move and
     * `docs_dir` has to appear, even though the site never wrote the key while it used the default.
     */
    fun `test applying a typed documentation directory moves it and writes docs_dir`() {
        val configFile = site()
        val page = page()

        page.type(Field.DOCS_DIR, "documentation")
        page.tab.apply()

        assertNull(configFile.parent.findChild("docs"))
        assertNotNull(configFile.parent.findChild("documentation")?.findChild("index.md"))
        assertEquals("documentation", runReadActionBlocking { MkDocsConfig.readDocsDir(project, configFile) })
    }

    /**
     * Use case: the user changes the build output directory. It holds output the next build writes anyway, so
     * only `site_dir` may change — nothing is moved.
     */
    fun `test applying a typed output directory only writes site_dir`() {
        val configFile = site()
        val page = page()

        page.type(Field.SITE_DIR, "build/docs")
        page.tab.apply()

        assertEquals("build/docs", runReadActionBlocking { MkDocsConfig.readSiteDir(project, configFile) })
        assertNotNull("nothing may have moved", configFile.parent.findChild("docs")?.findChild("index.md"))
    }

    /**
     * Use case: the page is opened and applied without anything being typed. Nothing may move and nothing may
     * be written — a site that relied on the MkDocs defaults must not suddenly carry them in writing.
     */
    fun `test applying an untouched page changes nothing`() {
        val configFile = site()
        val page = page()

        assertFalse("an untouched page is not modified", page.tab.isModified)
        page.tab.apply()

        assertEquals("site_name: Handbook\n", runReadActionBlocking { MkDocsConfig.yamlFileOf(project, configFile)!!.text })
        assertNotNull(configFile.parent.findChild("docs"))
    }

    /**
     * Use case: the user types a name that cannot be applied — one carrying a path of its own, and an empty
     * site name. The page has to refuse it before anything is moved, because half a rename is far worse than
     * none.
     */
    fun `test refuses names that cannot be applied`() {
        site()
        val page = page()

        page.type(Field.ASSETS_DIR, "media/icons")
        assertFalse("a name carrying a path must be refused", page.validate().isOk)

        page.type(Field.ASSETS_DIR, "media")
        assertTrue(page.validate().isOk)

        page.type(Field.SITE_NAME, "  ")
        assertFalse("an empty site name must be refused", page.validate().isOk)
    }

    /**
     * Use case: a name is typed and the dialog is cancelled, which resets the page. The fields have to show
     * the site again, and the page must no longer count as modified.
     */
    fun `test resetting restores what the site says`() {
        site()
        val page = page()

        page.type(Field.DOCS_DIR, "documentation")
        assertTrue(page.tab.isModified)

        page.tab.reset()

        assertEquals("docs", page.text(Field.DOCS_DIR))
        assertFalse(page.tab.isModified)
    }

    /**
     * Creates a site with a start page and the two convention directories.
     *
     * @param configText the content of the configuration file
     */
    private fun site(configText: String = "site_name: Handbook\n"): VirtualFile {
        val configFile = myFixture.addFileToProject("handbook/mkdocs.yml", configText).virtualFile
        myFixture.addFileToProject("handbook/docs/index.md", "# Handbook\n")
        myFixture.addFileToProject("handbook/docs/assets/.gitkeep", "")
        myFixture.addFileToProject("handbook/docs/stylesheets/.gitkeep", "")
        return configFile
    }

    /** Builds the page of a facet describing the site created by [site]. */
    private fun page(): Page {
        val configuration = MkDocsFacetConfiguration().apply {
            siteName = "Handbook"
            configFilePath = "mkdocs.yml"
        }
        val validators = CollectingValidatorsManager()
        val tab = MkDocsFacetEditorTab(configuration, TestFacetEditorContext(myFixture.module), validators)
        return Page(tab, validators, tab.createComponent())
    }

    /**
     * The built page, together with the fields the user types into.
     *
     * @property tab the editor tab under test
     * @property validators the manager the tab registered its validator with
     * @param component the built form
     */
    private class Page(
        val tab: FacetEditorTab,
        private val validators: CollectingValidatorsManager,
        component: JComponent,
    ) {

        private val fields: List<JTextField> = collectTextFields(component)

        init {
            check(fields.size == Field.entries.size) {
                "expected ${Field.entries.size} fields on the page, found ${fields.size}"
            }
        }

        /** Types [value] into [field], replacing what is in it. */
        fun type(field: Field, value: String) {
            fields[field.ordinal].text = value
        }

        /** The text currently shown in [field]. */
        fun text(field: Field): String = fields[field.ordinal].text

        /** What the validator of the page reports for what is currently typed. */
        fun validate() = validators.validator!!.check()

        private companion object {

            /** Collects the text fields of [container] in layout order. */
            fun collectTextFields(container: Container): List<JTextField> = buildList {
                for (child in container.components) {
                    when (child) {
                        is JBTextField -> add(child)
                        is Container -> addAll(collectTextFields(child))
                    }
                }
            }
        }
    }

    /** Keeps the validator the tab registers, so a test can ask it directly. */
    private class CollectingValidatorsManager : FacetValidatorsManager {

        var validator: FacetEditorValidator? = null

        override fun registerValidator(validator: FacetEditorValidator, vararg componentsToWatch: JComponent) {
            this.validator = validator
        }

        override fun validate() = Unit
    }

    /**
     * The context of an edited facet, reduced to what the MkDocs tab asks of it.
     *
     * The tab reads the module and the project and nothing else; everything a real Project Structure dialog
     * offers beyond that — libraries, root models, the facet itself — is out of reach of the tab and therefore
     * left unimplemented instead of faked into something that only looks right.
     *
     * @param module the module the edited facet sits on
     */
    private class TestFacetEditorContext(private val module: Module) : UserDataHolderBase(), FacetEditorContext {

        override fun getProject(): Project = module.project

        override fun getModule(): Module = module

        override fun getRootModel(): ModuleRootModel = ModuleRootManager.getInstance(module)

        override fun isNewFacet(): Boolean = false

        override fun getFacetName(): String = "MkDocs"

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
            throw UnsupportedOperationException("$member is not used by the MkDocs facet tab")
    }
}
