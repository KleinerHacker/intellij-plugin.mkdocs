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
import com.intellij.openapi.util.Key
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex.Companion.getInstance

/**
 * Completes the names of the icons the installed *Material for MkDocs* offers.
 *
 * The JSON schema of the theme cannot answer this: which icons exist is a property of the installed package,
 * not of the configuration format, and the sets hold several thousand names that would have to be written
 * into a schema built at start up. So the completion asks the [MkDocsMaterialIconIndex] instead, which reads
 * them from the package itself.
 *
 * Offered at every place the configuration file names an icon, which [MkDocsMaterialIconKeys] decides.
 *
 * The drawing itself is shown next to each entry, through a renderer rather than through a fixed icon on the
 * element: the popup then loads only what it actually paints, instead of every one of those thousands.
 */
class MkDocsMaterialIconCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!MkDocsMaterialIconKeys.isIconValue(position)) return

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
        .apply { putUserData(ICON_ELEMENT, true) }

    companion object {

        /**
         * What marks a lookup element as one of this completion.
         *
         * The footer menu of the popup is shared by everything the IDE completes, so
         * [MkDocsMaterialIconLookupActionProvider] has to be able to tell whether the entry in front of the
         * user is an icon of the theme.
         */
        val ICON_ELEMENT: Key<Boolean> = Key.create("mkdocs.material.icon.element")
    }
}
