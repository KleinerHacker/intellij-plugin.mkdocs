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

package org.pcsoft.ij.plugin.mkdocs.material.css

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Marks a value of `theme.palette` that the style sheets of the site contradict.
 *
 * `theme.palette` and the style sheets behind `extra_css` describe the same appearance twice, and the
 * configuration file shows only one half of it. Three ways of writing the two halves down leave a site
 * painted differently than it reads:
 *
 * * `custom` says the theme sets no colour and a style sheet of the site does — and if none of them defines
 *   the custom property, nothing sets it at all and the theme falls back to its own ground colour;
 * * a named colour says the theme sets it — and a style sheet redefining the same custom property overrides
 *   exactly that, so which of the two halves wins cannot be read off either file;
 * * a `scheme` no style sheet paints is a ground that does not exist: the theme writes the name into the
 *   `data-md-color-scheme` attribute of the page, no rule matches it, and the site is painted as if nothing
 *   had been asked for.
 *
 * Which definitions count for a palette is decided by the ground it is painted on: `:root` paints every
 * palette of the site, while a rule below `[data-md-color-scheme="slate"]` paints exactly the palette whose
 * `scheme` names that identifier. A palette of a colour scheme toggle therefore may well be `custom` while
 * its neighbour is not.
 *
 * The colours are a warning: either way of writing them is legal CSS and legal MkDocs, and there are sites
 * whose author means it. The unknown ground is an error, drawn the way an unresolved reference is — the name
 * points at a rule that is nowhere, which is not a matter of taste. None of them carries a quick fix, because
 * which half was meant — the configuration file or the style sheet — is a guess.
 *
 * The ground is judged only while an installation of the theme can be read: `default` and `slate` are rules
 * of the style sheet that package ships, and until it has been walked the set of grounds is a stand-in rather
 * than an answer. The colours are judged only while `extra_css` names a readable style sheet, for the same
 * reason — a site that styles nothing has nothing to contradict.
 *
 * Reported here rather than left to the reference on the same value: an unresolved reference is drawn by the
 * language that owns the file, and YAML draws none. `MkDocsMaterialSchemeReference` therefore navigates, and
 * the mark is put here.
 */
class MkDocsMaterialPaletteCssAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val scalar = element as? YAMLScalar ?: return
        val file = scalar.containingFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return

        val role = MkDocsMaterialPaletteKeys.roleOf(scalar) ?: return
        if (role != MkDocsMaterialPaletteKeys.Role.SCHEME &&
            MkDocsMaterialPaletteKeys.variableOf(role) == null
        ) {
            return
        }

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(scalar.project, configFile)) return

        val value = scalar.textValue.trim()
        if (value.isEmpty()) return

        when (role) {
            MkDocsMaterialPaletteKeys.Role.SCHEME -> annotateScheme(scalar, configFile, value, holder)
            else -> annotateColour(scalar, configFile, role, value, holder)
        }
    }

    /**
     * Marks [value] if no style sheet the site loads paints a ground of that name.
     *
     * @param scalar the value of `theme.palette.scheme`
     * @param configFile the configuration file of the site
     * @param value the identifier as the file writes it
     * @param holder what the finding is reported to
     */
    private fun annotateScheme(
        scalar: YAMLScalar,
        configFile: VirtualFile,
        value: String,
        holder: AnnotationHolder,
    ) {
        val palette = scalar.project.service<MkDocsMaterialCssPaletteService>()
        // Every ground a site can stand on is asked for, the theme's own included — those are named out of
        // the model while no installation can be read, which is what keeps the judgement working in a project
        // whose environment the IDE has not found.
        if (palette.schemes(configFile).any { it.name == value }) return

        holder.newAnnotation(
            HighlightSeverity.ERROR,
            MkDocsMaterialBundle.message("material.palette.scheme.unknown", value),
        )
            // Drawn the way an unresolved reference is, because that is what it is: the name points at a rule
            // of a style sheet, and there is none.
            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
            .range(rangeOf(scalar))
            .create()
    }

    /**
     * Marks [value] if the style sheets of the site and the colour of the palette contradict each other.
     *
     * @param scalar the value of `theme.palette.primary` or `theme.palette.accent`
     * @param configFile the configuration file of the site
     * @param role which of the two colours it is
     * @param value the identifier as the file writes it
     * @param holder what the finding is reported to
     */
    private fun annotateColour(
        scalar: YAMLScalar,
        configFile: VirtualFile,
        role: MkDocsMaterialPaletteKeys.Role,
        value: String,
        holder: AnnotationHolder,
    ) {
        val variable = MkDocsMaterialPaletteKeys.variableOf(role) ?: return
        val palette = scalar.project.service<MkDocsMaterialCssPaletteService>()
        // Nothing read is not the same as nothing found: a site without a style sheet says nothing about its
        // colours, and every `custom` of it would be reported for a file that does not exist.
        if (palette.definitions(configFile).isEmpty()) return

        val scheme = MkDocsMaterialPaletteKeys.schemeNameOf(scalar)
        val definitions = palette.definitionsFor(configFile, variable, scheme)
        val message = when {
            value == MkDocsMaterialPaletteKeys.COLOR_CUSTOM && definitions.isEmpty() ->
                MkDocsMaterialBundle.message("material.palette.css.missing", variable, scheme)

            value != MkDocsMaterialPaletteKeys.COLOR_CUSTOM && definitions.isNotEmpty() ->
                MkDocsMaterialBundle.message(
                    "material.palette.css.conflict",
                    value,
                    variable,
                    definitions.first().file.name,
                )

            else -> return
        }

        holder.newAnnotation(HighlightSeverity.WARNING, message).range(rangeOf(scalar)).create()
    }

    /**
     * Returns the range of the value of [scalar] in the document.
     *
     * The quotes around a value are not part of it, which is what keeps the mark off them.
     *
     * @param scalar the value being marked
     */
    private fun rangeOf(scalar: YAMLScalar): TextRange =
        ElementManipulators.getValueTextRange(scalar).shiftRight(scalar.textRange.startOffset)
}
