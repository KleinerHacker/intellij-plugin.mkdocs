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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject

/**
 * Shows a banner above an MkDocs configuration file missing its metadata keys.
 *
 * `site_name`, `site_author` and `site_description` decide how the built site presents itself: the browser
 * title, the author metadata and the description search engines show. MkDocs requires none of them, which is
 * why they are so easily forgotten — and why the reminder belongs where it cannot be overlooked.
 *
 * The message is created as a file level annotation, which the IDE renders as the sticky banner at the top
 * of the editor rather than as a highlight somewhere in the text. There is nothing in the file to underline
 * anyway: what is reported is precisely what is *not* there.
 *
 * Each missing key gets a banner of its own, carrying the fix that adds exactly that key — so a site
 * deliberately omitting one of them can keep the others.
 */
class MkDocsMissingMetadataAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Annotators visit every element of the file; the file itself is one of them, and it is the only one
        // this annotator has anything to say about.
        val file = element as? YAMLFile ?: return
        if (!MkDocsProject.isConfigFile(file.name)) return

        for (key in MkDocsMetadata.missingKeys(file)) {
            holder.newAnnotation(HighlightSeverity.WARNING, MkDocsBundle.message("metadata.missing", key))
                .fileLevel()
                .withFix(MkDocsAddConfigKeyIntention(key))
                .create()
        }
    }
}
