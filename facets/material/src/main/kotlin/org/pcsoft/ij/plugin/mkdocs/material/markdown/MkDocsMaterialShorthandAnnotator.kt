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

package org.pcsoft.ij.plugin.mkdocs.material.markdown

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Marks a shorthand in a page that names an icon of a known set which the installed theme does not offer.
 *
 * A page of *Material for MkDocs* writes an icon as `:material-check:`, and the theme renders whatever the
 * installed sets hold under that name — or nothing at all, silently, which is what a typo looks like.
 *
 * Only shorthands beginning with a set of the installed theme are judged. The syntax is not the theme's
 * alone: `:smile:` and every other emoji shorthand of `pymdownx.emoji` is written exactly the same way, and
 * a name that starts with no known set is far more likely one of those than a misspelt icon. A misspelt *set*
 * is therefore not reported either — in a page it cannot be told from an emoji, while `mkdocs.yml` names
 * nothing but icons at those keys and [org.pcsoft.ij.plugin.mkdocs.material.inspection.MkDocsMaterialIconAnnotator]
 * reports it there.
 */
class MkDocsMaterialShorthandAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // The whole page at once, on the file element: a shorthand is a piece of text, not a construct of the
        // language, and which elements it is split across depends on what the parser made of the line.
        val file = element as? PsiFile ?: return
        val virtualFile = file.virtualFile ?: return
        if (!MkDocsProject.isPageFile(virtualFile.name)) return

        val project = file.project
        val siteRoot = MkDocsMaterialShorthands.siteRootOf(project, virtualFile) ?: return
        val configFile = siteRoot.children.firstOrNull { !it.isDirectory && MkDocsProject.isConfigFile(it.name) }
            ?: return
        if (!MkDocsMaterialConfig.isMaterialTheme(project, configFile)) return

        val names = project.service<MkDocsMaterialIconIndex>().names(siteRoot)
        // Nothing read is not the same as nothing found: without an installation every shorthand of the page
        // would be reported, and the missing installation is stated on the configuration file instead.
        if (names.isEmpty()) return
        if (MkDocsMaterialIconLocator.locateInstallation(project) == null) return
        if (!service<MkDocsPipService>().isKnown(MkDocsMaterialIconLocator.DISTRIBUTION)) return

        val text = file.text
        val start = file.textRange.startOffset
        for (range in MkDocsMaterialShorthands.unknownIn(text, names)) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                MkDocsMaterialBundle.message("material.shorthand.unknown", text.substring(range).trim(':')),
            )
                .range(TextRange(start + range.first, start + range.last + 1))
                .create()
        }
    }
}
