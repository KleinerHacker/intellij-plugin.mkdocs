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

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IconUtil
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import javax.swing.Icon

/**
 * Puts a gutter icon next to every path value of an MkDocs configuration file that points somewhere.
 *
 * The icon says two things at once, which is why it is worth the line it takes: that the value resolves at
 * all — an entry of `nav` naming a page that was renamed away shows nothing — and what it resolves to. For a
 * file the icon is the one the IDE shows for it everywhere else, so the pages of the site carry the MkDocs
 * Markdown icon and a referenced style sheet the MkDocs CSS icon, both of which come from
 * `MkDocsFileIconProvider`. The two directory keys get their own markers instead: a directory icon would say
 * nothing, while the badge tells `docs_dir` and `site_dir` apart at a glance.
 *
 * The marker is attached to the leaf element inside the scalar rather than to the scalar itself, as the
 * platform requires of a line marker.
 */
class MkDocsPathLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = MkDocsBundle.message("reference.marker.group")

    override fun getIcon(): Icon = MkDocsIcons.MkDocs

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Only leaves may carry a marker, and only one of them may — otherwise every leaf of a scalar spanning
        // several tokens would contribute its own icon to the same line.
        if (element.firstChild != null) return
        val scalar = element.parent as? YAMLScalar ?: return
        if (PsiTreeUtil.getDeepestFirst(scalar) !== element) return

        val kind = MkDocsPathKind.of(scalar) ?: return
        val target = resolveTarget(scalar) ?: return

        val builder = NavigationGutterIconBuilder.create(iconOf(kind, target))
            .setTargets(listOf(target))
            .setTooltipText(tooltipOf(kind))
            .setPopupTitle(MkDocsBundle.message("reference.marker.popup"))
        result.add(builder.createLineMarkerInfo(element))
    }

    /**
     * Returns the file or directory [scalar] points at, or `null` if it points nowhere.
     *
     * The last file reference of the value is the one covering the whole path; the ones before it cover the
     * directories on the way there.
     *
     * @param scalar the scalar holding the path
     */
    private fun resolveTarget(scalar: YAMLScalar): PsiFileSystemItem? =
        scalar.references.filterIsInstance<FileReference>().lastOrNull()?.resolve()

    /**
     * Returns the icon the marker of [target] is drawn with.
     *
     * @param kind what MkDocs reads the value as
     * @param target the file or directory the value points at
     */
    private fun iconOf(kind: MkDocsPathKind, target: PsiFileSystemItem): Icon = when (kind) {
        MkDocsPathKind.DOCS_DIR -> MkDocsIcons.DocsBadge
        MkDocsPathKind.SITE_DIR -> MkDocsIcons.SiteDirBadge
        // Going through IconUtil rather than reaching for a fixed icon lets the file icon provider of the
        // plugin answer, so a page and a loaded style sheet keep the icon they carry everywhere else.
        else -> IconUtil.getIcon(target.virtualFile, 0, target.project)
    }

    /**
     * Returns the text shown when hovering the marker of [kind].
     *
     * @param kind what MkDocs reads the value as
     */
    private fun tooltipOf(kind: MkDocsPathKind): String = when (kind) {
        MkDocsPathKind.DOCS_DIR -> MkDocsBundle.message("reference.marker.tooltip.docsDir")
        MkDocsPathKind.SITE_DIR -> MkDocsBundle.message("reference.marker.tooltip.siteDir")
        MkDocsPathKind.LOGO -> MkDocsBundle.message("reference.marker.tooltip.logo")
        MkDocsPathKind.FAVICON -> MkDocsBundle.message("reference.marker.tooltip.favicon")
        MkDocsPathKind.EXTRA_CSS -> MkDocsBundle.message("reference.marker.tooltip.extraCss")
        MkDocsPathKind.NAV -> MkDocsBundle.message("reference.marker.tooltip.nav")
    }
}
