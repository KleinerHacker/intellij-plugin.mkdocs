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

package org.pcsoft.ij.plugin.mkdocs.material.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialKeys
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig

/**
 * Marks the completion entries of an MkDocs configuration file that come from *Material for MkDocs*.
 *
 * The configuration file of a site built with the theme mixes two vocabularies: the keys MkDocs itself reads,
 * and the ones the theme adds on top. The completion popup shows both in one list and says nothing about
 * which is which, so an author cannot tell from it what would stop working on a change of theme.
 *
 * The entries themselves are none of this contributor's doing — they come from the JSON schema of the theme
 * and from the other contributors of this plugin. This one runs first, lets the others produce their entries
 * through [CompletionResultSet.runRemainingContributors] and hands them on with the icon of the theme put on
 * them. An entry it cannot place is passed through untouched, because a wrong mark is worse than none.
 *
 * An entry that already carries an icon of its own — the drawing next to an icon name — keeps it and is badged
 * instead: there the icon is the content of the entry, not a statement about where it comes from.
 *
 * Only active in a configuration file whose theme is Material. Everywhere else the whole question does not
 * arise.
 */
class MkDocsMaterialOriginCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        val virtualFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(position.project, virtualFile)) return

        result.runRemainingContributors(parameters) { completionResult ->
            val element = completionResult.lookupElement
            val decorated = if (MkDocsMaterialKeys.isMaterialLookup(position, element.lookupString)) {
                decorate(element)
            } else {
                element
            }
            result.passResult(completionResult.withLookupElement(decorated))
        }
    }

    /**
     * Returns [element] rendered with the mark of the theme.
     *
     * Decorated rather than rebuilt: the entry keeps whatever the contributor behind it gave it — its type
     * text, its tail text, its insert handler — and only the icon is taken over. The renderer asks the wrapped
     * element first, so an entry drawing its own icon is badged instead of overwritten.
     *
     * @param element the entry another contributor produced
     */
    private fun decorate(element: LookupElement): LookupElement =
        LookupElementDecorator.withRenderer(
            element,
            object : LookupElementRenderer<LookupElementDecorator<LookupElement>>() {
                override fun renderElement(
                    decorator: LookupElementDecorator<LookupElement>,
                    presentation: LookupElementPresentation,
                ) {
                    decorator.delegate.renderElement(presentation)
                    val own = presentation.icon
                    presentation.icon =
                        if (own == null) MkDocsMaterialIcons.Badge
                        else MkDocsIconLoader.withBadge(own, MkDocsMaterialIcons.Overlay)
                }
            }
        )
}
