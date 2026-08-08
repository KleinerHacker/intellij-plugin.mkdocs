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
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import javax.swing.Icon

/**
 * Gives the files of an MkDocs site their own icons.
 *
 * Two kinds of file are recognised: the configuration file `mkdocs.yml` / `mkdocs.yaml`, and every Markdown
 * file below the documentation directory of a site — those are the pages MkDocs actually publishes. Both
 * keep their file type, only the icon changes, so they are recognisable at a glance wherever the IDE renders
 * them: project view, editor tabs, "Go to file" and navigation popups.
 */
class MkDocsFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.isDirectory) return null
        if (MkDocsProject.isConfigFile(file.name)) return MkDocsIcons.ConfigFile
        if (!MkDocsProject.isPageFile(file.name)) return null

        // Without a project there is no PSI to read docs_dir from, so the site cannot be identified.
        val owningProject = project ?: return null
        // Resolving the site reads the PSI of the configuration file, which needs a read action. The check is
        // a couple of parent lookups, so blocking is cheaper than handing it to a background thread.
        val isPage = runReadActionBlocking { MkDocsLayout.isInsideDocsDir(owningProject, file) }
        return if (isPage) MkDocsIcons.MarkdownFile else null
    }
}
