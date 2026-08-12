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
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Marks a path in an MkDocs configuration file that no file system will read the way it is meant.
 *
 * This is the companion of the file references: those say whether a path leads anywhere, this one says
 * whether it *can*. The two answers are separate — a path may name a file that exists perfectly well on the
 * machine it was written on and still break the moment the site is built somewhere else, which is exactly
 * what a `Nav\Page.md` or a `docs/Übersicht.md` does.
 *
 * The severity follows the operating system the IDE runs on, not the union of all of them: what is broken
 * here is an error, what is broken elsewhere a warning. Marking a Windows-only problem as an error on Linux
 * would claim the file does not work when it demonstrably does.
 *
 * The path check runs on `site_dir` as well, although the references leave that key alone: whether the build
 * output directory exists yet is nobody's business, but whether MkDocs can create it is.
 */
class MkDocsPathAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotators visit every element of the file; the scalars are the only ones carrying a path.
        val scalar = element as? YAMLScalar ?: return
        if (MkDocsPathKind.of(scalar) == null) return

        // The value range keeps the quotes of a quoted scalar out of both the checked text and the reported
        // ranges, so an offset inside the path can be shifted onto the file as it is.
        val valueRange = ElementManipulators.getValueTextRange(scalar)
        val path = valueRange.substring(scalar.text)
        val offset = scalar.textRange.startOffset + valueRange.startOffset

        for (finding in MkDocsPathValidator.validate(path)) {
            val severity =
                if (finding.problem.isError(SystemInfo.isWindows)) HighlightSeverity.ERROR
                else HighlightSeverity.WARNING
            holder.newAnnotation(severity, messageOf(finding))
                .range(finding.range.shiftRight(offset))
                .create()
        }
    }

    /**
     * Returns the text reported for [finding].
     *
     * @param finding what was found wrong with the path
     */
    private fun messageOf(finding: MkDocsPathFinding): String = when (finding.problem) {
        MkDocsPathProblem.FORBIDDEN_CHARACTER ->
            MkDocsBundle.message("reference.problem.forbiddenCharacter", finding.text)

        MkDocsPathProblem.CONTROL_CHARACTER ->
            MkDocsBundle.message("reference.problem.controlCharacter")

        MkDocsPathProblem.EMPTY_SEGMENT ->
            MkDocsBundle.message("reference.problem.emptySegment")

        MkDocsPathProblem.TRAILING_DOT_OR_SPACE ->
            MkDocsBundle.message("reference.problem.trailingDotOrSpace")

        MkDocsPathProblem.ABSOLUTE_PATH ->
            MkDocsBundle.message("reference.problem.absolutePath")

        MkDocsPathProblem.DRIVE_LETTER ->
            MkDocsBundle.message("reference.problem.driveLetter")

        MkDocsPathProblem.PARENT_ESCAPE ->
            MkDocsBundle.message("reference.problem.parentEscape")

        MkDocsPathProblem.BACKSLASH_SEPARATOR ->
            MkDocsBundle.message("reference.problem.backslashSeparator")

        MkDocsPathProblem.RESERVED_NAME ->
            MkDocsBundle.message("reference.problem.reservedName", finding.text)

        MkDocsPathProblem.NON_ASCII ->
            MkDocsBundle.message("reference.problem.nonAscii", finding.text)

        MkDocsPathProblem.SPACE_IN_SEGMENT ->
            MkDocsBundle.message("reference.problem.spaceInSegment")

        MkDocsPathProblem.TOO_LONG ->
            MkDocsBundle.message("reference.problem.tooLong")
    }
}
