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

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import javax.swing.JComponent

/**
 * Paints the drawing of an icon in front of the shorthand naming it in a page.
 *
 * `:material-weather-sunny:` renders an icon in the built site and nothing at all in the editor, which leaves
 * the author of a page reading names instead of seeing drawings. The hint puts the drawing where the site
 * will put it.
 *
 * Only inside the pages of a detected site using the Material theme: the shorthand means nothing in a
 * Markdown file that is not built by this theme, and a colon-wrapped word is an ordinary piece of text there.
 *
 * A shorthand the installed theme offers no icon for gets no hint — the missing drawing is the honest
 * statement that the name does not resolve.
 *
 * Lives in the optional descriptor for the Markdown plugin, next to the completion of the same shorthands:
 * without that plugin there is no Markdown language to register against.
 *
 * A provider of its own rather than a second collector of the hint in `mkdocs.yml`: a hint is registered per
 * language, and the two languages live in two content modules of the plugin. It therefore carries its own
 * switch in the settings, which is what lets an author keep one of the two and drop the other.
 */
@Suppress("UnstableApiUsage")
class MkDocsMaterialShorthandInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey(SETTINGS_KEY)

    override val name: String
        get() = MkDocsMaterialBundle.message("material.hint.shorthand.name")

    override val previewText: String
        get() = PREVIEW_TEXT

    override fun createSettings(): NoSettings = NoSettings()

    /**
     * Returns the settings panel of the hint, which holds nothing but its description.
     *
     * @param settings the settings of the hint, of which there are none
     */
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = panel {
                row { label(MkDocsMaterialBundle.message("material.hint.shorthand.description")) }
            }
        }

    /**
     * Returns `true` if [language] is Markdown.
     *
     * Judged on the identifier rather than on `MarkdownLanguage`: the facet is compiled without the Markdown
     * plugin, exactly like the completion of the same shorthands, and only the descriptor of this content
     * module ties the two together.
     *
     * @param language the language the settings page is asking about
     */
    override fun isLanguageSupported(language: Language): Boolean =
        generateSequence(language) { it.baseLanguage }.any { it.id.equals(LANGUAGE_ID, ignoreCase = true) }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        val virtualFile = file.originalFile.virtualFile ?: return null
        if (!MkDocsProject.isPageFile(virtualFile.name)) return null
        val siteRoot = MkDocsMaterialShorthands.siteRootOf(file.project, virtualFile) ?: return null
        val configFile = siteRoot.children.firstOrNull { !it.isDirectory && MkDocsProject.isConfigFile(it.name) }
            ?: return null
        if (!MkDocsMaterialConfig.isMaterialTheme(file.project, configFile)) return null
        return Collector(editor, siteRoot)
    }

    /**
     * Walks the page and paints the drawing of every shorthand in it.
     *
     * @param editor the editor the hints are painted in
     * @param siteRoot the directory holding `mkdocs.yml`, which the icons of the site belong to
     */
    private class Collector(
        editor: Editor,
        private val siteRoot: VirtualFile,
    ) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            // The whole page is read at once, on the file element. A shorthand is a piece of text, not a
            // construct of the language: which elements it is split across depends on what the Markdown
            // parser made of the line around it, and a walk of the leaves would miss it wherever the parser
            // put a link, an emphasis or a code span in between.
            if (element !is PsiFile) return true

            val index = element.project.service<MkDocsMaterialIconIndex>()
            val names = index.names(siteRoot)
            if (names.isEmpty()) return false

            val start = element.textRange.startOffset
            for (match in MkDocsMaterialShorthands.PATTERN.findAll(element.text)) {
                val name = MkDocsMaterialShorthands.nameOf(match.value, names) ?: continue
                val icon = index.icon(siteRoot, name, ICON_SIZE) ?: continue
                val presentation = factory.withTooltip(
                    MkDocsMaterialBundle.message("material.hint.shorthand.tooltip", name),
                    factory.inset(factory.icon(icon), top = 5, right = 1),
                )
                // The drawing belongs to the shorthand that follows it, so it must not move with the text in
                // front of it.
                sink.addInlineElement(start + match.range.first, false, presentation, false)
            }
            return true
        }
    }

    private companion object {

        /** The identifier of the language the hint is offered for. */
        const val LANGUAGE_ID = "Markdown"

        /** The identifier the IDE stores the enabled state of the hint under. */
        const val SETTINGS_KEY = "mkdocs.material.icon.shorthand"

        /** The size of the drawing; an inlay sits inside a line and must stay below the line height. */
        const val ICON_SIZE = 12

        /** The snippet the settings page renders the hint on. */
        const val PREVIEW_TEXT: String =
            "# Weather\n\nThe forecast is :material-weather-sunny: today.\n"
    }
}
