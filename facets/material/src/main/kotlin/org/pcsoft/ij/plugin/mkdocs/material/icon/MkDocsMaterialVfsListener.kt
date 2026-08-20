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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService

/**
 * Throws away what is remembered about the installed theme whenever that installation is written to.
 *
 * The icons the theme offers are the SVG files of the installed `mkdocs-material`, so an installation, an
 * upgrade or a removal of it makes everything stale that was read out of it.
 *
 * An addition, never the guarantee. The VFS reports what it watches, and what it watches are the content and
 * library roots of the open projects; a `site-packages` outside of them is watched only where it happens to be
 * a root of a configured interpreter. So a user who installed the theme next to a running IDE reaches for the
 * *Reload installation* action, and this listener is what saves them the trip where the directory is watched
 * after all.
 *
 * The paths of this distribution rather than any `site-packages`: everything read out of an installation is
 * dropped here, and dropping it because some unrelated package was written is what makes the reading happen
 * again and again.
 *
 * A listener of its own rather than a branch inside the MkDocs one: what the feature watches is the feature's
 * business, and the plugin must not know that an icon index exists.
 *
 * Registered in the module descriptor of the feature under `vfs.asyncListener`.
 */
class MkDocsMaterialVfsListener : AsyncFileListener {

    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (events.none { isDistributionPath(it.path) }) return null
        // Where the package lies is what pip answered once; an installation or a removal makes that stale.
        service<MkDocsPipService>().invalidate()
        service<MkDocsMaterialInstallationCache>().invalidate()
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) MkDocsMaterialIconIndex.getInstance(project).invalidate()
        }
        return null
    }

    companion object {

        /** The `*.dist-info` directory pip writes next to this distribution. */
        private const val DIST_INFO = "mkdocs_material-"

        /** The path of the icon sets inside the installed package. */
        private const val ICONS_INSIDE_PACKAGE = "material/templates/.icons"

        /**
         * Returns whether [path] belongs to an installation of this distribution.
         *
         * Deciding on the path alone keeps this cheap, and it is decided on the two paths only this
         * distribution has: the `*.dist-info` pip writes for it, and the icon sets inside the package.
         *
         * @param path the path of a single pending VFS event
         */
        fun isDistributionPath(path: String): Boolean {
            val normalised = path.replace('\\', '/')
            return normalised.contains(DIST_INFO) || normalised.contains(ICONS_INSIDE_PACKAGE)
        }
    }
}
