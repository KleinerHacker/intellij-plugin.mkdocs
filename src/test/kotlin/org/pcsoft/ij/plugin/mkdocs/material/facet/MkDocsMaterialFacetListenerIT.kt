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

package org.pcsoft.ij.plugin.mkdocs.material.facet

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the direction the detection does not cover: a facet added or removed by hand in the Project
 * Structure dialog has to be carried back into `mkdocs.yml`, otherwise the next detection run would simply
 * undo what the user did.
 */
class MkDocsMaterialFacetListenerIT : HeavyPlatformTestCase() {

    /**
     * Use case: the user adds the Angular Material facet in the Project Structure dialog on a site that is
     * still on the built-in theme. The site has to end up declaring the Material theme, so what the dialog
     * shows and what MkDocs renders agree.
     */
    fun `test writes the theme when the facet is added`() {
        val configFile = createSite("site_name: Handbook\n")
        val module = moduleNamed("Handbook")

        addMaterialFacet(module)

        assertTrue("adding the facet must write the theme", isMaterialTheme(configFile))
        assertTrue("the rest of the file must survive", text(configFile).contains("site_name: Handbook"))
    }

    /**
     * Use case: the user removes the facet again. The theme it stands for has to go out of the configuration
     * file with it, so MkDocs falls back to its built-in theme.
     */
    fun `test removes the theme when the facet is removed`() {
        val configFile = createSite("site_name: Handbook\ntheme:\n  name: material\n")
        val module = moduleNamed("Handbook")
        assertNotNull("the detection must have attached the facet first", MkDocsMaterialFacet.getInstance(module))

        removeMaterialFacet(module)

        assertFalse("removing the facet must take the theme out", isMaterialTheme(configFile))
        assertTrue("the rest of the file must survive", text(configFile).contains("site_name: Handbook"))
    }

    /**
     * Use case: the facet was created by the detection, because the file already said `material`. Writing the
     * same value back would touch the file for nothing and mark it as modified — the listener has to notice
     * that the file already says what the facet says and leave it alone.
     */
    fun `test leaves the file alone when it already declares the theme`() {
        val original = "site_name: Handbook\ntheme:\n  name: material\n  highlightjs: true\n"
        val configFile = createSite(original)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNotNull(MkDocsMaterialFacet.getInstance(moduleNamed("Handbook")))
        assertEquals("a file already declaring the theme must not be rewritten", original, text(configFile))
    }

    /**
     * Adds the Angular Material facet the way the Project Structure dialog does, then lets the events the
     * commit posts run — the listener writes the file later, not from inside the commit.
     */
    private fun addMaterialFacet(module: Module) {
        val facetManager = FacetManager.getInstance(module)
        assertNotNull("the module must carry the MkDocs facet first", MkDocsFacet.getInstance(module))

        ApplicationManager.getApplication().runWriteAction {
            val model = facetManager.createModifiableModel()
            val facetType = MkDocsMaterialFacet.facetType
            model.addFacet(
                facetType.createFacet(
                    module,
                    facetType.defaultFacetName,
                    facetType.createDefaultConfiguration(),
                    null,
                )
            )
            model.commit()
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /**
     * Removes the Angular Material facet the way the Project Structure dialog does.
     */
    private fun removeMaterialFacet(module: Module) {
        val facetManager = FacetManager.getInstance(module)
        val facet = MkDocsMaterialFacet.getInstance(module)!!

        ApplicationManager.getApplication().runWriteAction {
            val model = facetManager.createModifiableModel()
            model.removeFacet(facet)
            model.commit()
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /**
     * Writes an `mkdocs.yml` with [text] and lets the detection pick it up, so the module and its MkDocs
     * facet exist before the test touches the facets itself.
     */
    private fun createSite(text: String): VirtualFile {
        val configFile = VfsTestUtil.createFile(getOrCreateProjectBaseDir(), "handbook/mkdocs.yml", text)
        MkDocsModuleService.getInstance(project).sync()
        return configFile
    }

    private fun moduleNamed(name: String): Module {
        val module = ModuleManager.getInstance(project).findModuleByName(name)
        assertNotNull("expected a module named '$name'", module)
        return module!!
    }

    private fun isMaterialTheme(configFile: VirtualFile): Boolean =
        MkDocsMaterialConfig.isMaterialTheme(project, configFile)

    private fun text(configFile: VirtualFile): String =
        MkDocsConfig.yamlFileOf(project, configFile)!!.text
}
