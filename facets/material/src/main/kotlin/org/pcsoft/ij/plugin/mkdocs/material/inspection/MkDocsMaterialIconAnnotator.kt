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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconKeys
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconTree
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Marks a name in `mkdocs.yml` that the installed *Material for MkDocs* does not offer as an icon.
 *
 * The name of an icon is a path below the icon sets of the installed package, and a site builds nothing for a
 * path that is not there — the theme falls back to no icon at all, silently. What the editor showed of that
 * so far was the absence of the drawing in front of the name, which says the same thing only to someone who
 * knows that a drawing should be there.
 *
 * Told apart by where the name goes wrong, because the two are fixed differently:
 *
 * * the set is unknown — the name is in the wrong family altogether, and the marked range is that set;
 * * the set is known and the icon below it is not — a typo in a name, and the marked range is that name.
 *
 * Silent while nothing can be read: without an installation every name would be unknown, and the missing
 * installation is what [MkDocsMaterialInstallationAnnotator] states once for the whole file instead.
 */
class MkDocsMaterialIconAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val scalar = element as? YAMLScalar ?: return
        val file = scalar.containingFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return
        if (!MkDocsMaterialIconKeys.isIconValue(scalar)) return

        val configFile = file.virtualFile ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(scalar.project, configFile)) return

        val name = scalar.textValue.trim()
        if (name.isEmpty()) return

        val siteRoot = configFile.parent ?: return
        val names = scalar.project.service<MkDocsMaterialIconIndex>().names(siteRoot)
        // Nothing read is not the same as nothing found. Until an installation is there — and until pip has
        // answered where it lies — every name would be reported, which would put a wall of red into a file
        // that is merely waiting for an answer.
        if (names.isEmpty()) return
        if (MkDocsMaterialIconLocator.locateInstallation(scalar.project) == null) return
        if (!service<MkDocsPipService>().isKnown(MkDocsMaterialIconLocator.DISTRIBUTION)) return
        if (name in names) return

        val group = name.substringBeforeLast(MkDocsMaterialIconTree.SEPARATOR, "")
        if (!MkDocsMaterialIconTree.isGroup(names, group)) {
            report(
                holder,
                scalar,
                rangeOf(scalar, 0, group.length),
                MkDocsMaterialBundle.message("material.icon.unknown.group", group),
            )
            return
        }
        val from = if (group.isEmpty()) 0 else group.length + 1
        report(
            holder,
            scalar,
            rangeOf(scalar, from, name.length),
            MkDocsMaterialBundle.message("material.icon.unknown.name", name),
        )
    }

    /**
     * Returns the range of the value of [scalar] between [from] and [to], as the document holds it.
     *
     * Falls back to the whole value wherever the written text and the value are not the same run of
     * characters — a quoted scalar, a folded one — because an offset of the value says nothing about the
     * document there.
     *
     * @param scalar the value naming the icon
     * @param from the first character of the part in question, counted in the value
     * @param to the character behind the part in question, counted in the value
     */
    private fun rangeOf(scalar: YAMLScalar, from: Int, to: Int): TextRange {
        val value = ElementManipulators.getValueTextRange(scalar).shiftRight(scalar.textRange.startOffset)
        if (value.length != scalar.textValue.length || from >= to || to > value.length) return value
        return TextRange(value.startOffset + from, value.startOffset + to)
    }

    /**
     * Reports [message] on [range] as an error.
     *
     * @param holder what the annotation is handed to
     * @param scalar the value naming the icon
     * @param range the part of the document to mark
     * @param message what is wrong with it
     */
    private fun report(holder: AnnotationHolder, scalar: YAMLScalar, range: TextRange, message: String) {
        holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(if (range.isEmpty) scalar.textRange else range)
            .create()
    }
}
