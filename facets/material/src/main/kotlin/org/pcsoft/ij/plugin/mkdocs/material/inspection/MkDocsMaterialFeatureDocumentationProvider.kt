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
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureFlag
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Explains a feature flag listed under `theme.features` in the quick documentation popup.
 *
 * The generated schema carries the one line description of a flag on the value it *offers*, which is what the
 * completion popup shows — but pressing *Ctrl+Q* on a flag already written into the file asks the same
 * question again, and the schema has no answer for it there. What is offered here is that description plus
 * what the settings page knows on top of it: the section of the page the flag changes, the flags it needs or
 * clashes with, the Markdown extensions it forces, whether it needs an *Insiders* build, and a link into the
 * documentation of the theme.
 *
 * Only entries of `theme.features` are answered, and only flags this plugin knows: anything else is left to
 * whoever else has something to say about it.
 */
class MkDocsMaterialFeatureDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val flag = flagOf(element) ?: flagOf(originalElement) ?: return null
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape(flag.id))
        builder.append(DocumentationMarkup.DEFINITION_END)
        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(escape(MkDocsMaterialBundle.message(flag.descriptionKey)))
        builder.append(DocumentationMarkup.CONTENT_END)
        builder.append(DocumentationMarkup.SECTIONS_START)
        appendSection(
            builder,
            MkDocsMaterialBundle.message("material.feature.doc.group"),
            escape(MkDocsMaterialBundle.message(flag.group.titleKey)),
        )
        if (flag.insiders) {
            appendSection(
                builder,
                MkDocsMaterialBundle.message("material.feature.doc.insiders"),
                escape(MkDocsMaterialBundle.message("material.page.features.insiders")),
            )
        }
        appendIds(builder, "material.feature.doc.requires", flag.requires)
        appendIds(builder, "material.feature.doc.conflicts", conflictsOf(flag))
        appendIds(builder, "material.feature.doc.extensions", flag.requiredExtensions)
        if (flag.docUrl.isNotBlank()) {
            appendSection(
                builder,
                MkDocsMaterialBundle.message("material.feature.doc.documentation"),
                "<a href=\"${escape(flag.docUrl)}\">${escape(flag.docUrl)}</a>",
            )
        }
        builder.append(DocumentationMarkup.SECTIONS_END)
        return builder.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        flagOf(element)?.let { "${it.id} — ${MkDocsMaterialBundle.message(it.descriptionKey)}" }

    /**
     * Appends a row listing [ids], or nothing at all when there are none.
     *
     * @param builder the document being built
     * @param labelKey the bundle key of the text of the left column
     * @param ids the identifiers of the right column
     */
    private fun appendIds(builder: StringBuilder, labelKey: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        appendSection(
            builder,
            MkDocsMaterialBundle.message(labelKey),
            escape(ids.sorted().joinToString(", ")),
        )
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
     * The flags [flag] cannot be combined with, in both directions.
     *
     * The resource declares a pair on one side only, and which side that is must not decide whether the popup
     * mentions the conflict.
     *
     * @param flag the flag being explained
     */
    private fun conflictsOf(flag: MkDocsMaterialFeatureFlag): Set<String> =
        service<MkDocsMaterialDataService>().featureFlags.conflictsOf(flag).map { it.id }.toSet()

    /**
     * Returns the feature flag [element] sits in, or `null` if it is not an entry of `theme.features`.
     *
     * The parent key is checked as well: `features` alone is a word another key of another block may carry,
     * and only the one below `theme` is the theme's own list.
     *
     * @param element the element the popup was requested on
     */
    private fun flagOf(element: PsiElement?): MkDocsMaterialFeatureFlag? {
        if (element == null) return null
        val file = element.containingFile as? YAMLFile ?: return null
        if (!MkDocsProject.isConfigFile(file.name)) return null

        val item = element as? YAMLSequenceItem ?: element.parentOfType<YAMLSequenceItem>() ?: return null
        val owner = (item.parent?.parent as? YAMLKeyValue) ?: return null
        if (owner.keyText.trim() != KEY_FEATURES) return null
        val theme = owner.parentOfType<YAMLKeyValue>() ?: return null
        if (theme.keyText.trim() != KEY_THEME) return null

        val id = (item.value as? YAMLScalar)?.textValue?.trim() ?: return null
        return service<MkDocsMaterialDataService>().featureFlags.byId(id)
    }

    private companion object {

        /** The key holding the list this provider explains. */
        const val KEY_FEATURES: String = "features"

        /** The block that key has to sit in. */
        const val KEY_THEME: String = "theme"
    }
}
