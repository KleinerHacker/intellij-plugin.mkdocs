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

import com.intellij.facet.ui.*
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsSiteFiles
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsDirectoryLayout
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsDirectoryService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Project Structure tab of the MkDocs facet.
 *
 * The name of the site and its configuration file are derived from `mkdocs.yml` and therefore only shown. The
 * technical directories can be edited: applying the tab renames the directories on disk through the platform
 * rename refactoring — which rewrites every reference of the configuration file and of the pages — and writes
 * `docs_dir` and `site_dir` back into `mkdocs.yml`. The work itself is done by [MkDocsDirectoryService].
 *
 * The tab is also the last line of defence against a facet that has no site behind it. Such a facet can no
 * longer be added through the UI (see [MkDocsFacetType.isSuitableModuleType]), but it can still reach the
 * module model through a hand-edited or merged `.iml` file. Instead of showing empty fields, the tab then
 * reports a validation error.
 *
 * @param configuration the facet configuration whose values are displayed and edited
 * @param editorContext the context of the edited facet, used to locate the configuration file
 * @param validatorsManager the manager the validator is registered with
 */
class MkDocsFacetEditorTab(
    private val configuration: MkDocsFacetConfiguration,
    private val editorContext: FacetEditorContext,
    private val validatorsManager: FacetValidatorsManager,
) : FacetEditorTab() {

    /** The name of the site, held by `site_name`. */
    private val siteNameField = JBTextField()

    /** The documentation directory of the site, held by `docs_dir`. */
    private val docsDirField = JBTextField()

    /** The build output directory of the site, held by `site_dir`. */
    private val siteDirField = JBTextField()

    /** The assets directory of the site, held by the facet alone. */
    private val assetsDirField = JBTextField()

    /** The stylesheets directory of the site, held by the facet alone. */
    private val stylesheetsDirField = JBTextField()

    /** The layout the tab was last filled from; what an edit is compared against. */
    private var shownLayout: MkDocsDirectoryLayout = emptyLayout()

    /** The site name the tab was last filled from. */
    private var shownSiteName: String = ""

    init {
        validatorsManager.registerValidator(object : FacetEditorValidator() {
            override fun check(): ValidationResult = validate()
        })
        for (field in listOf(siteNameField, docsDirField, siteDirField, assetsDirField, stylesheetsDirField)) {
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = validatorsManager.validate()
                override fun removeUpdate(e: DocumentEvent) = validatorsManager.validate()
                override fun changedUpdate(e: DocumentEvent) = validatorsManager.validate()
            })
        }
    }

    override fun getDisplayName(): String = MkDocsBundle.message("facet.mkdocs.tab.title")

    override fun createComponent(): JComponent {
        reset()
        return panel {
            if (configFile() != null) {
                row(MkDocsBundle.message("facet.mkdocs.field.siteName")) {
                    cell(siteNameField).align(AlignX.FILL)
                }
                row(MkDocsBundle.message("facet.mkdocs.field.configFile")) {
                    label(configuration.configFilePath)
                }
                row(MkDocsBundle.message("facet.mkdocs.field.docsDir")) {
                    cell(docsDirField).align(AlignX.FILL)
                }
                row(MkDocsBundle.message("facet.mkdocs.field.siteDir")) {
                    cell(siteDirField).align(AlignX.FILL)
                }
                row(MkDocsBundle.message("facet.mkdocs.field.assetsDir")) {
                    cell(assetsDirField).align(AlignX.FILL)
                }
                row(MkDocsBundle.message("facet.mkdocs.field.stylesheetsDir")) {
                    cell(stylesheetsDirField).align(AlignX.FILL)
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
    }

    override fun isModified(): Boolean = enteredLayout() != shownLayout || enteredSiteName() != shownSiteName

    override fun reset() {
        val configFile = configFile()
        shownLayout = configFile
            ?.let { MkDocsDirectoryService.getInstance(editorContext.project).currentLayout(module(), it) }
            ?: emptyLayout()
        shownSiteName = configFile
            ?.let { runReadActionBlocking { MkDocsConfig.resolveSiteName(editorContext.project, it) } }
            ?: configuration.siteName
        siteNameField.text = shownSiteName
        docsDirField.text = shownLayout.docsDirName
        siteDirField.text = shownLayout.siteDirName
        assetsDirField.text = shownLayout.assetsDirName
        stylesheetsDirField.text = shownLayout.stylesheetsDirName
    }

    /**
     * Writes what the user changed back into the site.
     *
     * The site name goes into `site_name`, which is also what the module is called, so the name change reaches
     * the project view as well. The facet keeps the assets and the stylesheets directory, because MkDocs has no
     * key for either; the two keys it does have are written into the configuration file by the service.
     */
    override fun apply() {
        val configFile = configFile() ?: return
        val service = MkDocsDirectoryService.getInstance(editorContext.project)

        val siteName = enteredSiteName()
        if (siteName != shownSiteName) {
            service.renameSite(configFile, siteName)
            configuration.siteName = siteName
            shownSiteName = siteName
        }

        val target = enteredLayout()
        if (target == shownLayout) return

        service.applyLayout(module(), configFile, shownLayout, target)

        configuration.assetsDirName = target.assetsDirName
        configuration.stylesheetsDirName = target.stylesheetsDirName
        shownLayout = target
    }

    /** The site name as it is currently entered. */
    private fun enteredSiteName(): String = siteNameField.text.trim()

    /** The layout as it is currently entered in the fields. */
    private fun enteredLayout(): MkDocsDirectoryLayout = MkDocsDirectoryLayout(
        docsDirName = docsDirField.text.trim(),
        siteDirName = siteDirField.text.trim(),
        assetsDirName = assetsDirField.text.trim(),
        stylesheetsDirName = stylesheetsDirField.text.trim(),
    )

    /** The layout shown for a facet without a site behind it. */
    private fun emptyLayout(): MkDocsDirectoryLayout = MkDocsDirectoryLayout(
        docsDirName = MkDocsSiteTemplate.DEFAULT_DOCS_DIR,
        siteDirName = MkDocsSiteTemplate.DEFAULT_SITE_DIR,
        assetsDirName = configuration.assetsDirName,
        stylesheetsDirName = configuration.stylesheetsDirName,
    )

    /** The module the edited facet belongs to, or `null` while it is already gone. */
    private fun module(): Module? = editorContext.module.takeIf { !it.isDisposed }

    /** The configuration file of the site, or `null` if the module holds none. */
    private fun configFile(): VirtualFile? =
        module()?.let { MkDocsSiteFiles.findConfigFile(it, configuration.configFilePath) }

    /**
     * Reports everything that would keep the entered layout from being applied.
     *
     * Beyond the names themselves two situations are refused: a directory that is to be renamed but is not
     * there, and a name already taken by something else inside the site. Neither could be carried out, and
     * both are far easier to explain here than after half of the rename has happened.
     */
    private fun validate(): ValidationResult {
        val configFile = configFile() ?: return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.noConfig"))
        val target = enteredLayout()

        if (enteredSiteName().isEmpty()) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.emptySiteName"))
        }
        if (!MkDocsProject.isValidDirectoryName(target.docsDirName)) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.invalidDocsDir"))
        }
        if (!MkDocsProject.isValidSiteDirName(target.siteDirName)) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.invalidSiteDir"))
        }
        if (!MkDocsProject.isValidDirectoryName(target.assetsDirName)) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.invalidAssetsDir"))
        }
        if (!MkDocsProject.isValidDirectoryName(target.stylesheetsDirName)) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.invalidStylesheetsDir"))
        }
        if (target.assetsDirName == target.stylesheetsDirName) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.sameDir"))
        }

        val siteRoot = configFile.parent?.takeIf { it.isValid && it.isDirectory } ?: return ValidationResult.OK
        val docsDir = VfsUtilCore.findRelativeFile(shownLayout.docsDirName, siteRoot)?.takeIf { it.isDirectory }

        checkRename(siteRoot, shownLayout.docsDirName, target.docsDirName)?.let { return it }
        if (docsDir != null) {
            checkRename(docsDir, shownLayout.assetsDirName, target.assetsDirName)?.let { return it }
            checkRename(docsDir, shownLayout.stylesheetsDirName, target.stylesheetsDirName)?.let { return it }
        }
        return ValidationResult.OK
    }

    /**
     * Returns the reason renaming [currentName] to [newName] inside [parent] would fail, or `null` if it works.
     *
     * @param parent the directory holding the directory to rename
     * @param currentName the name the directory carries now
     * @param newName the name it should carry
     */
    private fun checkRename(parent: VirtualFile, currentName: String, newName: String): ValidationResult? {
        if (currentName == newName) return null
        if (parent.findChild(currentName)?.isDirectory != true) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.missingSource", currentName))
        }
        if (parent.findChild(newName) != null) {
            return ValidationResult(MkDocsBundle.message("facet.mkdocs.error.targetExists", newName))
        }
        return null
    }
}
