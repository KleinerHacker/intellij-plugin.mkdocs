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

package org.pcsoft.ij.plugin.mkdocs.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.reference.MkDocsPathKind

/**
 * Reports a path value of an MkDocs configuration file naming a file of a type the key cannot use.
 *
 * Some keys read a path and do something specific with what they find: `extra_css` writes a `<link>` into
 * every page, `extra_javascript` a `<script>`, and `theme.logo` and `theme.favicon` end up in an `<img>` and
 * in the icon of the browser tab. A Markdown page behind any of them is a file that exists, so the references
 * resolve it happily and nothing is marked — while the built site silently loads a page as a style sheet.
 * That gap is what this inspection closes.
 *
 * It is a separate check rather than one more entry of the path check next to the references, because it is
 * the one finding whose answer is a matter of taste in the corners: a project generating its style sheet into
 * a file without the usual extension is doing something unusual, not something broken, and should be able to
 * switch the report off without losing the character check of the same value.
 *
 * Only a value that *has* an extension is judged. A value without one is left alone on purpose: below
 * `theme.logo` and `theme.favicon` the Material theme accepts the name of one of its own icons —
 * `material/library` — which is no path at all, and reporting it would mark a perfectly ordinary
 * configuration.
 */
class MkDocsPathFileTypeInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {

            override fun visitElement(element: PsiElement) {
                val scalar = element as? YAMLScalar ?: return
                val kind = MkDocsPathKind.of(scalar) ?: return
                if (kind.fileExtensions.isEmpty()) return

                // The value range keeps the quotes of a quoted scalar out of the path, and gives the offset
                // the reported range has to be shifted by so it lands on the extension itself.
                val valueRange = ElementManipulators.getValueTextRange(scalar)
                val path = valueRange.substring(scalar.text)
                val name = path.substringAfterLast('/').substringAfterLast('\\')
                val extension = name.substringAfterLast('.', "")
                if (extension.isEmpty() || kind.accepts(name)) return

                val end = valueRange.startOffset + path.length
                holder.registerProblem(
                    scalar,
                    TextRange(end - extension.length - 1, end),
                    MkDocsBundle.message("inspection.pathFileType.problem", typeOf(kind), expectedOf(kind)),
                )
            }
        }

    /**
     * Returns what the key of [kind] makes of the file it points at, as the text the message names it with.
     *
     * @param kind the key the value belongs to
     */
    private fun typeOf(kind: MkDocsPathKind): String = MkDocsBundle.message(
        when (kind) {
            MkDocsPathKind.EXTRA_CSS -> "inspection.pathFileType.type.extraCss"
            MkDocsPathKind.EXTRA_JAVASCRIPT -> "inspection.pathFileType.type.extraJavascript"
            MkDocsPathKind.FAVICON -> "inspection.pathFileType.type.favicon"
            else -> "inspection.pathFileType.type.logo"
        }
    )

    /**
     * Returns the extensions [kind] accepts, spelled the way a file name carries them.
     *
     * @param kind the key the value belongs to
     */
    private fun expectedOf(kind: MkDocsPathKind): String =
        kind.fileExtensions.sorted().joinToString(", ") { ".$it" }
}
