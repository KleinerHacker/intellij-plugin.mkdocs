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

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialKeys
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import javax.swing.Icon

/**
 * Marks every key and every value of a configuration file that exists only because of *Material for MkDocs*.
 *
 * The file mixes two vocabularies: what MkDocs itself reads, and what the theme adds on top. Reading the file
 * says nothing about which is which, and that is what an author needs to know before changing the theme —
 * everything marked here stops working on such a change.
 *
 * Drawn into the gutter rather than into the line. The mark is a statement *about* the line, not part of it,
 * and an inlay would push the text of the file around to say so. The gutter also keeps the mark out of what is
 * copied out of the editor.
 *
 * More than one mark on a line is deliberate and is why the markers are plain [LineMarkerInfo]s rather than
 * mergeable ones: `markdown_extensions` is a key of MkDocs carrying values of the theme, so the line can hold
 * a marked value below an unmarked key, and the icons then stand next to each other in the order of the
 * elements they belong to.
 *
 * What counts as the theme's is decided by [MkDocsMaterialKeys] alone, which the completion popup asks as
 * well — a key marked here and left plain there would say the two mean different things.
 *
 * Only active in a configuration file whose theme is Material. Everywhere else the whole question does not
 * arise.
 */
class MkDocsMaterialOriginLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = MkDocsMaterialBundle.message("material.marker.origin.name")

    override fun getIcon(): Icon = MkDocsMaterialIcons.Badge

    /**
     * Returns the mark for [element], or `null` if it is nothing the theme brings along.
     *
     * Only leaves are answered for, which the platform requires of a line marker: anchoring one on a composite
     * element makes it complain about the range it would have to highlight, and the range of a key-value
     * covers the whole block below it.
     *
     * @param element the element the daemon offers, which may be any leaf of the file
     */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null
        val file = element.containingFile ?: return null
        if (!isMaterialConfigFile(file)) return null

        val keyValue = element.parent as? YAMLKeyValue
        if (keyValue != null) {
            if (keyValue.key !== element) return null
            if (!MkDocsMaterialKeys.isMaterialKey(keyValue)) return null
            return markerOf(element, "material.marker.origin.key.tooltip", keyValue.keyText.trim())
        }

        val scalar = element.parent as? YAMLScalar ?: return null
        // A plain scalar can be built of several tokens; the first of them stands for the whole value, so the
        // value gets one marker rather than one per token.
        if (scalar.firstChild !== element) return null
        if (!MkDocsMaterialKeys.isMaterialValue(scalar)) return null
        return markerOf(element, "material.marker.origin.value.tooltip", scalar.textValue.trim())
    }

    /**
     * Returns the mark to draw next to [anchor], carrying the text [bundleKey] describes.
     *
     * Without a navigation handler on purpose: the mark states where a setting comes from and has nowhere to
     * jump to. A handler would turn the cursor into a hand and promise a target that does not exist.
     *
     * @param anchor the leaf the mark belongs to
     * @param bundleKey the key of the tooltip text, which takes the name as its only argument
     * @param name the key or the value the mark stands for
     */
    private fun markerOf(anchor: PsiElement, bundleKey: String, name: String): LineMarkerInfo<PsiElement> {
        val tooltip = MkDocsMaterialBundle.message(bundleKey, name)
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            MkDocsMaterialIcons.Badge,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }

    /**
     * Returns `true` if [file] is a configuration file of a site built with the theme.
     *
     * Cached on the file: the daemon asks for every leaf of it, and reading the configured theme walks the
     * file each time. The cache drops with the next change of the file, which is exactly when the answer can
     * become another one.
     *
     * @param file the file the element being marked lies in
     */
    private fun isMaterialConfigFile(file: PsiFile): Boolean {
        if (!MkDocsProject.isConfigFile(file.name)) return false
        return CachedValuesManager.getCachedValue(file) {
            val virtualFile = file.originalFile.virtualFile
            val material = virtualFile != null &&
                MkDocsMaterialConfig.isMaterialTheme(file.project, virtualFile)
            CachedValueProvider.Result.create(material, file)
        }
    }
}
