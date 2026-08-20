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
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Completes the media queries a palette of *Material for MkDocs* is chosen by.
 *
 * The JSON schema describes `theme.palette.media` as a plain string, and rightly so: the theme passes the
 * value to the `media` attribute of the style sheet it renders, so every valid CSS media query is allowed
 * there. What the theme is actually built around are three of them, and those three are what an author is
 * looking for — written by hand today, from memory or from the documentation, with the colon and the
 * parentheses that make the value easy to get wrong.
 *
 * The entries are therefore offered rather than enforced. A query none of them matches stays a legal value;
 * that it will most likely not do what the author meant is what
 * `MkDocsMaterialPaletteMediaInspection` says, as a warning that can be switched off.
 *
 * The value has to be quoted: `(prefers-color-scheme: light)` carries a colon followed by a space, which ends
 * a plain scalar in YAML. The insert handler puts the quotes there wherever the author has not.
 */
class MkDocsMaterialPaletteMediaCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!MkDocsMaterialPaletteKeys.isMediaValue(position)) return

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(position.project, configFile)) return

        // Neither the parentheses nor the colon nor the space of the query is part of an identifier, so the
        // platform would match the entries against an empty prefix and offer all of them on every keystroke.
        val prefixed = result.withPrefixMatcher(typedOf(parameters))
        prefixed.addAllElements(
            MkDocsMaterialPaletteKeys.MEDIA_QUERIES.map { media ->
                LookupElementBuilder.create(media.query)
                    // The popup at this place also carries whatever the schema offers for a string, and the
                    // icon is what says these three come from the theme.
                    .withIcon(MkDocsMaterialIcons.Badge)
                    .withTypeText(MkDocsMaterialBundle.message("material.palette.media.type"), true)
                    .withTailText("  ${MkDocsMaterialBundle.message(media.descriptionKey)}", true)
                    .withInsertHandler(QUOTING)
            }
        )
    }

    /**
     * Returns what has been written of the query in front of the caret.
     *
     * Read off the value range of the scalar rather than off the document: the query holds characters that
     * end a word — a space, a colon, a parenthesis — so walking backwards over "word characters" would stop
     * inside it and match the entries against a fragment. The quotes around the value are not part of that
     * range, which is exactly what is wanted; the entries carry no quotes either.
     *
     * @param parameters what completion was invoked with
     */
    private fun typedOf(parameters: CompletionParameters): String {
        val scalar = parameters.position.parentOfType<YAMLScalar>(withSelf = true) ?: return ""
        val value = ElementManipulators.getValueTextRange(scalar).shiftRight(scalar.textRange.startOffset)
        val caret = parameters.offset
        if (caret <= value.startOffset || caret > value.endOffset) return ""
        return parameters.editor.document.charsSequence.subSequence(value.startOffset, caret).toString()
    }

    private companion object {

        /**
         * Wraps the inserted query in quotes wherever the file does not carry them already.
         *
         * `media: (prefers-color-scheme: light)` is not the value it looks like — YAML reads the colon and the
         * space behind it as the start of a mapping, and the file stops parsing. A quote the author has
         * already typed is kept as it is, opening and closing one judged on their own: completion invoked
         * behind a lone opening quote has to close it.
         */
        val QUOTING = InsertHandler<LookupElement> { context, _ ->
            val document = context.document
            val text = document.charsSequence
            val start = context.startOffset
            val tail = context.tailOffset
            val opened = start > 0 && isQuote(text[start - 1])
            val closed = tail < text.length && isQuote(text[tail])

            if (!closed) document.insertString(tail, "\"")
            if (!opened) document.insertString(start, "\"")
            // Behind the closing quote, which is one character wide and sits at the end of what was inserted.
            context.editor.caretModel.moveToOffset(tail + (if (opened) 0 else 1) + 1)
        }

        /**
         * Returns `true` if [character] is a quote YAML wraps a scalar in.
         *
         * @param character the character to judge
         */
        fun isQuote(character: Char): Boolean = character == '"' || character == '\''
    }
}
