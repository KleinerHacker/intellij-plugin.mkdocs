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
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtensionOption
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtensionOptionKind
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Completes the options of a Markdown extension, written below its entry under `markdown_extensions`.
 *
 * The level [MkDocsMaterialExtensionCompletionContributor] deliberately stays out of: `- toc:` and then
 * `permalink` below it configures the extension, and neither the base MkDocs schema nor the refined schema of
 * the facet describes what any single extension accepts there — the popup was empty at that place.
 *
 * Two things are offered, told apart by the key the caret sits under:
 * * the option names, wherever the enclosing key names an extension this plugin knows — both in the sequence
 *   form `- toc:` and in the mapping form `toc:`;
 * * the values of an option, wherever the enclosing key names an option of such an extension and the option
 *   takes a fixed set of them: `true` and `false` for a flag, the listed values for a choice.
 *
 * Nothing is filtered out: an option already written is offered again, for the same reason the extensions
 * themselves are — hiding it would mean reading the whole mapping on every keystroke.
 */
class MkDocsMaterialExtensionOptionCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(position.project, configFile)) return

        val owner = position.parentOfType<YAMLKeyValue>() ?: return
        if (insideKeyOf(position, owner)) {
            // A pair of its own — an option written next to one already there.
            val extension = extensionOf(owner.parentOfType<YAMLKeyValue>() ?: return) ?: return
            result.addAllElements(extension.options.map { element(extension, it, position) })
            return
        }

        val extension = extensionOf(owner)
        if (extension != null) {
            // The first option below the entry, which YAML still reads as the value of the extension.
            result.addAllElements(extension.options.map { element(extension, it, position) })
            return
        }

        val option = optionOf(owner) ?: return
        result.addAllElements(valuesOf(option).map { LookupElementBuilder.create(it) })
    }

    /**
     * Returns the lookup element offering [option].
     *
     * The kind of value the option takes is shown as the type of the entry, the same way the package of an
     * extension is one level above. What the option does is left to *Ctrl+Q*: a popup whose every entry carries
     * a sentence is read by nobody. That key only answers because the entry carries the element the
     * documentation is generated for, instead of the bare string the popup would otherwise be made of.
     *
     * @param extension the extension the option belongs to
     * @param option the option to offer
     * @param context the element completion was invoked at, which the documentation element hangs on
     */
    private fun element(
        extension: MkDocsMarkdownExtension,
        option: MkDocsMarkdownExtensionOption,
        context: PsiElement,
    ): LookupElement =
        LookupElementBuilder.create(option.key)
            .withTypeText(option.kind.name.lowercase(), true)
            .withInsertHandler { context, _ -> insertSeparator(context, option) }

    /**
     * Writes the `: ` behind the accepted option and the value it is worth starting with, if there is one.
     *
     * An option is never written alone — YAML would read the bare word as a value of the extension — so the
     * separator belongs to the insertion. The value that follows it is the one the theme recommends, or the one
     * the extension falls back to; without either the caret is simply left behind the separator.
     *
     * A pair the author has already opened keeps its own separator: the completion replaced the key of it, and
     * a second colon would break the very line it completed.
     *
     * @param context the insertion being performed
     * @param option the accepted option
     */
    private fun insertSeparator(context: InsertionContext, option: MkDocsMarkdownExtensionOption) {
        if (context.document.charsSequence.getOrNull(context.tailOffset) == ':') return

        val value = option.recommendedValue ?: option.defaultValue
        val text = if (value == null) ": " else ": $value"
        context.document.insertString(context.tailOffset, text)
        context.editor.caretModel.moveToOffset(context.tailOffset + text.length)
    }

    /**
     * Returns the values [option] accepts, or an empty list if they are not a fixed set.
     *
     * @param option the option the caret sits in the value of
     */
    private fun valuesOf(option: MkDocsMarkdownExtensionOption): List<String> = when (option.kind) {
        MkDocsMarkdownExtensionOptionKind.BOOLEAN -> BOOLEAN_VALUES
        MkDocsMarkdownExtensionOptionKind.ENUM -> option.values
        else -> emptyList()
    }

    /**
     * Returns `true` if [position] sits in the key of [owner] rather than in its value.
     *
     * Which of the two it is decides what is offered: a key names an option, a value carries one. While the
     * first option below an entry is being typed there is no key yet at all — YAML reads the bare word as the
     * value of the extension — which is why the value side is answered with the option names as well.
     *
     * @param position the element completion was invoked at
     * @param owner the key value pair [position] sits in
     */
    private fun insideKeyOf(position: PsiElement, owner: YAMLKeyValue): Boolean {
        val key = owner.key ?: return false
        return PsiTreeUtil.isAncestor(key, position, false)
    }

    /**
     * Returns the extension [owner] names, or `null` if it names something else.
     *
     * The pair has to sit below `markdown_extensions`, which is the nearest enclosing pair in both shapes an
     * entry can have: a sequence item is no pair of its own, so the step from `- toc:` leads to the same key as
     * the step from the mapping form.
     *
     * @param owner the key value pair to read
     */
    private fun extensionOf(owner: YAMLKeyValue): MkDocsMarkdownExtension? {
        val above = owner.parentOfType<YAMLKeyValue>() ?: return null
        if (above.keyText.trim() != MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS) return null
        return service<MkDocsMaterialDataService>().extensions.byId(owner.keyText.trim())
    }

    /**
     * Returns the option [owner] names, or `null` if it names something else.
     *
     * @param owner the key value pair to read
     */
    private fun optionOf(owner: YAMLKeyValue): MkDocsMarkdownExtensionOption? {
        val extension = extensionOf(owner.parentOfType<YAMLKeyValue>() ?: return null) ?: return null
        return extension.optionByKey(owner.keyText.trim())
    }

    private companion object {

        /** The values a flag accepts. */
        val BOOLEAN_VALUES: List<String> = listOf("true", "false")
    }
}
