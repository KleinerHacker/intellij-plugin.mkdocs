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

package org.pcsoft.ij.plugin.mkdocs.module

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Re-runs the MkDocs module detection whenever the virtual file system changes in a way that could affect
 * it.
 *
 * The filter is deliberately generous — a moved or renamed directory may carry a configuration file with it,
 * and that is not visible from the event alone. Over-triggering costs nothing because
 * [MkDocsModuleService.scheduleSync] merges bursts into a single scan.
 */
class MkDocsVfsListener : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (events.none(::isRelevant)) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                for (project in ProjectManager.getInstance().openProjects) {
                    if (project.isDisposed) continue
                    MkDocsModuleService.getInstance(project).scheduleSync()
                }
            }
        }
    }

    /**
     * Decides whether [event] can change the set of MkDocs sites.
     *
     * @param event a single pending VFS event
     * @return `true` for directories and for MkDocs configuration files
     */
    private fun isRelevant(event: VFileEvent): Boolean = when (event) {
        is VFileCreateEvent -> event.isDirectory || MkDocsProject.isConfigFile(event.childName)
        is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME &&
                (event.file.isDirectory ||
                        MkDocsProject.isConfigFile(event.oldValue.toString()) ||
                        MkDocsProject.isConfigFile(event.newValue.toString()))

        else -> event.file?.let { it.isDirectory || MkDocsProject.isConfigFile(it.name) } == true
    }
}
