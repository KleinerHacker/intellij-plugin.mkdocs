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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialIconSettingsConfigurable

/**
 * Opens the *Material* page of the settings, where the installation of the theme is chosen.
 *
 * The way out of the banner [MkDocsMaterialInstallationAnnotator] shows: nothing about a missing installation
 * can be fixed inside the configuration file, so the fix does not change the file at all — it takes the user
 * to the one place the path is set.
 */
class MkDocsMaterialOpenSettingsFix : IntentionAction {

    override fun getText(): String = MkDocsMaterialBundle.message("material.installation.fix")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MkDocsMaterialIconSettingsConfigurable::class.java)
    }

    override fun startInWriteAction(): Boolean = false
}
