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

package org.pcsoft.ij.plugin.mkdocs.material.inspection

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtensionOption
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Explains a Markdown extension listed under `markdown_extensions` in the quick documentation popup.
 *
 * The JSON schema of MkDocs describes the *shape* of that list, not what any single entry does — pressing
 * *Ctrl+Q* on `pymdownx.superfences` otherwise says nothing at all, and on `permalink` below `- toc:` even
 * less, because no schema describes that level in the first place. What is offered here is the one line the
 * settings page shows for the same extension plus a link to its own documentation, and for an option what it
 * does, what it takes, and what the extension falls back to without it.
 *
 * Only entries of `markdown_extensions` are answered, and only extensions this plugin knows: anything else is
 * left to whoever else has something to say about it. An option is looked for first — it sits *inside* the
 * entry of its extension, so both questions would otherwise be answered with the extension.
 */
class MkDocsMaterialExtensionDocumentationProvider : AbstractDocumentationProvider() {

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager?,
        obj: Any?,
        element: PsiElement?,
    ): PsiElement? {
        val name = obj as? String ?: return null
        val context = element ?: return null
        val file = context.containingFile as? YAMLFile ?: return null
        if (!MkDocsProject.isConfigFile(file.name)) return null

        // The caret can sit in the value of the extension itself — the first option below `- toc:`, which YAML
        // has no key for yet — or in a pair of its own next to an option already written.
        if (!underMarkdownExtensions(context)) return null

        // An option is looked for first: it sits *inside* the entry of its extension, so an entry offered
        // there would otherwise be answered with whatever extension is written above it.
        val enclosing = context.parentOfType<YAMLKeyValue>()
        val extensionOfOption = enclosing?.let { extensionOfPair(it) ?: extensionOfPair(it.parentOfType<YAMLKeyValue>() ?: it) }
        extensionOfOption?.optionByKey(name)?.let { return OptionDocElement(context, extensionOfOption, it) }

        val extension = service<MkDocsMaterialDataService>().extensions.byId(name) ?: return null
        return ExtensionDocElement(context, extension)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        (element as? ExtensionDocElement)?.let { return generateExtensionDoc(it.extension) }
        (element as? OptionDocElement)?.let { return generateOptionDoc(it.extension, it.option) }

        // The option level is answered first, and answered exclusively: an element sitting below the entry of
        // an extension asks about the option it is written in, and falling back to the extension there would
        // explain something the author is not looking at — a name that is no option of it included.
        optionOf(element)?.let { return generateOptionDoc(it.first, it.second) }
        optionOf(originalElement)?.let { return generateOptionDoc(it.first, it.second) }
        if (optionContextOf(element) != null || optionContextOf(originalElement) != null) return null

        val extension = extensionOf(element) ?: extensionOf(originalElement) ?: return null
        return generateExtensionDoc(extension)
    }

    /**
     * Returns the extension an option written at [element] would belong to, or `null` if [element] sits at no
     * such place.
     *
     * The pair [element] sits in is the option; the pair above it names the extension. Both shapes of an entry
     * lead to the same place — the sequence item of `- toc:` is no pair of its own and is stepped over.
     *
     * @param element the element the popup was requested on
     */
    private fun optionContextOf(element: PsiElement?): Pair<MkDocsMarkdownExtension, String>? {
        if (element == null) return null
        val file = element.containingFile as? YAMLFile ?: return null
        if (!MkDocsProject.isConfigFile(file.name)) return null

        val pair = element as? YAMLKeyValue ?: element.parentOfType<YAMLKeyValue>() ?: return null
        val above = pair.parentOfType<YAMLKeyValue>() ?: return null
        val extension = extensionOfPair(above) ?: return null
        return extension to pair.keyText.trim()
    }

    /**
     * Returns the option [element] sits in together with its extension, or `null` if it sits in none.
     *
     * @param element the element the popup was requested on
     */
    private fun optionOf(element: PsiElement?): Pair<MkDocsMarkdownExtension, MkDocsMarkdownExtensionOption>? {
        val (extension, key) = optionContextOf(element) ?: return null
        val option = extension.optionByKey(key) ?: return null
        return extension to option
    }

    /**
     * Returns the popup explaining [extension].
     *
     * @param extension the extension to explain
     */
    private fun generateExtensionDoc(extension: MkDocsMarkdownExtension): String {
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape(extension.id))
        builder.append(DocumentationMarkup.DEFINITION_END)
        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(escape(MkDocsMaterialBundle.message(extension.descriptionKey)))
        builder.append(DocumentationMarkup.CONTENT_END)
        builder.append(DocumentationMarkup.SECTIONS_START)
        extension.pipPackage?.let {
            appendSection(builder, MkDocsMaterialBundle.message("material.extension.doc.package"), escape(it))
        }
        appendSection(
            builder,
            MkDocsMaterialBundle.message("material.extension.doc.documentation"),
            "<a href=\"${escape(extension.docUrl)}\">${escape(extension.docUrl)}</a>",
        )
        builder.append(DocumentationMarkup.SECTIONS_END)
        return builder.toString()
    }

    /**
     * Returns the extension [pair] names, or `null` if it names something else.
     *
     * The pair has to sit directly below `markdown_extensions`, which is the nearest enclosing pair in both
     * shapes an entry can have: a sequence item is no pair of its own and is stepped over.
     *
     * @param pair the key value pair to read
     */
    private fun extensionOfPair(pair: YAMLKeyValue): MkDocsMarkdownExtension? {
        val above = pair.parentOfType<YAMLKeyValue>() ?: return null
        if (above.keyText.trim() != KEY_MARKDOWN_EXTENSIONS) return null
        return service<MkDocsMaterialDataService>().extensions.byId(pair.keyText.trim())
    }

    /**
     * Returns `true` if [element] sits anywhere below `markdown_extensions`.
     *
     * Asked while the popup is open, where the entry being offered is not in the file yet: the chain of pairs
     * above the caret is all there is to decide on, and the exact shape of the entry is still being typed.
     *
     * @param element the element completion was invoked at
     */
    private fun underMarkdownExtensions(element: PsiElement): Boolean {
        var pair = element.parentOfType<YAMLKeyValue>()
        while (pair != null) {
            if (pair.keyText.trim() == KEY_MARKDOWN_EXTENSIONS) return true
            pair = pair.parentOfType<YAMLKeyValue>()
        }
        return false
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return extensionOf(element)?.let { "${it.id} — ${MkDocsMaterialBundle.message(it.descriptionKey)}" }
    }

    /**
     * Returns the popup explaining [option] of [extension].
     *
     * The extension is named in the definition as well as in a row of its own: the option alone says nothing
     * about where it belongs, and the same name is an option of more than one extension.
     *
     * @param extension the extension the option belongs to
     * @param option the option to explain
     */
    private fun generateOptionDoc(
        extension: MkDocsMarkdownExtension,
        option: MkDocsMarkdownExtensionOption,
    ): String {
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape("${extension.id}.${option.key}"))
        builder.append(DocumentationMarkup.DEFINITION_END)
        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(escape(MkDocsMaterialBundle.message(option.descriptionKey)))
        builder.append(DocumentationMarkup.CONTENT_END)
        builder.append(DocumentationMarkup.SECTIONS_START)
        appendSection(
            builder,
            MkDocsMaterialBundle.message("material.extension.doc.option.extension"),
            escape(extension.id),
        )
        appendSection(
            builder,
            MkDocsMaterialBundle.message("material.extension.doc.option.type"),
            escape(option.kind.name.lowercase()),
        )
        if (option.values.isNotEmpty()) {
            appendSection(
                builder,
                MkDocsMaterialBundle.message("material.extension.doc.option.values"),
                escape(option.values.joinToString(", ")),
            )
        }
        option.defaultValue?.let {
            appendSection(builder, MkDocsMaterialBundle.message("material.extension.doc.option.default"), escape(it))
        }
        option.recommendedValue?.let {
            appendSection(
                builder,
                MkDocsMaterialBundle.message("material.extension.doc.option.recommended"),
                escape(it),
            )
        }
        builder.append(DocumentationMarkup.SECTIONS_END)
        return builder.toString()
    }

    /**
     * Appends one labelled row to the sections table of the popup.
     *
     * @param builder the document being built
     * @param label the text of the left column
     * @param value the HTML of the right column
     */
    private fun appendSection(builder: StringBuilder, label: String, value: String) {
        builder.append(DocumentationMarkup.SECTION_HEADER_START)
        builder.append(escape(label))
        builder.append(DocumentationMarkup.SECTION_SEPARATOR)
        builder.append(value)
        builder.append(DocumentationMarkup.SECTION_END)
    }

    /**
     * Returns [text] with the characters that carry a meaning in the popup replaced by their entities.
     *
     * The descriptions are prose written by hand and contain quotes and angle brackets; without this they
     * would end up as markup of the popup rather than as the text they are.
     *
     * @param text the raw text
     */
    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

    /**
     * Returns the extension [element] sits in, or `null` if it is not an entry of `markdown_extensions`.
     *
     * Both shapes an entry can have are covered: the scalar `- admonition`, and the mapping
     * `- pymdownx.highlight:` carrying the options of the extension below it.
     *
     * @param element the element the popup was requested on
     */
    private fun extensionOf(element: PsiElement?): MkDocsMarkdownExtension? {
        if (element == null) return null
        val file = element.containingFile as? YAMLFile ?: return null
        if (!MkDocsProject.isConfigFile(file.name)) return null

        val item = element as? YAMLSequenceItem ?: element.parentOfType<YAMLSequenceItem>() ?: return null
        val owner = (item.parent?.parent as? YAMLKeyValue) ?: return null
        if (owner.keyText.trim() != KEY_MARKDOWN_EXTENSIONS) return null

        val id = when (val value = item.value) {
            is YAMLScalar -> value.textValue.trim()
            else -> item.keysValues.firstOrNull()?.keyText?.trim()
        } ?: return null
        return service<MkDocsMaterialDataService>().extensions.byId(id)
    }

    private companion object {

        /** The top level key whose entries this provider explains. */
        const val KEY_MARKDOWN_EXTENSIONS: String = "markdown_extensions"
    }
}
