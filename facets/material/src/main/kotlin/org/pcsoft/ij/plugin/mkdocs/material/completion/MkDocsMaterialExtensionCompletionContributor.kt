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
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.inspection.ExtensionDocElement
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Completes the Markdown extensions *Material for MkDocs* builds upon, under `markdown_extensions`.
 *
 * Deliberately a contributor rather than a branch of the refined JSON schema, although the list is static and a
 * schema is where a static list belongs. `markdown_extensions` is one key with two shapes — a sequence of names,
 * where an entry may itself be a mapping of name to options, and a single mapping of name to options — and the
 * base MkDocs schema already describes it as "an array or an object". Describing the values through that takes
 * a chain of `anyOf` branches below a key the base schema constrains as well, and the platform walks no values
 * out of it: measured, the popup stayed empty in every one of the three shapes. `theme.features`, whose values
 * sit directly on `items` of a plain array, is completed the schema way and stays there.
 *
 * Offered only where the shape says an extension is named, never where it says an option of one is:
 * * `- <caret>` and `- na<caret>me:` — an entry of the sequence, bare or carrying options;
 * * `<caret>` directly below `markdown_extensions:` — a key of the mapping form.
 *
 * A key one level deeper — `- toc:` and then `permalink` below it — configures the extension and is none of
 * this contributor's business.
 *
 * Nothing is filtered out: an extension already listed is offered again, because deciding otherwise would mean
 * reading the whole list on every keystroke to hide an entry the author is about to overwrite anyway.
 */
class MkDocsMaterialExtensionCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val file = position.containingFile?.originalFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(position.project, configFile)) return
        if (!namesAnExtension(position)) return

        result.addAllElements(service<MkDocsMaterialDataService>().extensions.all.map { element(it, position) })
    }

    /**
     * Returns the lookup element offering [extension].
     *
     * The package it comes from is shown as the type of the entry, so an extension asking for an installation
     * of its own can be told from one Python Markdown already ships.
     *
     * The description is deliberately not shown as the tail: a popup of two dozen entries, each carrying a
     * sentence, is read by nobody. It is one *Ctrl+Q* away, which is where a sentence belongs — and that key
     * only answers because the entry carries the element the documentation is generated for, instead of the
     * bare string the popup would otherwise be made of.
     *
     * @param extension the extension to offer
     * @param context the element completion was invoked at, which the documentation element hangs on
     */
    private fun element(extension: MkDocsMarkdownExtension, context: PsiElement): LookupElement =
        LookupElementBuilder.create(ExtensionDocElement(context, extension), extension.id)
            .withTypeText(extension.pipPackage ?: PYTHON_MARKDOWN, true)

    /**
     * Returns `true` if [position] sits where an extension is named.
     *
     * The decision is made on the chain of key value pairs above the caret. The nearest pair is
     * `markdown_extensions` itself wherever the caret is a value of it — an item of the sequence, and a key of
     * the mapping form, which YAML reads as a scalar below the key while it is still being typed.
     *
     * One step further up is the entry of a sequence that carries options, `- toc:`, where the extension is the
     * key of a pair of its own. That step is only taken while the caret is inside that key. It looks the same
     * from the chain alone as an option written below such an entry — `- toc:` and a bare word under it — and
     * there the caret is in the *value* of the pair and names no extension: measured, offering one there put
     * `pymdownx.superfences` where `permalink` belongs.
     *
     * @param position the element completion was invoked at
     */
    private fun namesAnExtension(position: PsiElement): Boolean {
        val nearest = position.parentOfType<YAMLKeyValue>() ?: return false
        if (nearest.keyText.trim() == MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS) return true

        val key = nearest.key ?: return false
        if (!PsiTreeUtil.isAncestor(key, position, false)) return false
        val above = nearest.parentOfType<YAMLKeyValue>() ?: return false
        return above.keyText.trim() == MkDocsMaterialConfig.KEY_MARKDOWN_EXTENSIONS
    }

    private companion object {

        /** The type shown for an extension no separate package has to be installed for. */
        const val PYTHON_MARKDOWN = "Python Markdown"
    }
}
