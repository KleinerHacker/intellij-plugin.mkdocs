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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Explains a value of `theme.palette` in the quick documentation popup.
 *
 * The generated schema carries the one line description of a colour and of a scheme on the value it *offers*,
 * which is what the completion popup shows — but pressing *Ctrl+Q* on a value already written into the file
 * asks the same question again, and the schema has no answer for it there. A written `primary: deep-purple`
 * then says nothing at all, while the very same name explains itself one keystroke earlier.
 *
 * What is answered is what the value stands for: the description of the colour or of the scheme, the role it
 * plays — the primary colour is a different set from the accent colour — and, for a colour, the shade the
 * swatch is painted in, so the name can be checked against something concrete.
 *
 * Only the values of `theme.palette` are answered, and only the ones this plugin knows. The media query is
 * deliberately left out: it is an ordinary CSS media query and the browser, not the theme, decides what it
 * means.
 */
class MkDocsMaterialPaletteDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        documentOf(element) ?: documentOf(originalElement)

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val value = valueOf(element) ?: valueOf(originalElement) ?: return null
        return "${value.name} — ${MkDocsMaterialBundle.message(value.descriptionKey)}"
    }

    /**
     * Returns the popup explaining the value [element] sits in, or `null` if it is none of the palette's.
     *
     * @param element the element the popup was requested on
     */
    private fun documentOf(element: PsiElement?): String? {
        val value = valueOf(element) ?: return null
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(escape(value.name))
        builder.append(DocumentationMarkup.DEFINITION_END)
        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(escape(MkDocsMaterialBundle.message(value.descriptionKey)))
        builder.append(DocumentationMarkup.CONTENT_END)
        builder.append(DocumentationMarkup.SECTIONS_START)
        appendSection(builder, MkDocsMaterialBundle.message("material.palette.doc.role"), escape(value.role))
        value.shade?.let {
            appendSection(builder, MkDocsMaterialBundle.message("material.palette.doc.shade"), escape(it))
        }
        builder.append(DocumentationMarkup.SECTIONS_END)
        return builder.toString()
    }

    /**
     * Returns what [element] stands for, or `null` if it is no value of `theme.palette` this plugin knows.
     *
     * A value naming neither a colour of the role nor a scheme is left alone: it is either a typo, which the
     * schema reports on its own, or something a later version of the theme brought along — and inventing a
     * description for it would state something nobody checked.
     *
     * @param element the element the popup was requested on
     */
    private fun valueOf(element: PsiElement?): Value? {
        if (element == null) return null
        val file = element.containingFile as? YAMLFile ?: return null
        if (!MkDocsProject.isConfigFile(file.name)) return null

        val scalar = element as? YAMLScalar ?: element.parentOfType<YAMLScalar>() ?: return null
        val role = MkDocsMaterialPaletteKeys.roleOf(scalar) ?: return null
        val text = scalar.textValue.trim()
        if (text.isEmpty()) return null

        return when (role) {
            MkDocsMaterialPaletteKeys.Role.PRIMARY, MkDocsMaterialPaletteKeys.Role.ACCENT -> {
                val color = MkDocsMaterialPaletteKeys.colorOf(role, text) ?: return null
                Value(
                    name = color.id,
                    descriptionKey = color.descriptionKey,
                    role = MkDocsMaterialBundle.message(roleKeyOf(role)),
                    // The exact shade the theme compiles is none of this preview's business, which is why the
                    // row says what the swatch is painted in rather than what the style sheet will hold.
                    shade = if (color.custom) null else "#%06X".format(color.hex),
                )
            }

            MkDocsMaterialPaletteKeys.Role.SCHEME -> {
                val scheme = MkDocsMaterialPaletteKeys.schemeOf(text) ?: return null
                Value(
                    name = scheme.id,
                    descriptionKey = scheme.descriptionKey,
                    role = MkDocsMaterialBundle.message("material.palette.doc.role.scheme"),
                    shade = null,
                )
            }

            MkDocsMaterialPaletteKeys.Role.MEDIA -> null
        }
    }

    /**
     * Returns the bundle key naming [role].
     *
     * @param role the colour role the value plays
     */
    private fun roleKeyOf(role: MkDocsMaterialPaletteKeys.Role): String =
        if (role == MkDocsMaterialPaletteKeys.Role.PRIMARY) {
            "material.palette.color.type.primary"
        } else {
            "material.palette.color.type.accent"
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
     * @param text the raw text
     */
    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

    /**
     * What a value of `theme.palette` stands for.
     *
     * @property name the identifier as the file writes it
     * @property descriptionKey the bundle key of the one line description
     * @property role the text naming what the value is used as
     * @property shade the shade the swatch is painted in, or `null` where there is none
     */
    private data class Value(
        val name: String,
        val descriptionKey: String,
        val role: String,
        val shade: String?,
    )
}
