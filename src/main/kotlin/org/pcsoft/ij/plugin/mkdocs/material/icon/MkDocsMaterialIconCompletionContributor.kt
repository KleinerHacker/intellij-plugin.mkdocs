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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex.Companion.getInstance

/**
 * Completes the names of the icons the installed *Material for MkDocs* offers.
 *
 * The JSON schema of the theme cannot answer this: which icons exist is a property of the installed package,
 * not of the configuration format, and the sets hold several thousand names that would have to be written
 * into a schema built at start up. So the completion asks the [MkDocsMaterialIconIndex] instead, which reads
 * them from the package itself.
 *
 * Offered at the three places the configuration file names an icon:
 * * `theme.icon.*` — the icons of the repository link, of the edit button and of the navigation;
 * * `theme.palette[].toggle.icon` — the icon on the button switching between light and dark;
 * * `extra.social[].icon` — the icons of the links in the footer.
 *
 * The drawing itself is shown next to each entry, through a renderer rather than through a fixed icon on the
 * element: the popup then loads only what it actually paints, instead of every one of those thousands.
 */
class MkDocsMaterialIconCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!isIconValue(position)) return

        val siteRoot = file.virtualFile?.parent ?: return
        val index = getInstance(position.project)
        val names = index.names(siteRoot)
        if (names.isEmpty()) return

        result.addAllElements(names.map { element(index, siteRoot, it) })
    }

    /**
     * Returns the lookup element offering the icon [name].
     *
     * @param index the index the drawing is loaded from
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param name the name of the icon, as the theme addresses it
     */
    private fun element(
        index: MkDocsMaterialIconIndex,
        siteRoot: com.intellij.openapi.vfs.VirtualFile,
        name: String,
    ): LookupElement = LookupElementBuilder.create(name)
        .withTypeText(name.substringBefore('/'), true)
        .withRenderer(object : LookupElementRenderer<LookupElement>() {
            override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
                presentation.itemText = name
                presentation.typeText = name.substringBefore('/')
                presentation.icon = index.icon(siteRoot, name)
            }
        })

    /**
     * Returns `true` if [position] sits in a value that names an icon of the theme.
     *
     * @param position the element completion was invoked at
     */
    private fun isIconValue(position: PsiElement): Boolean {
        val keyValue = position.parentOfType<YAMLScalar>()?.parent as? YAMLKeyValue
            ?: position.parentOfType<YAMLKeyValue>()
            ?: return false
        if (keyValue.keyText.trim() != KEY_ICON) return false
        return path(keyValue).let {
            it.startsWith(PATH_THEME_ICON) || it.endsWith(PATH_TOGGLE) || it.endsWith(
                PATH_SOCIAL
            )
        }
    }

    /**
     * Returns the dotted path of [keyValue], as far up as the top level key.
     *
     * The sequences on the way contribute nothing to the path — `extra.social` is what an entry of that
     * sequence is *in*, and the index of the entry says nothing about what the key means.
     *
     * @param keyValue the pair to describe
     */
    private fun path(keyValue: YAMLKeyValue): String {
        val segments = mutableListOf<String>()
        var current: YAMLKeyValue? = keyValue
        var steps = 0
        while (current != null && steps < MAX_ANCESTORS) {
            segments += current.keyText.trim()
            current = current.parentOfType<YAMLKeyValue>()
            steps++
        }
        return segments.reversed().joinToString(".")
    }

    private companion object {

        /** The key naming an icon. */
        const val KEY_ICON = "icon"

        /** The path of the icons of the theme itself. */
        const val PATH_THEME_ICON = "theme.icon."

        /** The tail of the path of the icon on the palette toggle. */
        const val PATH_TOGGLE = "palette.toggle.icon"

        /** The tail of the path of the icon of a social link. */
        const val PATH_SOCIAL = "extra.social.icon"

        /** How many levels the walk upwards climbs; the deepest of the three paths needs four. */
        const val MAX_ANCESTORS = 8
    }
}
