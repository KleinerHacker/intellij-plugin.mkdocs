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

package org.pcsoft.ij.plugin.mkdocs.material.override

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigWriter

/**
 * Creates the template overrides of a *Material for MkDocs* site.
 *
 * Overriding a template of the theme is three steps that have to agree with each other: a directory has to
 * exist, `theme.custom_dir` has to name it, and the file has to lie at exactly the path the original lies at
 * inside the theme. Missing any one of them produces a site that builds and simply ignores the override,
 * which is a frustrating thing to debug — so the action does all three at once.
 *
 * Offered on the root directory of a site using the Material theme, and nowhere else.
 */
class MkDocsMaterialCreateOverrideAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = configFileOf(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val configFile = configFileOf(e) ?: return

        val dialog = MkDocsMaterialCreateOverrideDialog(project, directoryNameOf(project, configFile))
        if (!dialog.showAndGet()) return
        val selected = dialog.selectedOverrides()
        if (selected.isEmpty()) return

        create(project, configFile, dialog.directoryName(), selected)
    }

    /**
     * Writes the selected overrides and points the configuration file at them.
     *
     * Runs as a single undoable command: one *Ctrl+Z* has to take back the directory, the files and the key
     * together, because separately none of them means anything.
     *
     * @param project the project the site belongs to
     * @param configFile the configuration file of the site
     * @param directoryName the name of the override directory, below the site root
     * @param overrides the templates to create
     */
    private fun create(
        project: Project,
        configFile: VirtualFile,
        directoryName: String,
        overrides: List<MkDocsMaterialOverride>,
    ) {
        val siteRoot = configFile.parent ?: return
        var opened: VirtualFile? = null

        WriteCommandAction.writeCommandAction(project)
            .withName(MkDocsBundle.message("material.override.command"))
            .run<RuntimeException> {
                val root = VfsUtil.createDirectoryIfMissing(siteRoot, directoryName) ?: return@run
                for (override in overrides) {
                    val parent = override.path.substringBeforeLast('/', "")
                    val directory =
                        if (parent.isEmpty()) root else VfsUtil.createDirectoryIfMissing(root, parent) ?: continue
                    val existing = directory.findChild(override.fileName)
                    val file = existing ?: directory.createChildData(this, override.fileName)
                    // An override that is already there is the author's, and its content is the whole point
                    // of having created it — only a file this action just made gets the scaffold.
                    if (existing == null) VfsUtil.saveText(file, override.scaffold)
                    if (opened == null) opened = file
                }
                if (MkDocsConfig.readNestedScalar(project, configFile, MkDocsMaterialConfig.KEY_CUSTOM_DIR) == null) {
                    MkDocsConfigWriter.setNestedScalar(
                        project,
                        configFile,
                        MkDocsMaterialConfig.KEY_CUSTOM_DIR,
                        directoryName,
                    )
                }
            }

        opened?.let { FileEditorManager.getInstance(project).openFile(it, true) }
    }

    /**
     * Returns the name the override directory should carry.
     *
     * The one the configuration file already names, so a site that has overrides gets more of them in the
     * same place, and the name the theme's own documentation uses otherwise.
     *
     * @param project the project the site belongs to
     * @param configFile the configuration file of the site
     */
    private fun directoryNameOf(project: Project, configFile: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.readNestedScalar(project, configFile, MkDocsMaterialConfig.KEY_CUSTOM_DIR) }
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_DIRECTORY

    /**
     * Returns the configuration file of the Material site the action was invoked on, or `null` if it was
     * invoked somewhere else.
     *
     * @param e what the action was invoked with
     */
    private fun configFileOf(e: AnActionEvent): VirtualFile? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        val directory = if (file.isDirectory) file else file.parent ?: return null
        val configFile = directory.children
            .firstOrNull { !it.isDirectory && MkDocsProject.isConfigFile(it.name) }
            ?: return null
        return if (runReadActionBlocking { MkDocsConfig.isMaterialTheme(project, configFile) }) configFile else null
    }

    private companion object {

        /** The name the documentation of the theme gives the override directory. */
        const val DEFAULT_DIRECTORY = "overrides"
    }
}
