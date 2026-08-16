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
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialKeys
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import javax.swing.JComponent

/**
 * Puts the mark of *Material for MkDocs* in front of every key of a configuration file the theme alone reads.
 *
 * The configuration file of a site does not say which of its keys belong to MkDocs and which to the theme, and
 * an author reading it cannot tell what would stop working on a change of theme. The hint says it where the
 * question comes up — in front of `features:`, `palette:`, `font:`, `direction:`, `icon:` and the keys below
 * `extra` the theme reads. What belongs to which is decided in [MkDocsMaterialKeys], so the hint and the
 * completion never disagree.
 *
 * Shown only in a configuration file whose theme is Material. A site on another theme has none of these keys,
 * and one that happens to use the same name means something else by it.
 *
 * Built on the imperative hints API rather than on the declarative one: the mark is an icon, and the
 * declarative API renders text only.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialKeyInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey(SETTINGS_KEY)

    override val name: String
        get() = MkDocsMaterialBundle.message("material.hint.origin.name")

    override val previewText: String
        get() = PREVIEW_TEXT

    override fun createSettings(): NoSettings = NoSettings()

    /**
     * Returns the settings panel of the hint, which holds nothing but its description.
     *
     * The hint has no options: it either marks the keys of the theme or it does not, and that is the switch
     * the settings page offers for every hint anyway.
     *
     * @param settings the settings of the hint, of which there are none
     */
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = panel {
                row { label(MkDocsMaterialBundle.message("material.hint.origin.description")) }
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
        return Collector(editor)
    }

    /**
     * Walks the configuration file and marks the keys of the theme.
     *
     * @param editor the editor the hints are painted in
     */
    private class Collector(editor: Editor) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            val keyValue = element as? YAMLKeyValue ?: return true
            if (!MkDocsMaterialKeys.isMaterialKey(keyValue)) return true

            // The icon arrives at the size it is painted at, fixed by `MkDocsIconLoader`, and is placed by
            // hand: the inset lifts it off the baseline into the middle of the line and keeps it off the key.
            val presentation = factory.withTooltip(
                MkDocsMaterialBundle.message("material.hint.origin.tooltip"),
                factory.inset(factory.icon(MkDocsMaterialIcons.Inlay), top = 5, right = 1),
            )
            // The mark belongs to the key that follows it, not to whatever precedes it on the line, so it must
            // not move with the text in front of it.
            sink.addInlineElement(keyValue.textRange.startOffset, false, presentation, false)
            return true
        }
    }

    private companion object {

        /** The identifier the IDE stores the enabled state of the hint under. */
        const val SETTINGS_KEY = "mkdocs.material.origin"

        /** The snippet the settings page renders the hint on. */
        const val PREVIEW_TEXT: String =
            "theme:\n  name: material\n  features:\n    - navigation.tabs\n  palette:\n    primary: indigo\n"
    }
}
