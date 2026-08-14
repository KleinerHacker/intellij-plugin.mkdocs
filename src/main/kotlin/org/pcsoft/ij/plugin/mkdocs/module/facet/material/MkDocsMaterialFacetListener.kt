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
import com.intellij.facet.FacetManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetEditorTab
import org.pcsoft.ij.plugin.mkdocs.schema.MkDocsMaterialSchemaCache
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigWriter

/**
 * Carries a hand-made change of the Angular Material facet into the MkDocs configuration file.
 *
 * The facet can be added and removed in the Project Structure dialog, and what it stands for lives in
 * `mkdocs.yml`: adding it writes `theme.name: material`, removing it takes the `theme` key out again. Without
 * this the next detection run would simply undo what the user did in the dialog.
 *
 * The other direction — an edited configuration file changing the facet — is handled by
 * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService]. Both directions meet in the middle: the file is
 * only written when it does not already say what the facet says, so a facet the detection itself created
 * writes nothing.
 *
 * Independently of who caused the change, the facet decides which JSON schema the configuration file is
 * validated against, so every appearance and disappearance of it invalidates the cached schema assignment.
 *
 * Registered in `plugin.xml` under `projectListeners`.
 *
 * @param project the project the facets belong to
 */
class MkDocsMaterialFacetListener(private val project: Project) : FacetManagerListener {

    override fun facetAdded(facet: Facet<*>) {
        invalidateSchema(facet)
        if (!isUserChange(facet)) return
        applyToConfigFile(facet.module, MkDocsBundle.message("facet.angularMaterial.command.add")) { configFile ->
            if (MkDocsConfig.isMaterialTheme(project, configFile)) return@applyToConfigFile
            MkDocsConfigWriter.setThemeName(project, configFile, MkDocsConfig.THEME_MATERIAL)
        }
    }

    override fun facetRemoved(facet: Facet<*>) {
        invalidateSchema(facet)
        if (!isUserChange(facet)) return
        applyToConfigFile(facet.module, MkDocsBundle.message("facet.angularMaterial.command.remove")) { configFile ->
            if (!MkDocsConfig.isMaterialTheme(project, configFile)) return@applyToConfigFile
            MkDocsConfigWriter.removeTheme(project, configFile)
        }
    }

    /**
     * Drops the cached schema assignment when [facet] is an Angular Material facet.
     *
     * Unlike the write-back below this does not care who caused the change: a facet the detection attached
     * changes which schema the file has to be validated against just as much as one added by hand.
     *
     * @param facet the facet the platform announced
     */
    private fun invalidateSchema(facet: Facet<*>) {
        if (facet !is MkDocsMaterialFacet) return
        MkDocsMaterialSchemaCache.invalidate(project)
    }

    /**
     * Tells whether [facet] is an Angular Material facet whose appearance or disappearance is news.
     *
     * Everything else has to be ignored, and the removal side is where it matters: writing to the
     * configuration file is destructive, so the only removal allowed to reach it is one a user performed in
     * the Project Structure dialog.
     *
     * Three sources are turned away. A facet of another type is none of this listener's business. A facet the
     * detection itself is adding or removing only mirrors the file it was read from — carrying it back would
     * let a misread file delete the very key it declares. And a project or module on its way out takes its
     * facets with it, which says nothing about the site the files describe.
     *
     * @param facet the facet the platform announced
     */
    private fun isUserChange(facet: Facet<*>): Boolean {
        if (facet !is MkDocsMaterialFacet) return false
        if (project.isDisposed || facet.module.isDisposed) return false
        if (MkDocsModuleService.getInstance(project).isApplyingFacets) return false
        return true
    }

    /**
     * Runs [change] on the configuration file of [module], inside an undoable command of its own.
     *
     * The change is scheduled rather than run right away: the listener fires while the facet model is being
     * committed, and writing a file from inside that commit is not allowed. A module without an MkDocs site
     * behind it is left alone — there is no file the change could go into.
     *
     * @param module the module the facet belongs to
     * @param commandName name of the undoable command the change is wrapped in
     * @param change the change to apply to the configuration file
     */
    private fun applyToConfigFile(module: Module, commandName: String, change: (VirtualFile) -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || module.isDisposed) return@invokeLater
            val configFile = configFileOf(module) ?: return@invokeLater
            if (!configFile.isValid) return@invokeLater
            WriteCommandAction.runWriteCommandAction(project, commandName, null, { change(configFile) })
        }, project.disposed)
    }

    /**
     * Returns the MkDocs configuration file of [module], or `null` if the module holds no MkDocs site.
     *
     * @param module the module the facet belongs to
     */
    private fun configFileOf(module: Module): VirtualFile? {
        val mkDocsFacet = runReadActionBlocking { MkDocsFacet.getInstance(module) } ?: return null
        return MkDocsFacetEditorTab.findConfigFile(module, mkDocsFacet.configuration.configFilePath)
    }
}
