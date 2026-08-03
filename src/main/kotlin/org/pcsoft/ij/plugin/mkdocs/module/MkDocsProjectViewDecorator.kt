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
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.ui.LayeredIcon
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import javax.swing.Icon
import javax.swing.SwingConstants

/**
 * Renders the root directory of an MkDocs site the way Maven renders a project directory: the site name in
 * brackets behind the directory name, plus a small badge on the folder icon.
 *
 * Only directories that actually hold an MkDocs configuration file and whose module carries an
 * [MkDocsFacet] are decorated, so the project view never disagrees with the detected module model.
 */
class MkDocsProjectViewDecorator : ProjectViewNodeDecorator {

    companion object {

        /**
         * Returns `true` if [directory] directly contains an MkDocs configuration file.
         *
         * @param directory the directory to inspect
         */
        @JvmStatic
        fun isSiteRoot(directory: VirtualFile): Boolean =
            directory.isValid && directory.isDirectory &&
                MkDocsProject.CONFIG_FILE_NAMES.any { directory.findChild(it) != null }

        /**
         * Puts the MkDocs badge into the lower right corner of [base].
         *
         * @param base the undecorated node icon
         * @return a layered icon of the same size as [base]
         */
        @JvmStatic
        fun withBadge(base: Icon): Icon {
            val layered = LayeredIcon.layeredIcon(arrayOf(base, MkDocsIcons.Badge))
            layered.setIcon(MkDocsIcons.Badge, 1, SwingConstants.SOUTH_EAST)
            return layered
        }
    }

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val directory = node.value as? PsiDirectory ?: return
        val virtualFile = directory.virtualFile
        if (!isSiteRoot(virtualFile)) return

        val project = node.project ?: return
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return
        val siteName = MkDocsFacet.getInstance(module)?.configuration?.siteName?.takeIf { it.isNotBlank() } ?: return

        data.locationString = siteName
        data.getIcon(false)?.let { data.setIcon(withBadge(it)) }
    }
}
