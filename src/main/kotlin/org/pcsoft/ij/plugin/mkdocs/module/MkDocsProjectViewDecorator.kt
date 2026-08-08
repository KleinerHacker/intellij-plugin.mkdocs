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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.MkDocsTextAttributes
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import javax.swing.Icon
import javax.swing.SwingConstants

/**
 * Renders the directories of an MkDocs site the way Maven renders a project directory: the site name in
 * brackets behind the site root, plus a small badge on the folder icon of the site root, the documentation
 * directory and the assets directory.
 *
 * Only directories belonging to a module that carries an [MkDocsFacet] are decorated, so the project view
 * never disagrees with the detected module model.
 */
class MkDocsProjectViewDecorator : ProjectViewNodeDecorator {

    companion object {

        /**
         * Returns `true` if [directory] directly contains an MkDocs configuration file.
         *
         * @param directory the directory to inspect
         */
        @JvmStatic
        fun isSiteRoot(directory: VirtualFile): Boolean = MkDocsLayout.isSiteRoot(directory)

        /**
         * Returns `true` if [directory] is the documentation directory of the site it belongs to.
         *
         * The name comes from `docs_dir` of that site, falling back to the MkDocs default.
         *
         * @param project the project [directory] belongs to
         * @param directory the directory to inspect
         */
        @JvmStatic
        fun isDocsDirectory(project: Project, directory: VirtualFile): Boolean =
            MkDocsLayout.isDocsDirectory(project, directory)

        /**
         * Returns `true` if [directory] is the assets directory of the site it belongs to.
         *
         * The expected name is taken from the MkDocs facet of the owning module, because MkDocs itself has no
         * configuration key for the assets directory.
         *
         * @param project the project [directory] belongs to
         * @param module the module owning [directory]
         * @param directory the directory to inspect
         */
        @JvmStatic
        fun isAssetsDirectory(project: Project, module: Module, directory: VirtualFile): Boolean =
            MkDocsLayout.isAssetsDirectory(project, module, directory)

        /**
         * Puts [badge] into the lower right corner of [base].
         *
         * @param base the undecorated node icon
         * @param badge the marker to overlay
         * @return a layered icon of the same size as [base]
         */
        @JvmStatic
        fun withBadge(base: Icon, badge: Icon = MkDocsIcons.Badge): Icon {
            val layered = LayeredIcon.layeredIcon(arrayOf(base, badge))
            layered.setIcon(badge, 1, SwingConstants.SOUTH_EAST)
            return layered
        }
    }

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val directory = node.value as? PsiDirectory ?: return
        val virtualFile = directory.virtualFile
        val project = node.project ?: return
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return
        val facet = MkDocsFacet.getInstance(module) ?: return

        if (isSiteRoot(virtualFile)) {
            val siteName = facet.configuration.siteName.takeIf { it.isNotBlank() } ?: return
            // Once a coloured fragment is added the plain presentable text is no longer rendered, so the
            // directory name has to become the first fragment itself.
            if (data.coloredText.isEmpty()) {
                data.addText(data.presentableText ?: directory.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            data.addText(
                " [$siteName]",
                MkDocsTextAttributes.asSimpleTextAttributes(MkDocsTextAttributes.SiteName),
            )
            data.getIcon(false)?.let { data.setIcon(withBadge(it)) }
            return
        }

        val badge = when {
            isDocsDirectory(project, virtualFile) -> MkDocsIcons.DocsBadge
            isAssetsDirectory(project, module, virtualFile) -> MkDocsIcons.AssetsBadge
            else -> return
        }
        data.getIcon(false)?.let { data.setIcon(withBadge(it, badge)) }
    }
}
