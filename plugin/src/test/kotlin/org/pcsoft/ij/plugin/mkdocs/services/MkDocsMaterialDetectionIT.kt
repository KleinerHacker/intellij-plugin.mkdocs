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

package org.pcsoft.ij.plugin.mkdocs.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacet
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the detection of the Angular Material facet on a real project on disk: the facet has to follow
 * the `theme` key of the configuration file in both directions, and it has to go away together with the
 * MkDocs facet it belongs to.
 */
class MkDocsMaterialDetectionIT : HeavyPlatformTestCase() {

    private val service: MkDocsModuleService
        get() = project.service<MkDocsModuleService>()

    /**
     * Use case: a site declaring the Material theme is checked out. The detection creates the MkDocs module
     * and gives it the Angular Material facet next to the MkDocs one, so the site is marked as a Material
     * site without the user having to configure anything.
     */
    fun `test attaches the facet to a material site`() {
        createConfig("handbook/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")

        service.sync()

        val module = moduleNamed("Handbook")
        val facet = MkDocsMaterialFacet.getInstance(module)
        assertNotNull("a site on the material theme must carry the Angular Material facet", facet)
        assertEquals("material", facet!!.configuration.themeName)
        assertNotNull(
            "the facet only ever belongs next to the MkDocs facet of the same module",
            MkDocsFacet.getInstance(module),
        )
    }

    /**
     * Regression: opening a project whose module already carries the MkDocs facet — written into the `.iml`
     * by an earlier session — while the Angular Material facet is not there yet, although `mkdocs.yml`
     * plainly declares the Material theme.
     *
     * This is the state every project was in the first time the Angular Material feature shipped, and it used
     * to destroy the file: the detection added the facet, a later scan read the file before it was parsed,
     * concluded the site was not a Material site and dropped the facet again, and the facet listener carried
     * that removal into the file — deleting the `theme` block the user had written, without anyone asking for
     * it. The theme has to survive here, settings and all, and the facet has to end up attached.
     */
    fun `test keeps the theme when only the mkdocs facet was registered`() {
        // The state the reported project was in: the MkDocs facet is registered, the Material facet is not.
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")
        service.sync()
        val module = moduleNamed("Handbook")
        assertNotNull("the MkDocs facet is the precondition of this test", MkDocsFacet.getInstance(module))
        assertNull("the Material facet must not be there yet", MkDocsMaterialFacet.getInstance(module))

        // …while mkdocs.yml declares the Material theme, with settings of the user's own next to it.
        val declared = "site_name: Handbook\ntheme:\n  name: material\n  palette:\n    primary: indigo\n"
        write(configFile, declared)

        // Opening the project scans repeatedly; not one of those scans may touch the configuration file.
        service.sync()
        service.sync()
        service.sync()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("the theme block must survive untouched", declared, text(configFile))
        assertNotNull(
            "the detection must attach the facet to a site declaring the material theme",
            MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")),
        )
    }

    /**
     * Regression: the detection dropping the facet — because it read a file it could not parse yet, or
     * because the theme really is gone — must never reach the configuration file. Only the user removing the
     * facet in the Project Structure dialog may do that.
     *
     * The removal is provoked the way the detection provokes it: the facet is present while the file says
     * nothing about Material, so the next scan takes it away again.
     */
    fun `test does not write to the file when the detection removes the facet`() {
        val original = "site_name: Handbook\ntheme:\n  name: material\n"
        val configFile = createConfig("handbook/mkdocs.yml", original)
        service.sync()
        assertNotNull(MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))

        write(configFile, "site_name: Handbook\ntheme:\n  name: readthedocs\n")
        service.sync()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNull(
            "the facet must follow the file",
            MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")),
        )
        assertEquals(
            "the theme the file declares must survive the removal of the facet",
            "site_name: Handbook\ntheme:\n  name: readthedocs\n",
            text(configFile),
        )
    }

    /**
     * Use case: a site on another theme. It is an MkDocs site all right, but not a Material one, so the
     * MkDocs facet appears alone.
     */
    fun `test leaves a site on another theme without the facet`() {
        createConfig("handbook/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: readthedocs\n")

        service.sync()

        val module = moduleNamed("Handbook")
        assertNotNull(MkDocsFacet.getInstance(module))
        assertNull("only a material site may carry the facet", MkDocsMaterialFacet.getInstance(module))
    }

    /**
     * Use case: the user switches the theme of an existing site over to Material by hand. The next detection
     * run has to notice the changed file and attach the facet.
     */
    fun `test attaches the facet after the theme was added`() {
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")
        service.sync()
        assertNull(MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))

        write(configFile, "site_name: Handbook\ntheme:\n  name: material\n")
        service.sync()

        assertNotNull(MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))
    }

    /**
     * Use case: the theme is taken out of the configuration file again. The facet stands for nothing then and
     * has to be removed — a facet the file does not back would keep claiming a theme that is gone.
     */
    fun `test removes the facet after the theme was taken out`() {
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")
        service.sync()
        assertNotNull(MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))

        write(configFile, "site_name: Handbook\n")
        service.sync()

        assertNull("the facet must follow the file", MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))
    }

    /**
     * Use case: the whole documentation folder is deleted. The MkDocs facet goes, and the Angular Material
     * facet next to it must not be left behind — a facet whose site is gone would keep the module in an
     * inconsistent state.
     */
    fun `test drops the facet together with the mkdocs facet`() {
        val configFile = createConfig("docs-a/mkdocs.yml", "site_name: Alpha\ntheme:\n  name: material\n")
        service.sync()
        assertNotNull(MkDocsMaterialFacet.getInstance(moduleNamed("Alpha")))

        VfsTestUtil.deleteFile(configFile)
        service.sync()

        assertNull(ModuleManager.getInstance(project).findModuleByName("Alpha"))
        assertTrue(service.getMkDocsModules().isEmpty())
    }

    private fun moduleNamed(name: String): Module {
        val module = ModuleManager.getInstance(project).findModuleByName(name)
        assertNotNull("expected a module named '$name'", module)
        return module!!
    }

    private fun createConfig(relativePath: String, text: String): VirtualFile =
        VfsTestUtil.createFile(getOrCreateProjectBaseDir(), relativePath, text)

    /**
     * Reads the configuration file back through the PSI, which is what the detection reads it through.
     */
    private fun text(configFile: VirtualFile): String =
        MkDocsConfig.yamlFileOf(project, configFile)!!.text


    /**
     * Replaces the content of [file] through the virtual file system and commits the change to the PSI.
     *
     * Both steps matter: writing past the VFS would leave the PSI on the previous content, and a change that
     * is not committed lives in the document only — the detection reads the configuration through the PSI
     * and would still see the old theme. In the running IDE the commit happens on its own before the
     * scheduled scan gets there.
     */
    private fun write(file: VirtualFile, text: String) {
        ApplicationManager.getApplication().runWriteAction { VfsUtil.saveText(file, text) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
