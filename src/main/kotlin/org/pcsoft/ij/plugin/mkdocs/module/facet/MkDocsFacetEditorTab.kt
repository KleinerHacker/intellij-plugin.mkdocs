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

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetEditorValidator
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import javax.swing.JComponent

/**
 * Project Structure tab of the MkDocs facet.
 *
 * Everything shown here is derived from the MkDocs configuration file, so the tab is purely informational:
 * the values are edited by editing `mkdocs.yml`, not here.
 *
 * The tab is also the last line of defence against a facet that has no site behind it. Such a facet can no
 * longer be added through the UI (see [MkDocsFacetType.isSuitableModuleType]), but it can still reach the
 * module model through a hand-edited or merged `.iml` file. Instead of showing two empty labels, the tab then
 * reports a validation error.
 *
 * @param configuration the facet configuration whose values are displayed
 * @param editorContext the context of the edited facet, used to locate the configuration file
 * @param validatorsManager the manager the configuration-file validator is registered with
 */
class MkDocsFacetEditorTab(
    private val configuration: MkDocsFacetConfiguration,
    private val editorContext: FacetEditorContext,
    validatorsManager: FacetValidatorsManager,
) : FacetEditorTab() {

    companion object {

        /**
         * Locates the MkDocs configuration file backing a facet.
         *
         * The content roots of [module] are searched recursively, because the site root is not necessarily
         * the module root — a site commonly lives in a `docs/` subdirectory of an otherwise unrelated module.
         * [configFilePath] is stored relative to the *site* root and therefore usually holds nothing but the
         * bare file name; it is used to prefer the configured spelling, and any MkDocs configuration file
         * counts if that spelling is not found. The directories skipped are the same ones the detection skips
         * ([MkDocsModuleService.IGNORED_DIRECTORIES]), so a configuration file inside a build output does not
         * make an empty facet look valid.
         *
         * @param module the module carrying the facet
         * @param configFilePath the configured path, relative to the site root, possibly empty
         * @return the configuration file, or `null` if the module has no MkDocs site
         */
        @JvmStatic
        fun findConfigFile(module: Module, configFilePath: String): VirtualFile? {
            if (module.isDisposed) return null
            val preferredName = configFilePath.substringAfterLast('/').substringAfterLast('\\')
            return runReadActionBlocking {
                if (module.isDisposed) return@runReadActionBlocking null
                var fallback: VirtualFile? = null
                for (root in ModuleRootManager.getInstance(module).contentRoots) {
                    if (!root.isValid || !root.isDirectory) continue
                    var preferred: VirtualFile? = null
                    VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            if (preferred != null) return false
                            if (file.isDirectory) {
                                return file == root || file.name !in MkDocsModuleService.IGNORED_DIRECTORIES
                            }
                            if (!MkDocsProject.isConfigFile(file.name)) return true
                            if (preferredName.isNotEmpty() && file.name.equals(preferredName, ignoreCase = true)) {
                                preferred = file
                            } else if (fallback == null) {
                                fallback = file
                            }
                            return true
                        }
                    })
                    preferred?.let { return@runReadActionBlocking it }
                }
                fallback
            }
        }
    }

    init {
        validatorsManager.registerValidator(object : FacetEditorValidator() {
            override fun check(): ValidationResult = validate()
        })
    }

    override fun getDisplayName(): String = MkDocsBundle.message("facet.mkdocs.tab.title")

    override fun createComponent(): JComponent = panel {
        if (validate().isOk) {
            row(MkDocsBundle.message("facet.mkdocs.field.siteName")) {
                label(configuration.siteName)
            }
            row(MkDocsBundle.message("facet.mkdocs.field.configFile")) {
                label(configuration.configFilePath)
            }
            row(MkDocsBundle.message("facet.mkdocs.field.docsDir")) {
                label(docsDirName())
            }
            row(MkDocsBundle.message("facet.mkdocs.field.assetsDir")) {
                label(configuration.assetsDirName)
            }
            row {
                comment(MkDocsBundle.message("facet.mkdocs.hint"))
            }
        } else {
            row {
                label(MkDocsBundle.message("facet.mkdocs.error.noConfig"))
            }
            row {
                comment(MkDocsBundle.message("facet.mkdocs.error.noConfig.hint"))
            }
        }
    }

    /** The tab never edits anything, so there is nothing that could become modified. */
    override fun isModified(): Boolean = false

    /**
     * Returns the documentation directory of the site, read from `docs_dir` of its configuration file.
     *
     * Falls back to the MkDocs default when the file cannot be read — the tab is informational, it must not
     * fail because a configuration file is momentarily half-written.
     */
    private fun docsDirName(): String {
        val configFile = findConfigFile(editorContext.module, configuration.configFilePath)
            ?: return MkDocsProject.DEFAULT_DOCS_DIR
        return runReadActionBlocking { MkDocsConfig.resolveDocsDir(editorContext.project, configFile) }
    }

    /**
     * Reports an error while the module carries the facet without holding an MkDocs configuration file.
     */
    private fun validate(): ValidationResult =
        if (findConfigFile(editorContext.module, configuration.configFilePath) != null) {
            ValidationResult.OK
        } else {
            ValidationResult(MkDocsBundle.message("facet.mkdocs.error.noConfig"))
        }
}
