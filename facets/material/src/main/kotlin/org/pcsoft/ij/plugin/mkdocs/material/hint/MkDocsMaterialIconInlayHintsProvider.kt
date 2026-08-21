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

package org.pcsoft.ij.plugin.mkdocs.material.hint

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import javax.swing.JComponent

/**
 * Paints the drawing of an icon in front of the name naming it in a configuration file.
 *
 * `material/weather-sunny` says nothing about what it draws, and the sets of an installed
 * *Material for MkDocs* hold thousands of such names. The hint answers the question where it comes up, in
 * front of the value: the author sees what the site will render without leaving the file.
 *
 * Every place the configuration file names an icon is covered, which [MkDocsMaterialIconKeys] decides — the
 * same answer the completion of those names is built on.
 *
 * A name the installed theme does not offer gets no hint. Nothing else in the file could be shown there, and
 * the missing drawing is the honest statement that the name does not resolve.
 *
 * Built on the imperative hints API rather than on the declarative one: the hint is an icon, and the
 * declarative API renders text only.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialIconInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey(SETTINGS_KEY)

    override val name: String
        get() = MkDocsMaterialBundle.message("material.hint.icon.name")

    override val previewText: String
        get() = PREVIEW_TEXT

    override fun createSettings(): NoSettings = NoSettings()

    /**
     * Returns the settings panel of the hint, which holds nothing but its description.
     *
     * The hint has no options: it either shows the drawings or it does not, and that is the switch the
     * settings page offers for every hint anyway.
     *
     * @param settings the settings of the hint, of which there are none
     */
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = panel {
                row { label(MkDocsMaterialBundle.message("material.hint.icon.description")) }
            }
        }

    override fun isLanguageSupported(language: Language): Boolean = language.isKindOf(YAMLLanguage.INSTANCE)

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (!MkDocsProject.isConfigFile(file.name)) return null
        val virtualFile = file.originalFile.virtualFile ?: return null
        if (!MkDocsMaterialConfig.isMaterialTheme(file.project, virtualFile)) return null
        val siteRoot = virtualFile.parent ?: return null
        return Collector(editor, siteRoot)
    }

    /**
     * Walks the configuration file and paints the drawing of every icon it names.
     *
     * @param editor the editor the hints are painted in
     * @param siteRoot the directory holding `mkdocs.yml`, which the icons of the site belong to
     */
    private class Collector(
        editor: Editor,
        private val siteRoot: VirtualFile,
    ) : FactoryInlayHintsCollector(editor) {

        @Suppress("SameReturnValue")
        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            val scalar = element as? YAMLScalar ?: return true
            if (!MkDocsMaterialIconKeys.isIconValue(scalar)) return true

            val name = scalar.textValue.trim()
            if (name.isEmpty()) return true
            val icon = element.project.service<MkDocsMaterialIconIndex>().icon(siteRoot, name, ICON_SIZE)
                ?: return true

            // The icon arrives at the size it is painted at, fixed by `MkDocsIconLoader`, and is placed by
            // hand: the inset lifts it off the baseline into the middle of the line and keeps it off the name.
            val presentation = factory.withTooltip(
                MkDocsMaterialBundle.message("material.hint.icon.tooltip", name),
                factory.inset(factory.icon(icon), top = 6, right = 2),
            )
            // The drawing belongs to the name that follows it, so it must not move with the text in front of it.
            sink.addInlineElement(scalar.textRange.startOffset, false, presentation, false)
            return true
        }
    }

    private companion object {

        /** The identifier the IDE stores the enabled state of the hint under. */
        const val SETTINGS_KEY = "mkdocs.material.icon.preview"

        /** The size of the drawing; an inlay sits inside a line and must stay below the line height. */
        const val ICON_SIZE = 12

        /** The snippet the settings page renders the hint on. */
        const val PREVIEW_TEXT: String =
            "theme:\n  name: material\n  icon:\n    repo: fontawesome/brands/github\n    edit: material/pencil\n"
    }
}
