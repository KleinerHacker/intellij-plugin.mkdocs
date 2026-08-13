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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Writes the few configuration keys the IDE side of the plugin has to change back into an MkDocs
 * configuration file.
 *
 * Writing goes through the PSI of the bundled YAML plugin rather than through plain text: the file keeps the
 * arrangement, the comments and the indentation its author gave it, and only the touched key is rewritten.
 * The counterpart reading these keys is [MkDocsConfig].
 *
 * Every function here must be called inside a write action — the caller owns it, because a change to the
 * configuration usually belongs into the same undoable command as whatever triggered it.
 */
object MkDocsConfigWriter {

    /** The key naming the theme below [MkDocsConfig.KEY_THEME]. */
    private const val KEY_NAME: String = "name"

    /**
     * Sets the theme of the site described by [configFile] to [name].
     *
     * The three shapes a configuration file can be in are all handled: an existing `theme` mapping only has
     * its `name` replaced or added, so settings the author put next to it survive; a `theme` written as a
     * plain scalar is replaced by a mapping, because a scalar has nowhere to keep the name; and a file
     * without a theme at all gets the key appended to the end of its top level mapping.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param name the name of the theme to configure, for example [MkDocsConfig.THEME_MATERIAL]
     */
    fun setThemeName(project: Project, configFile: VirtualFile, name: String) {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val generator = YAMLElementGenerator.getInstance(project)
        val mapping = MkDocsConfig.topLevelMapping(yamlFile)

        if (mapping == null) {
            val text = "${MkDocsConfig.KEY_THEME}:\n  $KEY_NAME: $name"
            val document = generator.createDummyYamlWithText(text).documents.firstOrNull() ?: return
            yamlFile.add(document)
            commit(project, configFile)
            return
        }

        val theme = mapping.keyValues.firstOrNull { it.keyText.trim() == MkDocsConfig.KEY_THEME }
        val themeMapping = theme?.value as? YAMLMapping

        if (themeMapping != null) {
            val nameValue = themeMapping.keyValues.firstOrNull { it.keyText.trim() == KEY_NAME }
            val newNameValue = generator.createYamlKeyValue(KEY_NAME, name)
            if (nameValue == null) {
                themeMapping.putKeyValue(newNameValue)
            } else {
                nameValue.replace(newNameValue)
            }
        } else {
            val text = "${MkDocsConfig.KEY_THEME}:\n  $KEY_NAME: $name"
            val newTheme = generator.createDummyYamlWithText(text)
                .documents
                .firstNotNullOfOrNull { (it.topLevelValue as? YAMLMapping)?.keyValues?.firstOrNull() }
                ?: return
            if (theme == null) {
                mapping.putKeyValue(newTheme)
            } else {
                theme.replace(newTheme)
            }
        }

        commit(project, configFile)
    }

    /**
     * Removes the theme of the site described by [configFile], so MkDocs falls back to its built-in one.
     *
     * Only the theme *name* is taken out. A `theme` mapping carrying settings next to the name — a palette,
     * a font, a list of features — keeps those: they are the user's work, they were not written by this
     * plugin, and dropping them because a facet was unchecked would destroy far more than was asked for. The
     * `theme` key itself is removed only when the name was the last thing left in it, which is the case a
     * plugin-written theme block is in.
     *
     * A file without a theme is left untouched.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     */
    fun removeTheme(project: Project, configFile: VirtualFile) {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val mapping = MkDocsConfig.topLevelMapping(yamlFile) ?: return
        val theme: YAMLKeyValue = mapping.keyValues
            .firstOrNull { it.keyText.trim() == MkDocsConfig.KEY_THEME }
            ?: return

        val themeMapping = theme.value as? YAMLMapping
        val nameValue = themeMapping?.keyValues?.firstOrNull { it.keyText.trim() == KEY_NAME }

        if (themeMapping != null && nameValue != null && themeMapping.keyValues.size > 1) {
            nameValue.delete()
        } else {
            theme.delete()
        }
        commit(project, configFile)
    }

    /**
     * Writes the PSI changes made to [configFile] through to its document and to disk.
     *
     * Without this the change lives in the PSI only, and everything reading the file through the virtual file
     * system — the build, an external MkDocs run — would still see the previous content.
     *
     * @param project the project [configFile] belongs to
     * @param configFile the configuration file that was changed
     */
    private fun commit(project: Project, configFile: VirtualFile) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val psiFile: YAMLFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val document = psiDocumentManager.getDocument(psiFile) ?: return
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
        psiDocumentManager.commitDocument(document)
        FileDocumentManager.getInstance().saveDocument(document)
    }
}
