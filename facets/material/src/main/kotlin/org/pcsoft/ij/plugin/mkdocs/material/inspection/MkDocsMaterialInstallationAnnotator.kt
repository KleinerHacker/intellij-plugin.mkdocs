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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Shows a banner above a Material configuration file while no installation of the theme can be found.
 *
 * Everything this feature reads out of the installed package — the icons of the completion, the drawings of
 * the inlay hints, the shorthands of a page — is silently empty without it, and an empty completion popup
 * gives no clue whether the theme offers nothing or whether the IDE simply does not know where it lies. So
 * the state is said outright, once, on the one file the site is configured in.
 *
 * A file level annotation rather than a highlight: what is reported is about the site, not about a place in
 * the file. The fix opens the settings page, which is where the path is chosen.
 */
class MkDocsMaterialInstallationAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotators visit every element of the file; the file itself is one of them, and it is the only one
        // this annotator has anything to say about.
        val file = element as? YAMLFile ?: return
        val configFile = file.virtualFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!MkDocsMaterialConfig.isMaterialTheme(element.project, configFile)) return
        if (MkDocsMaterialIconLocator.locateInstallation(element.project) != null) return
        // Nothing found is only a finding once pip has been asked. Until then the question is merely open —
        // the locator has put it, and the highlighting is restarted with the answer — and reporting an error
        // in the meantime would put a banner above every freshly opened file for a moment.
        if (!service<MkDocsPipService>().isKnown(MkDocsMaterialIconLocator.DISTRIBUTION)) return

        holder.newAnnotation(
            HighlightSeverity.ERROR,
            MkDocsMaterialBundle.message("material.installation.missing"),
        )
            .fileLevel()
            .withFix(MkDocsMaterialOpenSettingsFix())
            .create()
    }
}
