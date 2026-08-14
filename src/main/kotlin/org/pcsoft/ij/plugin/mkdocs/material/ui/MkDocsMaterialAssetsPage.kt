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

package org.pcsoft.ij.plugin.mkdocs.material.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import javax.swing.JList
import javax.swing.event.DocumentEvent

/**
 * The page holding what the theme loads from the site itself: logo, favicon, override directory, and the two
 * keys deciding how the site reads — its language and its writing direction.
 *
 * The three paths are written relative, and to two different places: MkDocs resolves `theme.logo` and
 * `theme.favicon` inside the documentation directory, while `theme.custom_dir` is relative to the
 * configuration file. The file choosers therefore start in the right directory and turn what was picked back
 * into a relative path — an absolute one would work on the machine that picked it and nowhere else.
 *
 * A path typed by hand is left exactly as typed. The site may well point at something that does not exist yet.
 *
 * @param project the project the site belongs to, or `null` in a wizard that has none yet
 * @param docsDir the documentation directory of the site, or `null` while it does not exist
 * @param siteRoot the directory holding the configuration file, or `null` while it does not exist
 */
class MkDocsMaterialAssetsPage(
    private val project: Project?,
    private val docsDir: () -> VirtualFile? = { null },
    private val siteRoot: () -> VirtualFile? = { null },
) : MkDocsMaterialPageBase(ID, "material.page.assets.title") {

    companion object {

        /** The identifier of this page. */
        const val ID: String = "material.assets"

        /** The value of `theme.direction` reading left to right. */
        const val DIRECTION_LTR: String = "ltr"

        /** The value of `theme.direction` reading right to left. */
        const val DIRECTION_RTL: String = "rtl"
    }

    private val logoField = pathField(
        FileChooserDescriptorFactory.singleFile()
            .withTitle(MkDocsBundle.message("material.page.assets.logo.title")),
        docsDir,
    )

    private val faviconField = pathField(
        FileChooserDescriptorFactory.singleFile()
            .withTitle(MkDocsBundle.message("material.page.assets.favicon.title")),
        docsDir,
    )

    private val customDirField = pathField(
        FileChooserDescriptorFactory.singleDir()
            .withTitle(MkDocsBundle.message("material.page.assets.customDir.title")),
        siteRoot,
    )

    private val languageField = JBTextField().apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = fireChanged()
        })
    }

    private val directionCombo = ComboBox(arrayOf<String?>(null, DIRECTION_LTR, DIRECTION_RTL)).apply {
        renderer = object : SimpleListCellRenderer<String?>() {
            override fun customize(
                list: JList<out String?>,
                value: String?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value ?: MkDocsBundle.message("material.page.assets.direction.default")
            }
        }
        addActionListener { fireChanged() }
    }

    override fun createContent(): DialogPanel = panel {
        row(MkDocsBundle.message("material.page.assets.logo")) {
            cell(logoField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("material.page.assets.favicon")) {
            cell(faviconField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("material.page.assets.customDir")) {
            cell(customDirField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("material.page.assets.language")) {
            cell(languageField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("material.page.assets.direction")) {
            cell(directionCombo)
        }
        row {
            comment(MkDocsBundle.message("material.page.assets.hint"))
        }
    }

    override fun reset(settings: MkDocsMaterialSettings) {
        logoField.text = settings.logo.orEmpty()
        faviconField.text = settings.favicon.orEmpty()
        customDirField.text = settings.customDir.orEmpty()
        languageField.text = settings.language.orEmpty()
        directionCombo.selectedItem = settings.direction?.takeIf { it == DIRECTION_LTR || it == DIRECTION_RTL }
    }

    override fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings = settings.copy(
        logo = logoField.text.trimmed(),
        favicon = faviconField.text.trimmed(),
        customDir = customDirField.text.trimmed(),
        language = languageField.text.trimmed(),
        direction = directionCombo.selectedItem as String?,
    )

    /**
     * Types [path] into the logo field.
     *
     * @param path the path to type, relative to the documentation directory
     */
    @TestOnly
    internal fun setLogoForTest(path: String) {
        logoField.text = path
    }

    /**
     * Types [code] into the language field.
     *
     * @param code the language code to type
     */
    @TestOnly
    internal fun setLanguageForTest(code: String) {
        languageField.text = code
    }

    /**
     * Selects [direction] as the writing direction.
     *
     * @param direction [DIRECTION_LTR], [DIRECTION_RTL], or `null` to leave the choice to the theme
     */
    @TestOnly
    internal fun setDirectionForTest(direction: String?) {
        directionCombo.selectedItem = direction
    }

    /** The value of a field, or `null` if the field names nothing — an empty key says something else. */
    private fun String?.trimmed(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * A field with a chooser that starts in [base] and writes the choice back relative to it.
     *
     * @param descriptor what the chooser accepts
     * @param base the directory the value is relative to
     */
    private fun pathField(descriptor: FileChooserDescriptor, base: () -> VirtualFile?): TextFieldWithBrowseButton =
        TextFieldWithBrowseButton().apply {
            addActionListener {
                val root = base()
                val chosen = FileChooser.chooseFile(descriptor, project, root) ?: return@addActionListener
                text = root?.let { VfsUtilCore.getRelativePath(chosen, it) } ?: chosen.path
            }
            textField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = fireChanged()
            })
        }
}
