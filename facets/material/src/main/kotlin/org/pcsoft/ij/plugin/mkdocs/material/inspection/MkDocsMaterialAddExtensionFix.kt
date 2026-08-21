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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension

/**
 * Adds one Markdown extension to `markdown_extensions`, with the options it needs to render.
 *
 * The same fix is offered from two places: from the banner [MkDocsMaterialExtensionAnnotator] puts above a
 * file missing an extension its own features force, and from the weak warning
 * [MkDocsMaterialRecommendedExtensionInspection] shows for the ones that are merely recommended. Both hand
 * the user the identical change, so the class implements both contracts rather than existing twice.
 *
 * @param extension the extension this fix adds
 */
class MkDocsMaterialAddExtensionFix(
    private val extension: MkDocsMarkdownExtension,
) : IntentionAction, LocalQuickFix {

    override fun getText(): String = MkDocsMaterialBundle.message("material.extension.fix", extension.id)

    override fun getName(): String = text

    override fun getFamilyName(): String = MkDocsMaterialBundle.message("material.extension.fix.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val yamlFile = file as? YAMLFile ?: return false
        return extension in MkDocsMaterialExtensions.missingRequired(project, yamlFile) ||
                extension in MkDocsMaterialExtensions.missingRecommended(project, yamlFile)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        addTo(project, file)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        addTo(project, descriptor.psiElement?.containingFile)
    }

    override fun startInWriteAction(): Boolean = true

    /**
     * Writes the extension into [file], if that is a configuration file this fix may touch.
     *
     * @param project the project [file] belongs to
     * @param file the file the fix was invoked on
     */
    private fun addTo(project: Project, file: PsiFile?) {
        val configFile = (file as? YAMLFile)?.virtualFile ?: return
        MkDocsMaterialExtensions.add(project, configFile, extension)
    }
}
