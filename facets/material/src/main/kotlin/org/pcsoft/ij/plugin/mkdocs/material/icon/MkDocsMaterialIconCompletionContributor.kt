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

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

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
 * One level at a time, the way a path is completed: the sets first, then what lies below the chosen one, and
 * the icons themselves at the bottom. A set is offered with its separator and re-opens the popup once it is
 * taken, so the next level follows without the user asking for it again. What decides the levels is
 * [MkDocsMaterialIconTree].
 *
 * The drawing next to an entry is a handle that reads its file only when it is painted, and the entries of a
 * level share one renderer instead of carrying one each. The popup renders *every* matching entry to measure
 * its own width, so an icon loaded while rendering would be several thousand files read for the few rows the
 * user actually sees — which is what made a keystroke take seconds.
 */
class MkDocsMaterialIconCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!MkDocsMaterialIconKeys.isIconValue(position)) return

        val siteRoot = file.virtualFile?.parent ?: return
        val index = position.project.service<MkDocsMaterialIconIndex>()
        val names = index.names(siteRoot)
        if (names.isEmpty()) return

        val typed = typedOf(parameters)
        val group = typed.substringBeforeLast(MkDocsMaterialIconTree.SEPARATOR, "")
        // A group nobody offered is a typo, and the levels below it are none: answering it with the sets
        // again would offer entries that cannot follow what stands in the file.
        if (!MkDocsMaterialIconTree.isGroup(names, group)) return

        // The separator is no part of an identifier, so the platform would match the entries of this level
        // against the whole path written so far and offer none of them.
        val prefixed = result.withPrefixMatcher(typed.substringAfterLast(MkDocsMaterialIconTree.SEPARATOR))
        val level = MkDocsMaterialIconTree.childrenOf(names, group)
        val matching = level.filter { prefixed.prefixMatcher.prefixMatches(it.segment) }
        val offered = matching.take(LIMIT)
        prefixed.addAllElements(elements(index, siteRoot, offered))
        if (matching.size > offered.size) {
            // What was left out can be exactly what matches after the next letter, so the platform must not
            // go on filtering this list — it has to ask again.
            prefixed.restartCompletionOnAnyPrefixChange()
            prefixed.addLookupAdvertisement(
                MkDocsMaterialBundle.message("material.icon.completion.truncated", offered.size, matching.size),
            )
        }
    }

    /**
     * Returns what has been written of the icon name in front of the caret.
     *
     * Read off the document rather than off the element: what the completion is handed is a copy of the file
     * carrying a placeholder at the caret, and the path written so far is what the user sees.
     *
     * @param parameters what completion was invoked with
     */
    private fun typedOf(parameters: CompletionParameters): String {
        val text = parameters.editor.document.charsSequence
        var start = parameters.offset
        while (start > 0 && isNameCharacter(text[start - 1])) start--
        return text.subSequence(start, parameters.offset).toString()
    }

    /**
     * Returns `true` if [character] can appear inside the name of an icon.
     *
     * @param character the character to judge
     */
    private fun isNameCharacter(character: Char): Boolean =
        character.isLetterOrDigit() ||
            character == '-' ||
            character == '_' ||
            character == MkDocsMaterialIconTree.SEPARATOR

    /**
     * Returns the lookup elements offering the [entries] of one level.
     *
     * @param index the index the drawings are loaded from
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param entries what lies directly below the group the caret stands in
     */
    private fun elements(
        index: MkDocsMaterialIconIndex,
        siteRoot: VirtualFile,
        entries: List<MkDocsMaterialIconTree.Entry>,
    ): List<LookupElement> {
        val renderer = LevelRenderer(
            index,
            siteRoot,
            entries.filterNot { it.group }.associate { it.segment to it.path },
        )
        return entries.map { entry ->
            val text = if (entry.group) entry.segment + MkDocsMaterialIconTree.SEPARATOR else entry.segment
            LookupElementBuilder.create(text)
                .withRenderer(renderer)
                .let { if (entry.group) it.withInsertHandler(NextLevel) else it }
                .apply { putUserData(ICON_ELEMENT, true) }
        }
    }

    /**
     * Opens the popup again once a group was taken, so the level below it follows at once.
     *
     * Without it the user would have to ask for the completion again after every separator, which is what
     * makes a path completion feel like one step per set rather than one walk.
     */
    private object NextLevel : InsertHandler<LookupElement> {

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
    }

    /**
     * Paints an entry of one level: a group with the mark of the theme, an icon with its own drawing.
     *
     * One instance for the whole level rather than one per entry — what it needs to answer is the text of the
     * element it is handed, and a set of the theme holds several thousand of them.
     *
     * @property index the index the drawing is loaded from
     * @property siteRoot the directory holding `mkdocs.yml`
     * @property paths the whole path per entry text, for the entries that are icons
     */
    private class LevelRenderer(
        private val index: MkDocsMaterialIconIndex,
        private val siteRoot: VirtualFile,
        private val paths: Map<String, String>,
    ) : LookupElementRenderer<LookupElement>() {

        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            val text = element.lookupString
            presentation.itemText = text
            val path = paths[text]
            if (path == null) {
                // A group. It draws nothing of its own — what lies below it does — so it carries the folder
                // of this plugin, badged with the mark of the theme.
                presentation.icon = MkDocsMaterialIcons.Group
                presentation.setTypeText(MkDocsMaterialBundle.message("material.icon.completion.group"), null)
                presentation.isTypeGrayed = true
                return
            }
            presentation.icon = index.icon(siteRoot, path)
            // The shorthand a page writes the same icon with. It is the second spelling of the entry in front
            // of the user, and deriving it by hand is what this takes away.
            //
            // Carried as the type text, not as the tail text: the popup writes the tail directly behind the
            // name, while the type text is the column it aligns to its right edge. Grayed, because it is not
            // what is inserted here.
            presentation.setTypeText(MkDocsMaterialIconTree.shorthandOf(path), null)
            presentation.isTypeGrayed = true
        }
    }

    companion object {

        /**
         * How many entries at most reach the popup.
         *
         * A level is short as a rule, but not always: the `material` set alone holds several thousand icons
         * flat, and a popup measuring and sorting all of them takes about a second per keystroke. What is cut
         * away comes back as soon as the next letter narrows the list, because the completion is restarted.
         */
        private const val LIMIT = 100

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
