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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsSiteCreationService

/**
 * Creates a new MkDocs site through a wizard.
 *
 * Registered in the platform's *New* group, so it appears both in the project view context menu under *New*
 * and in *File → New*.
 *
 * Text, description and icon all come from the registration in `plugin.xml`. Passing any of them to a
 * constructor of [AnAction] would build the action's presentation while the action is being instantiated,
 * which the platform reports — and it would duplicate what the descriptor already says.
 */
class MkDocsCreateSiteAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = MkDocsSiteCreationService.getInstance(project)
        val directory = service.targetDirectoryOf(e.getData(CommonDataKeys.VIRTUAL_FILE))
            ?: project.guessProjectDir()
            ?: return

        val wizard = MkDocsCreateSiteWizard(project, directory)
        if (!wizard.showAndGet()) return

        val template = wizard.template ?: return
        template.validate()?.let { error ->
            showError(project, wizard.messageFor(error))
            return
        }
        runCatching { service.create(template) }
            .onFailure { failure -> showError(project, failure.message ?: failure.toString()) }
    }

    /**
     * Reports a failed creation to the user.
     *
     * @param project the project the wizard ran in
     * @param message what went wrong
     */
    private fun showError(project: Project, message: String) {
        Messages.showErrorDialog(project, message, MkDocsBundle.message("create.site.title"))
    }

    /** The directory a site would be created in for [file], exposed for tests. */
    internal fun targetDirectory(project: Project, file: VirtualFile?): VirtualFile? =
        MkDocsSiteCreationService.getInstance(project).targetDirectoryOf(file) ?: project.guessProjectDir()
}
