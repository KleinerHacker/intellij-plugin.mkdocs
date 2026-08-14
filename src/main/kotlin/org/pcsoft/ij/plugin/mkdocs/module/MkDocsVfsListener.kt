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
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsSitesListener

/**
 * Re-runs the MkDocs module detection whenever the virtual file system changes in a way that could affect
 * it.
 *
 * The filter is deliberately generous — a moved or renamed directory may carry a configuration file with it,
 * and that is not visible from the event alone. Over-triggering costs nothing because
 * [MkDocsModuleService.scheduleSync] merges bursts into a single scan.
 *
 * Changes to the pages of a site are watched as well, but they do not start a scan: a Markdown file cannot
 * turn a directory into a site. They only change what the pages are called, which is reported through
 * [MkDocsSitesListener.pagesChanged].
 */
class MkDocsVfsListener : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val sitesAffected = events.any(::isRelevant)
        val pagesAffected = events.any(::isPage)
        val iconsAffected = events.any(::isInstalledPackage)
        if (iconsAffected) {
            for (project in ProjectManager.getInstance().openProjects) {
                if (!project.isDisposed) MkDocsMaterialIconIndex.getInstance(project).invalidate()
            }
        }
        if (!sitesAffected && !pagesAffected) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                for (project in ProjectManager.getInstance().openProjects) {
                    if (project.isDisposed) continue
                    if (sitesAffected) {
                        // The scan reports itself once it is through, so no separate page notification.
                        MkDocsModuleService.getInstance(project).scheduleSync()
                    } else {
                        project.messageBus.syncPublisher(MkDocsSitesListener.TOPIC).pagesChanged()
                    }
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

    /**
     * Decides whether [event] concerns a page of a site.
     *
     * @param event a single pending VFS event
     * @return `true` for Markdown files
     */
    private fun isPage(event: VFileEvent): Boolean = when (event) {
        is VFileCreateEvent -> !event.isDirectory && MkDocsProject.isPageFile(event.childName)
        is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME &&
                (MkDocsProject.isPageFile(event.oldValue.toString()) ||
                        MkDocsProject.isPageFile(event.newValue.toString()))

        else -> event.file?.let { MkDocsProject.isPageFile(it.name) } == true
    }

    /**
     * Decides whether [event] concerns an installed Python package.
     *
     * Deciding on the path alone keeps this cheap: an installation, an upgrade or a removal of
     * `mkdocs-material` writes below `site-packages`, and nothing else the plugin cares about does. What the
     * icon index then remembers is stale, so it is thrown away — building it again costs one directory
     * listing, and only once something asks for an icon.
     *
     * @param event a single pending VFS event
     * @return `true` for anything below a `site-packages` directory
     */
    private fun isInstalledPackage(event: VFileEvent): Boolean = event.path.contains(SITE_PACKAGES)

    private companion object {

        /** The path fragment every installed Python package carries. */
        const val SITE_PACKAGES = "site-packages"
    }
}
