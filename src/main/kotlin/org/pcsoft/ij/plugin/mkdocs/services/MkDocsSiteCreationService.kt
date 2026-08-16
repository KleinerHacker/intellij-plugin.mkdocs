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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplateError
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfigWriter

/**
 * Creates the file structure of a new MkDocs site.
 *
 * What is written is deliberately the bare minimum MkDocs needs — a `site_name`, a documentation directory
 * and a start page. Everything beyond that is left to the user or to a
 * [org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature].
 *
 * @param project the project the site is created in
 */
@Service(Service.Level.PROJECT)
class MkDocsSiteCreationService(private val project: Project) {

    companion object {

        /**
         * Returns the service instance for [project].
         *
         * @param project the project whose service is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsSiteCreationService = project.service()
    }

    /**
     * Creates the site described by [template].
     *
     * Runs as a single undoable command: the structure is written, the module detection is re-run so the new
     * site immediately becomes an MkDocs module, the selected features are applied, and the configuration
     * file is opened in the editor.
     *
     * Must be called on the EDT.
     *
     * @param template what to create
     * @return the created site
     * @throws IllegalArgumentException if [template] does not pass [MkDocsSiteTemplate.validate]
     */
    fun create(template: MkDocsSiteTemplate): MkDocsSite {
        template.validate()?.let { error ->
            throw IllegalArgumentException("Cannot create MkDocs site: ${error.name}")
        }

        val site = WriteCommandAction.writeCommandAction(project)
            .withName(MkDocsBundle.message("create.site.command"))
            .compute<MkDocsSite, RuntimeException> { write(template) }

        MkDocsModuleService.getInstance(project).sync()
        rememberDirectoryNames(template, site)

        WriteCommandAction.runWriteCommandAction(project) {
            for (feature in template.features) {
                feature.apply(project, site)
            }
        }

        FileEditorManager.getInstance(project).openFile(site.configFile, true)
        return site
    }

    /**
     * Writes the structure of [template] to disk.
     *
     * Must be called inside a write action.
     */
    private fun write(template: MkDocsSiteTemplate): MkDocsSite {
        // The site directory is usually the one the wizard appended the site name to, so it rarely exists
        // yet. createDirectories creates every missing level, not just the last one.
        val root = VfsUtil.createDirectories(template.rootPath.toString())
        val configFile = root.createChildData(this, MkDocsProject.CONFIG_FILE_NAMES.first())
        VfsUtil.saveText(configFile, buildConfigText(template))

        val docsDir = VfsUtil.createDirectoryIfMissing(root, template.docsDirName)
            ?: error("Cannot create documentation directory '${template.docsDirName}'")
        val indexFile = docsDir.createChildData(this, "index.md")
        VfsUtil.saveText(indexFile, buildIndexText(template))

        VfsUtil.createDirectoryIfMissing(docsDir, template.assetsDirName)
            ?: error("Cannot create assets directory '${template.assetsDirName}'")

        VfsUtil.createDirectoryIfMissing(docsDir, template.stylesheetsDirName)
            ?: error("Cannot create stylesheets directory '${template.stylesheetsDirName}'")

        return MkDocsSite(root = root, configFile = configFile, siteName = template.siteName)
    }

    /**
     * Builds the content of `mkdocs.yml`.
     *
     * `docs_dir` and `site_dir` are written only when they differ from what MkDocs assumes anyway — a
     * configuration file repeating the defaults tells the reader nothing.
     */
    private fun buildConfigText(template: MkDocsSiteTemplate): String = buildString {
        append("${MkDocsConfig.KEY_SITE_NAME}: ${MkDocsConfigWriter.yamlScalar(template.siteName)}\n")
        appendOptional(MkDocsConfig.KEY_SITE_AUTHOR, template.siteAuthor)
        appendOptional(MkDocsConfig.KEY_SITE_DESCRIPTION, template.siteDescription)
        appendOptional(MkDocsConfig.KEY_SITE_URL, template.siteUrl)
        appendOptional(MkDocsConfig.KEY_REPO_NAME, template.repoName)
        appendOptional(MkDocsConfig.KEY_REPO_URL, template.repoUrl)
        appendOptional(MkDocsConfig.KEY_COPYRIGHT, template.copyright)
        if (template.docsDirName != MkDocsSiteTemplate.DEFAULT_DOCS_DIR) {
            append("${MkDocsConfig.KEY_DOCS_DIR}: ${MkDocsConfigWriter.yamlScalar(template.docsDirName)}\n")
        }
        if (template.siteDirName != MkDocsSiteTemplate.DEFAULT_SITE_DIR) {
            append("${MkDocsConfig.KEY_SITE_DIR}: ${MkDocsConfigWriter.yamlScalar(template.siteDirName)}\n")
        }
    }

    /**
     * Appends `key: value` unless [value] is empty.
     *
     * Every key beyond `site_name` is optional, and MkDocs treats a key written empty differently from a key
     * that is absent — an empty `repo_url` renders an empty link.
     *
     * @param key the configuration key to write
     * @param value the value as the user entered it
     */
    private fun StringBuilder.appendOptional(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        append("$key: ${MkDocsConfigWriter.yamlScalar(trimmed)}\n")
    }

    /** Builds the content of the start page. */
    private fun buildIndexText(template: MkDocsSiteTemplate): String =
        "# ${template.siteName}\n"

    /**
     * Stores the chosen assets and stylesheets directories in the facet the detection just created.
     *
     * MkDocs has a configuration key for neither of them, so the facet is the only place the names can live.
     * A name left at its default is not written: the facet already starts out with it, and writing it would
     * only add noise to the `.iml` file.
     */
    private fun rememberDirectoryNames(template: MkDocsSiteTemplate, site: MkDocsSite) {
        val assetsDiffers = template.assetsDirName != MkDocsSiteTemplate.DEFAULT_ASSETS_DIR
        val stylesheetsDiffers = template.stylesheetsDirName != MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR
        if (!assetsDiffers && !stylesheetsDiffers) return

        val module = ModuleUtilCore.findModuleForFile(site.root, project) ?: return
        val facet = MkDocsFacet.getInstance(module) ?: return
        if (assetsDiffers) facet.configuration.assetsDirName = template.assetsDirName
        if (stylesheetsDiffers) facet.configuration.stylesheetsDirName = template.stylesheetsDirName
    }

    /**
     * Returns the message for [error], ready to be shown to the user.
     *
     * @param error the reason a template was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String = MkDocsBundle.message(error.messageKey)

    /** The candidate directory a site would be created in, or `null` if [file] is unusable as one. */
    fun targetDirectoryOf(file: VirtualFile?): VirtualFile? = when {
        file == null || !file.isValid -> null
        file.isDirectory -> file
        else -> file.parent?.takeIf { it.isValid }
    }
}
