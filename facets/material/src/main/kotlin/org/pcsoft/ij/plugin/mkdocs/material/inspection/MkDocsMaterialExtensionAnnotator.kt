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
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle

/**
 * Shows a banner above a Material configuration file whose own features ask for a Markdown extension it does
 * not list.
 *
 * Reported as an error, and only for extensions that are actually forced: a feature such as
 * `content.code.annotate` renders nothing at all without `pymdownx.superfences`, so the site is broken in a
 * way the author cannot see in the configuration file. Everything the theme merely builds on is left to
 * [MkDocsMaterialRecommendedExtensionInspection], which can be switched off — this one cannot, because there
 * is nothing here to judge.
 *
 * A file level annotation rather than a highlight: what is reported is precisely what is *not* in the file.
 * Each missing extension gets a banner of its own, carrying the fix that adds exactly that one.
 */
class MkDocsMaterialExtensionAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotators visit every element of the file; the file itself is one of them, and it is the only one
        // this annotator has anything to say about.
        val file = element as? YAMLFile ?: return

        for (extension in MkDocsMaterialExtensions.missingRequired(element.project, file)) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                MkDocsMaterialBundle.message("material.extension.missing", extension.id),
            )
                .fileLevel()
                .withFix(MkDocsMaterialAddExtensionFix(extension))
                .create()
        }
    }
}
