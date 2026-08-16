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

package org.pcsoft.ij.plugin.mkdocs.material.inspection

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfigWriter

/**
 * Finding and adding the Markdown extensions a *Material for MkDocs* site is missing.
 *
 * The annotator, the inspection and the quick fix all have to agree on what counts as missing and on how an
 * extension is written into the file — collecting that here is what keeps the three from drifting apart.
 *
 * The distinction between the two lists is the whole point of this file. The theme renders a plain site
 * without a single extension, so nothing is missing just because it is absent: [missingRequired] reports only
 * what the configuration itself asks for, which is why the annotator may show it as an error, while
 * [missingRecommended] reports what would merely widen what an author can write, which is why the inspection
 * shows it as a weak warning the user is free to switch off.
 *
 * Everything reading PSI must be called inside a read action, [add] inside a write action.
 */
object MkDocsMaterialExtensions {

    /**
     * Returns the extensions [file] has to list, but does not.
     *
     * Empty for everything that is not the configuration file of a site using the Material theme, and empty
     * for a Material site whose features force nothing — which is the normal case for a fresh site.
     *
     * @param project the project [file] belongs to
     * @param file the configuration file to inspect
     * @return the forced extensions the file is missing, in declaration order
     */
    fun missingRequired(project: Project, file: YAMLFile): List<MkDocsMarkdownExtension> {
        val settings = settingsOf(project, file) ?: return emptyList()
        // Icon shorthands are a question about the Markdown pages of the site, not about this file; until the
        // icon index answers it, nothing here may claim that pymdownx.emoji is forced.
        val extensions = service<MkDocsMaterialDataService>().extensions
        val required = extensions.requiredBy(settings.features, usesIcons = false)
        return extensions.all.filter { it in required && it.id !in settings.extensions }
    }

    /**
     * Returns the extensions [file] is free to ignore, but would profit from.
     *
     * @param project the project [file] belongs to
     * @param file the configuration file to inspect
     * @return the recommended extensions the file is missing, in declaration order
     */
    fun missingRecommended(project: Project, file: YAMLFile): List<MkDocsMarkdownExtension> {
        val settings = settingsOf(project, file) ?: return emptyList()
        return service<MkDocsMaterialDataService>().extensions.recommended()
            .filter { it.id !in settings.extensions }
    }

    /**
     * Writes [extension] into the `markdown_extensions` of [configFile].
     *
     * An extension carrying default options is written as a mapping with those options below it, everything
     * else as a plain scalar entry: `pymdownx.tabbed` without `alternate_style` renders the old tab style, so
     * adding the identifier alone would produce a site that looks broken rather than one that works.
     *
     * The caller must hold a write action; the whole entry reaches the file as a single commit.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param extension the extension to add
     */
    fun add(project: Project, configFile: VirtualFile, extension: MkDocsMarkdownExtension) {
        MkDocsConfigWriter.edit(project, configFile) {
            if (extension.defaultOptions.isEmpty()) {
                addScalarItem(MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS, extension.id)
            } else {
                addMappingTreeItem(
                    MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS,
                    listOf(extension.id to extension.defaultOptions),
                )
            }
        }
    }

    /**
     * Reads the Material settings of [file], or `null` if these checks do not apply to it at all.
     *
     * @param project the project [file] belongs to
     * @param file the file to read
     */
    private fun settingsOf(project: Project, file: YAMLFile) = file.virtualFile
        ?.takeIf { MkDocsProject.isConfigFile(file.name) && MkDocsMaterialConfig.isMaterialTheme(project, it) }
        ?.let { MkDocsMaterialConfig.read(project, it) }
}
