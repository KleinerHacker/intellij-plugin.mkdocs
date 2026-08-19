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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconIndex
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The *Material* page of the settings, below the *MkDocs* page of the plugin.
 *
 * It answers one question, and it answers it in both directions: where the installed *Material for MkDocs*
 * lies. The page states what was found on its own — the icon completion, the icon hints and the shorthands of
 * a page all read the icons out of that installation, and an author seeing nothing offered has no other way
 * of telling whether the plugin found it. The field below is the answer for every setup the search cannot
 * guess: an interpreter somewhere else, a system wide installation, a container mount.
 *
 * The page belongs to the Angular Material feature, which is why it lives here rather than next to the
 * plugin: it edits nothing the plugin itself reads, and a feature owns its own settings.
 *
 * Applying throws the icon index away, so a corrected path takes effect in the very next completion popup
 * rather than after a restart.
 *
 * @param project the project the settings belong to
 */
class MkDocsMaterialIconSettingsConfigurable(private val project: Project) : Configurable {

    private val iconPath = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(MkDocsMaterialBundle.message("settings.iconPath.chooser")),
        )
    }

    private val detected = JLabel()

    override fun getDisplayName(): String = MkDocsMaterialBundle.message("settings.material.title")

    override fun createComponent(): JComponent = panel {
        row(MkDocsMaterialBundle.message("settings.detected.label")) {
            cell(detected).align(Align.FILL)
        }
        row(MkDocsMaterialBundle.message("settings.iconPath.label")) {
            cell(iconPath).align(Align.FILL)
        }
        row {
            comment(MkDocsMaterialBundle.message("settings.iconPath.comment"))
        }
    }

    override fun isModified(): Boolean = iconPath.text.trim() != settings().iconPath

    override fun apply() {
        settings().iconPath = iconPath.text.trim()
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
        detected.text = detectedText()
    }

    override fun reset() {
        iconPath.text = settings().iconPath
        detected.text = detectedText()
    }

    /**
     * Returns what the page states about the installation it found on its own.
     *
     * The search reads the file system and therefore runs in a read action, off the settings of the page: what
     * is shown here is what the plugin would find if the field below were empty.
     */
    private fun detectedText(): String {
        val found = runReadActionBlocking { MkDocsMaterialIconLocator.detectInProject(project)?.presentableUrl }
        return found ?: MkDocsMaterialBundle.message("settings.detected.none")
    }

    /**
     * Returns the settings this page edits.
     */
    private fun settings(): MkDocsMaterialIconSettings = MkDocsMaterialIconSettings.getInstance(project)
}
