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
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconTree

/**
 * Completes the icon shorthands of *Material for MkDocs* inside the pages of a site.
 *
 * The theme lets a page write `:material-check:`, `:fontawesome-brands-github:` or `:octicons-repo-16:` and
 * renders the icon of that name inline. Which shorthands exist follows directly from the installed icon sets:
 * the name is the path below the sets with its slashes turned into dashes.
 *
 * Offered one level at a time, the same way as in `mkdocs.yml`: the sets first, then what lies below the
 * chosen one. A set is offered as the beginning of a shorthand — `:material-` — and re-opens the popup once
 * it is taken. What decides the levels is [MkDocsMaterialIconTree], on the names rather than on the written
 * dashes: a dash is part of many an icon name, so the written text is matched against the known sets instead
 * of being split at every one of them.
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
        val index = project.service<MkDocsMaterialIconIndex>()
        val names = index.names(siteRoot)
        if (names.isEmpty()) return

        // The colon opening a shorthand is not part of an identifier, so the platform would prefix-match the
        // entries against nothing at all and offer every one of them on every keystroke.
        val typed = prefixOf(parameters)
        val prefixed = result.withPrefixMatcher(typed)
        val level = MkDocsMaterialIconTree.childrenOf(names, groupOf(names, typed))
        val matching = elements(index, siteRoot, level)
            .filter { prefixed.prefixMatcher.prefixMatches(it) }
        val offered = matching.take(LIMIT)
        prefixed.addAllElements(offered)
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
     * Returns the group the written shorthand [typed] stands in, empty for the level of the sets.
     *
     * Matched against the known groups rather than split at the dashes: `material-weather-sunny` carries
     * three of them and only the first one separates a set from a name.
     *
     * @param names the names the installed theme offers
     * @param typed what has been written of the shorthand, the opening colon included
     */
    private fun groupOf(names: Collection<String>, typed: String): String {
        val bare = typed.trimStart(SHORTHAND_MARK)
        return MkDocsMaterialIconTree.groups(names)
            .filter { bare.startsWith(shorthandPrefixOf(it)) }
            .maxByOrNull { it.length }
            .orEmpty()
    }

    /**
     * Returns how a shorthand of an icon below the group [path] begins.
     *
     * @param path the group path, as the theme addresses it
     */
    private fun shorthandPrefixOf(path: String): String =
        path.replace(MkDocsMaterialIconTree.SEPARATOR, SEGMENT_MARK) + SEGMENT_MARK

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
            entries.filterNot { it.group }
                .associate { MkDocsMaterialShorthands.shorthandOf(it.path) to it.path },
        )
        return entries.map { entry ->
            val text = if (entry.group) {
                "$SHORTHAND_MARK${shorthandPrefixOf(entry.path)}"
            } else {
                MkDocsMaterialShorthands.shorthandOf(entry.path)
            }
            LookupElementBuilder.create(text)
                .withRenderer(renderer)
                .let { if (entry.group) it.withInsertHandler(NextLevel) else it }
        }
    }

    /**
     * Opens the popup again once a set was taken, so the icons below it follow at once.
     *
     * Without it the user would have to ask for the completion again after every set, which is what makes a
     * walk through the sets feel like one step per level rather than one movement.
     */
    private object NextLevel : InsertHandler<LookupElement> {

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
    }

    /**
     * Paints an entry of one level.
     *
     * The drawing stays the icon of the entry — that is what the author is picking from — and the mark of the
     * theme is badged onto it. A Markdown file mixes the shorthands of the theme with everything else the
     * editor offers, and without the badge nothing in the popup says that this syntax is the theme's.
     *
     * One instance for the whole level rather than one per entry, and the name behind a shorthand is read out
     * of a map rather than searched for: a set holds several thousand entries, and the popup renders every one
     * of them that matches.
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
                // A set. It draws nothing of its own — what lies below it does — so it carries the folder
                // of this plugin, badged with the mark of the theme.
                presentation.icon = MkDocsMaterialIcons.Group
                presentation.typeText = MkDocsMaterialBundle.message("material.icon.completion.group")
                presentation.isTypeGrayed = true
                return
            }
            presentation.icon = index.icon(siteRoot, path)
                ?.let { MkDocsIconLoader.withBadge(it, MkDocsMaterialIcons.Overlay) }
                ?: MkDocsMaterialIcons.Badge
        }
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
        if (start > 0 && text[start - 1] == SHORTHAND_MARK) start--
        return text.subSequence(start, parameters.offset).toString()
    }

    /**
     * Returns `true` if [character] can appear inside the name of a shorthand.
     *
     * @param character the character to judge
     */
    private fun isShorthandCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == SEGMENT_MARK || character == '_'

    private companion object {

        /** What opens and closes a shorthand. */
        const val SHORTHAND_MARK = ':'

        /** What separates the segments of a name inside a shorthand. */
        const val SEGMENT_MARK = '-'

        /**
         * How many entries at most reach the popup.
         *
         * A level is short as a rule, but not always: the `material` set alone holds several thousand icons
         * flat, and a popup measuring and sorting all of them takes about a second per keystroke. What is cut
         * away comes back as soon as the next letter narrows the list, because the completion is restarted.
         */
        const val LIMIT = 100
    }
}
