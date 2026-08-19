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

package org.pcsoft.ij.plugin.mkdocs.material.markdown

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex

/**
 * Completes the icon shorthands of *Material for MkDocs* inside the pages of a site.
 *
 * The theme lets a page write `:material-check:`, `:fontawesome-brands-github:` or `:octicons-repo-16:` and
 * renders the icon of that name inline. Which shorthands exist follows directly from the installed icon sets:
 * the name is the path below the sets with its slashes turned into dashes.
 *
 * Only offered inside the documentation directory of a detected site — the shorthand means nothing in a
 * Markdown file that is not built by this theme, and offering thousands of entries there would make the
 * completion popup useless.
 *
 * Lives in the optional descriptor for the Markdown plugin: without that plugin there is no Markdown language
 * to register against, and the rest of the plugin has to keep working all the same.
 */
class MkDocsMaterialShorthandCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.position.containingFile?.originalFile ?: return
        val virtualFile = file.virtualFile ?: return
        if (!MkDocsProject.isPageFile(virtualFile.name)) return

        val project = parameters.position.project
        val siteRoot = MkDocsMaterialShorthands.siteRootOf(project, virtualFile) ?: return
        val index = MkDocsMaterialIconIndex.getInstance(project)
        val names = index.names(siteRoot)
        if (names.isEmpty()) return

        // The colon opening a shorthand is not part of an identifier, so the platform would prefix-match the
        // entries against nothing at all and offer every one of them on every keystroke.
        val prefixed = result.withPrefixMatcher(prefixOf(parameters))
        prefixed.addAllElements(names.map { element(index, siteRoot, it) })
    }

    /**
     * Returns the lookup element offering the shorthand of the icon [name].
     *
     * The drawing stays the icon of the entry — that is what the author is picking from — and the mark of the
     * theme is badged onto it. A Markdown file mixes the shorthands of the theme with everything else the
     * editor offers, and without the badge nothing in the popup says that this syntax is the theme's.
     *
     * @param index the index the drawing is loaded from
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param name the name of the icon, as the theme addresses it
     */
    private fun element(
        index: MkDocsMaterialIconIndex,
        siteRoot: VirtualFile,
        name: String,
    ): LookupElement {
        val shorthand = MkDocsMaterialShorthands.shorthandOf(name)
        return LookupElementBuilder.create(shorthand)
            .withRenderer(object : LookupElementRenderer<LookupElement>() {
                override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                    presentation.itemText = shorthand
                    presentation.typeText = name.substringBefore('/')
                    presentation.icon = index.icon(siteRoot, name)
                        ?.let { MkDocsIconLoader.withBadge(it, MkDocsMaterialIcons.Overlay) }
                        ?: MkDocsMaterialIcons.Badge
                }
            })
    }

    /**
     * Returns what has been typed of the shorthand at the caret.
     *
     * @param parameters what completion was invoked with
     */
    private fun prefixOf(parameters: CompletionParameters): String {
        val text = parameters.editor.document.charsSequence
        var start = parameters.offset
        while (start > 0 && isShorthandCharacter(text[start - 1])) start--
        if (start > 0 && text[start - 1] == ':') start--
        return text.subSequence(start, parameters.offset).toString()
    }

    /**
     * Returns `true` if [character] can appear inside the name of a shorthand.
     *
     * @param character the character to judge
     */
    private fun isShorthandCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '-' || character == '_'
}
