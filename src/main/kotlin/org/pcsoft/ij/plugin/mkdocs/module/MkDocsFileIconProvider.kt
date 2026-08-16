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

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import javax.swing.Icon

/**
 * Gives the files of an MkDocs site their own icons.
 *
 * Four kinds of file are recognised: the configuration file `mkdocs.yml` / `mkdocs.yaml`, the
 * `requirements.txt` lying next to it in the site root, every Markdown file below the documentation directory
 * of a site — those are the pages MkDocs actually publishes — and every style sheet the site loads. All of
 * them keep their file type, only the icon changes, so they are recognisable at a glance wherever the IDE
 * renders them: project view, editor tabs, "Go to file" and navigation popups.
 *
 * A `requirements.txt` is claimed only in the site root, because that is the only place where it pins the
 * MkDocs version, the theme and the plugins of a site. Anywhere else it belongs to some other Python setup.
 *
 * A style sheet is claimed only while `extra_css` names it. Where the file lies decides nothing here: the key
 * is what makes MkDocs load it, and a `.css` sitting unreferenced in the stylesheets directory has no effect
 * on the built site.
 */
class MkDocsFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.isDirectory) return null
        if (MkDocsProject.isConfigFile(file.name)) return MkDocsIcons.ConfigFile
        if (MkDocsProject.isRequirementsFile(file.name)) {
            // Only a name comparison in the parent directory — no PSI, no read action and no project needed.
            val parent = file.parent ?: return null
            return if (MkDocsLayout.isSiteRoot(parent)) MkDocsIcons.RequirementsFile else null
        }
        if (MkDocsProject.isStylesheetFile(file.name)) {
            // Without a project there is no PSI to read extra_css from, so the site cannot be identified.
            val owningProject = project ?: return null
            val isLoaded = runReadActionBlocking { MkDocsLayout.isReferencedStylesheet(owningProject, file) }
            return if (isLoaded) MkDocsIcons.StylesheetFile else null
        }
        if (!MkDocsProject.isPageFile(file.name)) return null

        // Without a project there is no PSI to read docs_dir from, so the site cannot be identified.
        val owningProject = project ?: return null
        // Resolving the site reads the PSI of the configuration file, which needs a read action. The check is
        // a couple of parent lookups, so blocking is cheaper than handing it to a background thread.
        val isPage = runReadActionBlocking { MkDocsLayout.isInsideDocsDir(owningProject, file) }
        return if (isPage) MkDocsIcons.MarkdownFile else null
    }
}
