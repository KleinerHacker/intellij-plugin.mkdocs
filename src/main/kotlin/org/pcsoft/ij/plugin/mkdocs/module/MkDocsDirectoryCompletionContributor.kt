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

import com.intellij.ide.actions.CreateDirectoryCompletionContributor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout

/**
 * Offers the directories an MkDocs site is missing in the *New Directory* dialog.
 *
 * At the site root that is the documentation directory named by `docs_dir`, inside the documentation
 * directory it is the assets directory. Both carry the same badge the project view puts on them, so the
 * suggestion and the result look alike. A directory that already exists is not offered — there would be
 * nothing to create.
 */
class MkDocsDirectoryCompletionContributor : CreateDirectoryCompletionContributor {

    override fun getDescription(): String = MkDocsBundle.message("directory.completion.description")

    override fun getVariants(directory: PsiDirectory): Collection<CreateDirectoryCompletionContributor.Variant> {
        val project = directory.project
        val virtualFile = directory.virtualFile
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project) ?: return emptyList()
        // Without the facet the directory belongs to no detected site, and nothing here applies.
        MkDocsFacet.getInstance(module) ?: return emptyList()

        val configFile = MkDocsLayout.configFileOf(virtualFile)
        if (configFile != null) {
            val docsDir = MkDocsConfig.resolveDocsDir(project, configFile)
            if (virtualFile.findFileByRelativePath(docsDir) != null) return emptyList()
            return listOf(variantOf(docsDir, MkDocsIcons.DocsBadgeLarge))
        }

        if (MkDocsLayout.isDocsDirectory(project, virtualFile)) {
            val assetsDir = MkDocsLayout.assetsDirNameOf(module)
            if (virtualFile.findChild(assetsDir) != null) return emptyList()
            return listOf(variantOf(assetsDir, MkDocsIcons.AssetsBadgeLarge))
        }

        return emptyList()
    }

    /**
     * Builds a suggestion for [path].
     *
     * No source root type is attached: MkDocs directories are no source roots of any build system, and the
     * parameter is optional precisely for contributors like this one.
     */
    private fun variantOf(path: String, icon: javax.swing.Icon): CreateDirectoryCompletionContributor.Variant =
        CreateDirectoryCompletionContributor.Variant(path, null, icon)
}
