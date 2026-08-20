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

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallation
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationCache
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import org.pcsoft.ij.plugin.mkdocs.utils.ui.MkDocsInstallationComboBox
import javax.swing.JComponent

/**
 * The *Material* page of the settings, below the *MkDocs* page of the plugin.
 *
 * It answers one question: where the installed *Material for MkDocs* lies. The installations
 * `pip show mkdocs-material` reports are the fixed list of the page, and one entry below them stands for a
 * directory chosen by hand — for every setup pip cannot answer for: an interpreter somewhere else, a system
 * wide installation, a container mount.
 *
 * A directory chosen by hand is checked before it is accepted. The icon completion, the icon hints and the
 * shorthands of a page all read out of that directory, and a wrong one shows itself as an empty popup and
 * nothing else — so what is wrong with it is said on the page, in red, and applying is refused.
 *
 * The page belongs to the Material feature, which is why it lives here rather than next to the plugin: it
 * edits nothing the plugin itself reads, and a feature owns its own settings.
 *
 * Applying throws the icon index and the cached answer of pip away, so a corrected path takes effect in the
 * very next completion popup rather than after a restart.
 *
 * @param project the project the settings belong to
 */
class MkDocsMaterialIconSettingsConfigurable(private val project: Project) : Configurable {

    private val iconPath = MkDocsInstallationComboBox(
        project,
        MkDocsMaterialIconLocator.DISTRIBUTION,
        MkDocsMaterialBundle.message("settings.iconPath.chooser"),
        MkDocsInstallationComboBox.Texts(
            automatic = MkDocsMaterialBundle.message("settings.iconPath.auto"),
            automaticNone = MkDocsMaterialBundle.message("settings.iconPath.auto.none"),
            custom = MkDocsMaterialBundle.message("settings.iconPath.custom"),
            inUse = MkDocsMaterialBundle.message("settings.iconPath.inUse"),
            inUseNone = MkDocsMaterialBundle.message("settings.iconPath.inUse.none"),
        ),
        MkDocsMaterialBundle.message("settings.iconPath.progress"),
        ::problemWith,
    )

    override fun getDisplayName(): String = MkDocsMaterialBundle.message("settings.material.title")

    override fun createComponent(): JComponent = panel {
        group(MkDocsMaterialBundle.message("settings.iconPath.group")) {
            row(MkDocsMaterialBundle.message("settings.iconPath.label")) {
                cell(iconPath).align(Align.FILL)
            }
            row {
                comment(MkDocsMaterialBundle.message("settings.iconPath.comment"))
            }
            // Nothing re-checks an installation on its own — it does not change while the IDE runs. This is
            // the way to say that it changed after all, and it works without anything on the page being
            // modified, which is why it is a button of its own rather than part of applying.
            row {
                button(MkDocsMaterialBundle.message("material.reload.button")) {
                    MkDocsMaterialIconLocator.reload(project)
                    iconPath.reloadCandidates()
                }
            }
        }
    }

    override fun isModified(): Boolean = iconPath.path != settings().iconPath

    override fun apply() {
        iconPath.errorText?.let { throw ConfigurationException(it) }
        settings().iconPath = iconPath.path
        service<MkDocsPipService>().invalidate()
        service<MkDocsMaterialInstallationCache>().invalidate()
    }

    override fun reset() {
        iconPath.reloadCandidates()
        iconPath.path = settings().iconPath
    }

    /**
     * Returns what is wrong with the directory [path], worded for the user, or `null` if nothing is.
     *
     * The finding itself comes from [MkDocsMaterialInstallation]; what it reads as belongs to the bundle of
     * this feature, which is why the two are apart.
     *
     * @param path the directory that was chosen by hand
     */
    private fun problemWith(path: String): String? =
        when (MkDocsMaterialInstallation.problemOf(path)) {
            MkDocsMaterialInstallation.Problem.NO_DIRECTORY ->
                MkDocsMaterialBundle.message("settings.iconPath.error.noDirectory")

            MkDocsMaterialInstallation.Problem.NO_DIST_INFO ->
                MkDocsMaterialBundle.message("settings.iconPath.error.noDistInfo")

            MkDocsMaterialInstallation.Problem.WRONG_NAME ->
                MkDocsMaterialBundle.message("settings.iconPath.error.wrongName")

            MkDocsMaterialInstallation.Problem.NO_RECORD ->
                MkDocsMaterialBundle.message("settings.iconPath.error.noRecord")

            null -> null
        }

    /**
     * Returns the settings this page edits.
     */
    private fun settings(): MkDocsMaterialIconSettings = project.service<MkDocsMaterialIconSettings>()
}
