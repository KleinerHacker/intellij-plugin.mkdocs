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

package org.pcsoft.ij.plugin.mkdocs.material.ui

import com.intellij.facet.FacetManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacet
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator

/**
 * Reads the installed *Material for MkDocs* again, on request.
 *
 * Nothing re-checks an installation on its own: it does not change while the IDE runs, and re-reading its
 * file listing per completion popup is what this feature was made slow by. This is the way to say that it
 * changed after all — a package installed next to a running IDE, an environment rebuilt, a directory
 * replaced — and it is the same work the button of the settings page and the entry in the completion popup
 * trigger.
 *
 * Offered only where there is something to reload: a project carrying at least one site on the Material
 * theme, which is what the facet of the feature marks.
 */
class MkDocsMaterialReloadAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        event.presentation.isEnabledAndVisible = project != null && hasMaterialSite(project)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        MkDocsMaterialIconLocator.reload(project)
    }

    /**
     * Returns whether [project] holds a site rendered with the Material theme.
     *
     * @param project the project being looked at
     */
    private fun hasMaterialSite(project: Project): Boolean =
        ModuleManager.getInstance(project).modules.any {
            FacetManager.getInstance(it).getFacetByType(MkDocsMaterialFacet.ID) != null
        }
}
