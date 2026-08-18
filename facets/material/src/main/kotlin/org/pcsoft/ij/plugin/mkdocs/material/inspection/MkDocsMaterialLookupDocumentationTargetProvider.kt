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

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile

/**
 * Answers *Ctrl+Q* on an entry of the completion popup, for the Markdown extensions and their options.
 *
 * The way the popup is documented today, and the reason the entries carry [ExtensionDocElement]: quick
 * documentation of a lookup entry no longer runs through the
 * `DocumentationProvider` of the language. The platform asks the providers of
 * `platform.backend.documentation.lookupElementTargetProvider` first, and what it does without one is what was
 * measured on this very list — the popup showed the name of the element behind the entry and nothing else.
 *
 * The text itself is not built here: [MkDocsMaterialExtensionDocumentationProvider] already writes it, for the
 * same two elements, and is what answers on a name already written into the file.
 */
internal class MkDocsMaterialLookupDocumentationTargetProvider : LookupElementDocumentationTargetProvider {

    override fun documentationTarget(psiFile: PsiFile, element: LookupElement, offset: Int): DocumentationTarget? {
        val target = element.psiElement ?: return null
        if (target !is ExtensionDocElement) return null

        val html = MkDocsMaterialExtensionDocumentationProvider().generateDoc(target, target) ?: return null
        val name = target.name
        return MkDocsMaterialDocTarget(name, html)
    }
}

/**
 * The documentation of one entry of the popup, computed before the target is even built.
 *
 * Both texts come out of bundled resources, so there is nothing to defer: the result is handed over
 * synchronously and the pointer is a hard one, the instance holding nothing that could become invalid.
 *
 * @property name the name of the entry, shown as the title of the documentation
 * @property html the documentation itself
 */
private class MkDocsMaterialDocTarget(
    private val name: String,
    private val html: String,
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(name).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)
}
