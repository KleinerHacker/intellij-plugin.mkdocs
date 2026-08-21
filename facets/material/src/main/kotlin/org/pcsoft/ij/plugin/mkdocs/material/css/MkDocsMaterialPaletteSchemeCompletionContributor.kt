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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Completes `theme.palette.scheme` with the colour schemes the style sheets of the site paint.
 *
 * A scheme is not a value the theme defines but a name a style sheet answers to: everything the theme paints
 * differently on another ground sits below `[data-md-color-scheme="…"]`, and `theme.palette.scheme` is nothing
 * but the identifier written into that attribute. Offering a written list of names would offer grounds no
 * style sheet of the site stands on, which is what the schema did until now.
 *
 * So the names come out of the CSS and out of nothing else — out of both style sheets a site loads, the one
 * the installed theme ships and the ones behind `extra_css`. `default` and `slate` are therefore offered as
 * what they are: two grounds a file of the theme paints, not two values the plugin has been told about. Each
 * entry names where it comes from, so a ground of the site is told apart from one of the theme.
 *
 * Lives in the optional descriptor for the CSS plugin, together with everything else reading a style sheet.
 */
class MkDocsMaterialPaletteSchemeCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (MkDocsMaterialPaletteKeys.roleOf(position) != MkDocsMaterialPaletteKeys.Role.SCHEME) return

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(position.project, configFile)) return

        result.addAllElements(
            position.project.service<MkDocsMaterialCssPaletteService>().schemes(configFile)
                .map { scheme ->
                    // Deliberately without an icon: `MkDocsMaterialOriginCompletionContributor` runs in front
                    // of this one and puts the mark of the theme on every entry of a Material key. An icon set
                    // here would be taken for the content of the entry and end up badged on top.
                    LookupElementBuilder.create(scheme.name).withTypeText(typeTextOf(scheme), true)
                }
        )
    }

    /**
     * Returns what the popup names as the origin of [scheme].
     *
     * A ground of the site is named by the file it stands in, which is the one an author is about to edit. A
     * ground the installed theme paints is named by the theme instead: the file it lies in is a minified
     * asset whose name carries a build hash, and that says nothing to anybody.
     *
     * @param scheme the ground the entry offers
     */
    private fun typeTextOf(scheme: MkDocsMaterialCssScheme): String = when {
        scheme.builtIn -> MkDocsMaterialBundle.message("material.palette.scheme.type.theme")
        else -> scheme.file?.name.orEmpty()
    }
}
