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
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Explains a Markdown extension listed under `markdown_extensions` in the quick documentation popup.
 *
 * The JSON schema of MkDocs describes the *shape* of that list, not what any single entry does — pressing
 * *Ctrl+Q* on `pymdownx.superfences` otherwise says nothing at all. What is offered here is the one line the
 * settings page shows for the same extension plus a link to its own documentation, because the question an
 * author has in front of that list is whether they need the entry, not how to spell it.
 *
 * Only entries of `markdown_extensions` are answered, and only extensions this plugin knows: anything else is
 * left to whoever else has something to say about it.
 */
class MkDocsMaterialExtensionDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val extension = extensionOf(element) ?: extensionOf(originalElement) ?: return null
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape(extension.id))
        builder.append(DocumentationMarkup.DEFINITION_END)
        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(escape(MkDocsBundle.message(extension.descriptionKey)))
        builder.append(DocumentationMarkup.CONTENT_END)
        builder.append(DocumentationMarkup.SECTIONS_START)
        extension.pipPackage?.let {
            appendSection(builder, MkDocsBundle.message("material.extension.doc.package"), escape(it))
        }
        appendSection(
            builder,
            MkDocsBundle.message("material.extension.doc.documentation"),
            "<a href=\"${escape(extension.docUrl)}\">${escape(extension.docUrl)}</a>",
        )
        builder.append(DocumentationMarkup.SECTIONS_END)
        return builder.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        extensionOf(element)?.let { "${it.id} — ${MkDocsBundle.message(it.descriptionKey)}" }

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
