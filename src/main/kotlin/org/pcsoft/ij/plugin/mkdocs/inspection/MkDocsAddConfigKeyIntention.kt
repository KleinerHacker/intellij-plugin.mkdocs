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

package org.pcsoft.ij.plugin.mkdocs.inspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject

/**
 * Adds a missing configuration key with an empty value.
 *
 * Offered from the banner [MkDocsMissingMetadataAnnotator] puts above the file. The value is left empty on
 * purpose: nothing here can know what the site is called or who wrote it, and an invented value is harder to
 * notice than an empty one.
 *
 * @param key the configuration key to add
 */
class MkDocsAddConfigKeyIntention(private val key: String) : IntentionAction {

    override fun getText(): String = MkDocsBundle.message("metadata.fix", key)

    override fun getFamilyName(): String = MkDocsBundle.message("metadata.fix.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val yamlFile = file as? YAMLFile ?: return false
        if (!MkDocsProject.isConfigFile(yamlFile.name)) return false
        return key in MkDocsMetadata.missingKeys(yamlFile)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val yamlFile = file as? YAMLFile ?: return
        MkDocsMetadata.addKey(project, yamlFile, key)
    }

    override fun startInWriteAction(): Boolean = true
}
