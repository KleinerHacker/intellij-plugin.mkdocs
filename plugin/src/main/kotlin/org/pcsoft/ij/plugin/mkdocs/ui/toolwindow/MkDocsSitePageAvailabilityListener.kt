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

package org.pcsoft.ij.plugin.mkdocs.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsSitesListener

/**
 * Shows and hides the *Site Page* tool window as sites appear and disappear, and keeps its tabs in line.
 *
 * A project can gain its first MkDocs site long after it was opened — through a checkout, or through the
 * *New MkDocs Site* wizard. Deciding the availability once at start up would leave the tool window missing
 * until the next restart, so it is decided again after every scan.
 *
 * @param project the project this listener watches
 */
class MkDocsSitePageAvailabilityListener(private val project: Project) : MkDocsSitesListener {

    override fun sitesChanged() {
        // The scan runs on a pooled thread; everything below touches Swing.
        ApplicationManager.getApplication().invokeLater(::update, ModalityState.any())
    }

    /**
     * Brings the availability and the tabs of the tool window up to date.
     */
    private fun update() {
        if (project.isDisposed) return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(MkDocsSitePageToolWindowFactory.ID)
            ?: return

        val available = project.service<MkDocsModuleService>().getMkDocsModules().isNotEmpty()
        toolWindow.setAvailable(available)
        if (!available) return

        // Only an already built tool window is brought up to date — building it here would open it for a
        // user who never asked for it.
        if (toolWindow.contentManagerIfCreated == null) return
        MkDocsSitePageToolWindowFactory.syncContents(project, toolWindow)
    }
}
