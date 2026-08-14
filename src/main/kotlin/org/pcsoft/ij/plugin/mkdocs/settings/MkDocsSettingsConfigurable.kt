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

package org.pcsoft.ij.plugin.mkdocs.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import javax.swing.JComponent

/**
 * The settings page of the plugin, below *Tools*.
 *
 * Holds the one setting that cannot be derived from the project: where the icons of the
 * *Material for MkDocs* theme are installed. The search covers the virtual environments a project normally
 * keeps next to its sources, and this field is the answer for every other setup.
 *
 * Applying throws the icon index away, so a corrected path takes effect in the very next completion popup
 * rather than after a restart.
 *
 * @param project the project the settings belong to
 */
class MkDocsSettingsConfigurable(private val project: Project) : Configurable {

    private val iconPath = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(MkDocsBundle.message("settings.iconPath.chooser")),
        )
    }

    override fun getDisplayName(): String = MkDocsBundle.message("settings.title")

    override fun createComponent(): JComponent = panel {
        row(MkDocsBundle.message("settings.iconPath.label")) {
            cell(iconPath).align(Align.FILL)
        }
        row {
            comment(MkDocsBundle.message("settings.iconPath.comment"))
        }
    }

    override fun isModified(): Boolean = iconPath.text.trim() != settings().iconPath

    override fun apply() {
        settings().iconPath = iconPath.text.trim()
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
    }

    override fun reset() {
        iconPath.text = settings().iconPath
    }

    /**
     * Returns the settings this page edits.
     */
    private fun settings(): MkDocsSettings = MkDocsSettings.getInstance(project)
}
