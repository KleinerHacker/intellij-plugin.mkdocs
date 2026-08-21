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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Marks a path value of an MkDocs configuration file whose target does not exist.
 *
 * The file references of [MkDocsPathReferenceContributor] already know the answer — they are hard for every
 * kind but [MkDocsPathKind.soft] — but YAML has no highlight visitor that draws an unresolved reference, so
 * the knowledge never reached the editor: a `docs_dir` naming a directory nobody created, a `nav` entry
 * pointing at a page that was renamed and an `extra_css` whose style sheet was deleted all stayed black. This
 * annotator paints them the way every other language paints an unresolved reference, and offers
 * [MkDocsCreatePathIntention] next to the mark.
 *
 * Only the first segment that leads nowhere is reported. A value like `nav/old/page.md` whose `old` is gone
 * has three unresolved segments, and marking all of them would say three times what is one mistake — the
 * first one is where the path stops being true.
 *
 * Three cases are left alone, each because the report would be wrong rather than unwelcome:
 *
 * * a [MkDocsPathKind.soft] kind — `site_dir` is build output and does not exist before the first build
 * * a value [MkDocsPathValidator] already reports as an error — an absolute path or one climbing out of the
 *   site cannot resolve *because* of that, and saying so a second time adds nothing
 * * a `theme.logo` or `theme.favicon` carrying no extension — the Material theme accepts the name of one of
 *   its own icons there, `material/library`, which is no path at all
 */
class MkDocsMissingPathAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotators visit every element of the file; the scalars are the only ones carrying a path.
        val scalar = element as? YAMLScalar ?: return
        val kind = MkDocsPathKind.of(scalar) ?: return
        if (kind.soft) return

        // The value range keeps the quotes of a quoted scalar out of the checked text.
        val path = ElementManipulators.getValueTextRange(scalar).substring(scalar.text)
        if (path.isBlank()) return
        if (isIconName(kind, path)) return
        if (MkDocsPathValidator.validate(path).any { it.problem.isError(SystemInfo.isWindows) }) return

        val references = scalar.references
            .filterIsInstance<MkDocsPathReference>()
            .sortedBy { it.rangeInElement.startOffset }
        val reference = references.firstOrNull { it.multiResolve(false).isEmpty() } ?: return
        if (reference.rangeInElement.isEmpty) return

        // Everything but the last segment of a path is a directory, whatever the key reads the value as:
        // "assets/logo.svg" names a file, but its "assets" is a directory and has to be reported as one.
        val directory = kind.directory || reference !== references.last()
        val key = if (directory) "reference.problem.missingDirectory" else "reference.problem.missingFile"
        holder.newAnnotation(HighlightSeverity.ERROR, MkDocsBundle.message(key, reference.canonicalText))
            .range(reference.rangeInElement.shiftRight(scalar.textRange.startOffset))
            .textAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
            .withFix(MkDocsCreatePathIntention())
            .create()
    }

    /**
     * Returns `true` if [path] is the name of a theme icon rather than a path.
     *
     * Only `theme.logo` and `theme.favicon` accept one, and it is told apart from a path by carrying no
     * extension: an icon is addressed as `material/library`, an image always as `assets/logo.svg`.
     *
     * @param kind the key the value belongs to
     * @param path the value as written
     */
    private fun isIconName(kind: MkDocsPathKind, path: String): Boolean {
        if (kind != MkDocsPathKind.LOGO && kind != MkDocsPathKind.FAVICON) return false
        return !path.substringAfterLast('/').contains('.')
    }
}
