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

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Points out the Markdown extensions *Material for MkDocs* builds on wherever they are there.
 *
 * Nothing here is wrong: a site listing none of these extensions builds and renders. What it loses is the
 * markup the theme styles — call-outs, content tabs, key caps, highlighted code — which is why this is a weak
 * warning and why it may be switched off entirely. An author who deliberately keeps the Markdown of the site
 * plain is right to do so, and should not have to read about it on every file.
 *
 * The extensions the configuration *forces* are none of this inspection's business; those are an error
 * [MkDocsMaterialExtensionAnnotator] reports and that cannot be turned off.
 */
class MkDocsMaterialRecommendedExtensionInspection : LocalInspectionTool() {

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        val yamlFile = file as? YAMLFile ?: return null
        val missing = MkDocsMaterialExtensions.missingRecommended(file.project, yamlFile)
        if (missing.isEmpty()) return null

        val anchor = anchorOf(yamlFile)
        return missing.map { extension ->
            manager.createProblemDescriptor(
                anchor,
                MkDocsBundle.message("material.extension.recommended", extension.id),
                MkDocsMaterialAddExtensionFix(extension),
                ProblemHighlightType.WEAK_WARNING,
                isOnTheFly,
            )
        }.toTypedArray()
    }

    /**
     * Returns the element the warnings are attached to.
     *
     * The list of extensions itself where the file has one, and the `theme` key otherwise — that is where the
     * theme asking for them is named, and it is the one key a Material site is guaranteed to carry. The file
     * is the last resort, which only a file without any top level key ever falls back to.
     *
     * @param file the configuration file being inspected
     */
    private fun anchorOf(file: YAMLFile): PsiElement =
        keyOf(file, MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS)
            ?: keyOf(file, MkDocsConfig.KEY_THEME)
            ?: file

    /**
     * Returns the key element of the dotted [path], or `null` if the file does not carry it.
     *
     * @param file the configuration file to look in
     * @param path the dotted path of the key
     */
    private fun keyOf(file: YAMLFile, path: String): PsiElement? =
        YAMLUtil.getQualifiedKeyInFile(file, *path.split('.').toTypedArray())?.key
}
