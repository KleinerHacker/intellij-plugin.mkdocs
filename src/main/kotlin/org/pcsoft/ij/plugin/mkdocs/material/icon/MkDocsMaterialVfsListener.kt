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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Throws the icon index away whenever an installed Python package changes.
 *
 * The icons the theme offers are the SVG files of the installed `mkdocs-material`, so an installation, an
 * upgrade or a removal of it makes everything the index remembers stale. Building the index again costs one
 * directory listing, and only once something asks for an icon, so the index is dropped rather than repaired.
 *
 * A listener of its own rather than a branch inside the MkDocs one: what the feature watches is the feature's
 * business, and the plugin must not know that an icon index exists.
 *
 * Registered in `plugin.xml` under `vfs.asyncListener`.
 */
class MkDocsMaterialVfsListener : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (events.none(::isInstalledPackage)) return null
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) MkDocsMaterialIconIndex.getInstance(project).invalidate()
        }
        return null
    }

    /**
     * Decides whether [event] concerns an installed Python package.
     *
     * Deciding on the path alone keeps this cheap: an installation, an upgrade or a removal of
     * `mkdocs-material` writes below `site-packages`, and nothing else this feature cares about does.
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
