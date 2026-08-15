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
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialCssVariables

/**
 * Completes the custom properties *Material for MkDocs* styles itself with.
 *
 * The theme is not restyled by overriding its rules but by redefining its variables — `--md-primary-fg-color`
 * and its like — in an additional style sheet. Which ones exist is documented, not discoverable: a style sheet
 * of a site defines them, it does not read them from anywhere, so nothing in the IDE can offer them by
 * looking at the project.
 *
 * Each entry carries the description of the variable, so what a name actually paints is readable in the popup
 * rather than only in the theme's documentation.
 *
 * Lives in the optional descriptor for the CSS plugin: without that plugin there is no CSS language to
 * register against, and the rest of the plugin has to keep working all the same.
 */
class MkDocsMaterialCssVariableCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val prefixed = result.withPrefixMatcher(prefixOf(parameters))
        prefixed.addAllElements(
            MkDocsMaterialCssVariables.all.map { variable ->
                LookupElementBuilder.create(variable.name)
                    // A style sheet of a site mixes these with the custom properties of the author, and the
                    // icon is what tells the two apart in the popup.
                    .withIcon(MkDocsIcons.MaterialBadge)
                    .withTypeText(MkDocsBundle.message(variable.group.titleKey), true)
                    .withTailText("  ${MkDocsBundle.message(variable.descriptionKey)}", true)
            }
        )
    }

    /**
     * Returns what has been typed of the variable name at the caret.
     *
     * The two dashes opening a custom property are not part of an identifier, so without this the entries
     * would be matched against nothing and all of them offered on every keystroke.
     *
     * @param parameters what completion was invoked with
     */
    private fun prefixOf(parameters: CompletionParameters): String {
        val text = parameters.editor.document.charsSequence
        var start = parameters.offset
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '-')) start--
        return text.subSequence(start, parameters.offset).toString()
    }
}
