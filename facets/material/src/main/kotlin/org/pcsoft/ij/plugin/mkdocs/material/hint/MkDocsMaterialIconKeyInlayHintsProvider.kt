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
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconTree
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import javax.swing.JComponent

/**
 * Writes the shorthand of an icon behind the name naming it in a configuration file.
 *
 * A configuration file addresses an icon by its path, `material/pencil`, while a page writes the same icon as
 * `:material-pencil:` — and the two spellings are what an author has to translate by hand whenever the icon
 * of `theme.icon` is to appear in the text as well. The hint spells it out where the name stands, behind the
 * value, so the shorthand can be read off instead of being derived.
 *
 * Every place the configuration file names an icon is covered, which [MkDocsMaterialIconKeys] decides — the
 * same answer the completion of those names is built on.
 *
 * A name the installed theme does not offer gets no hint: a shorthand written for it would name an icon that
 * does not exist, and the missing text is the honest statement that the name does not resolve.
 *
 * Built on the imperative hints API rather than on the declarative one, exactly like the hint painting the
 * drawing of the same name in front of it, whose switch in the settings this one is kept apart from.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialIconKeyInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey(SETTINGS_KEY)

    override val name: String
        get() = MkDocsMaterialBundle.message("material.hint.shortcode.name")

    override val previewText: String
        get() = PREVIEW_TEXT

    override fun createSettings(): NoSettings = NoSettings()

    /**
     * Returns the settings panel of the hint, which holds nothing but its description.
     *
     * The hint has no options: it either writes the shorthands or it does not, and that is the switch the
     * settings page offers for every hint anyway.
     *
     * @param settings the settings of the hint, of which there are none
     */
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = panel {
                row { label(MkDocsMaterialBundle.message("material.hint.shortcode.description")) }
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
     * Walks the configuration file and writes the shorthand of every icon it names.
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
            if (name !in element.project.service<MkDocsMaterialIconIndex>().names(siteRoot)) return true

            val shorthand = MkDocsMaterialIconTree.shorthandOf(name)
            val presentation = factory.withTooltip(
                MkDocsMaterialBundle.message("material.hint.shortcode.tooltip", name),
                factory.roundWithBackground(factory.smallText(shorthand)),
            )
            // Behind the name, and bound to it: the shorthand is the second spelling of the text in front of
            // it, so it has to move with that text rather than with whatever is typed after it.
            sink.addInlineElement(scalar.textRange.endOffset, true, presentation, false)
            return true
        }
    }

    private companion object {

        /** The identifier the IDE stores the enabled state of the hint under. */
        const val SETTINGS_KEY = "mkdocs.material.icon.shortcode"

        /** The snippet the settings page renders the hint on. */
        const val PREVIEW_TEXT: String =
            "theme:\n  name: material\n  icon:\n    repo: fontawesome/brands/github\n    edit: material/pencil\n"
    }
}
