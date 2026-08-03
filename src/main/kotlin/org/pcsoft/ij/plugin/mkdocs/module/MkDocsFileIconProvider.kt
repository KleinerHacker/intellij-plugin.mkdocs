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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import javax.swing.Icon

/**
 * Replaces the generic YAML icon of `mkdocs.yml` / `mkdocs.yaml` with the MkDocs configuration icon.
 *
 * The file keeps its YAML file type — only its icon changes — so the configuration file is recognisable at a
 * glance wherever the IDE renders it: project view, editor tabs, "Go to file" and navigation popups.
 */
class MkDocsFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.isDirectory) return null
        return if (MkDocsProject.isConfigFile(file.name)) MkDocsIcons.ConfigFile else null
    }
}
