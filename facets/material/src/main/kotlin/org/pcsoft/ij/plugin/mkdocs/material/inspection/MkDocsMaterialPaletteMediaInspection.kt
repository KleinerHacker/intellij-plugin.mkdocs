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

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Points out a `theme.palette.media` that is none of the media queries *Material for MkDocs* is built around.
 *
 * The value is handed to the `media` attribute of the style sheet the theme renders, so anything a browser
 * accepts is a legal value here and nothing about such a file is broken. What the theme is built around are
 * three queries — the light appearance, the dark one, and the palette following the system preference — and a
 * value outside them decides nothing the toggle of the theme can act on: the palette is either always active
 * or never, and both look like the palette being ignored.
 *
 * A warning, and one that can be switched off: an author writing a query of their own — a print style sheet,
 * a width — is doing something this inspection cannot judge, and should not have to read about it on every
 * file. No quick fix either. Which of the three queries was meant is not something the file says, and picking
 * one would be guessing at the appearance the author wanted.
 *
 * Silent on a site that is not rendered with the theme, and on a YAML file that is not a configuration file
 * of MkDocs — `media` means nothing of this there.
 */
class MkDocsMaterialPaletteMediaInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {

            override fun visitElement(element: PsiElement) {
                val scalar = element as? YAMLScalar ?: return
                val file = scalar.containingFile ?: return
                if (!MkDocsProject.isConfigFile(file.name)) return
                if (!MkDocsMaterialPaletteKeys.isMediaValue(scalar)) return

                val configFile = file.virtualFile ?: return
                if (!MkDocsMaterialConfig.isMaterialTheme(scalar.project, configFile)) return

                // A key written without a value is a file being typed, not a wrong query.
                val query = scalar.textValue.trim()
                if (query.isEmpty()) return
                if (MkDocsMaterialPaletteKeys.isKnownMedia(query)) return

                holder.registerProblem(
                    scalar,
                    MkDocsMaterialBundle.message("material.palette.media.unknown", query),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ElementManipulators.getValueTextRange(scalar),
                )
            }
        }
}
