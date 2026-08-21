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

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsInstallationSettings
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsTool
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsToolInstallation
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsToolService
import org.pcsoft.ij.plugin.mkdocs.utils.ui.MkDocsExecutableComboBox
import javax.swing.JComponent

/**
 * The *MkDocs* page below *Tools*, which the settings of the features hang under.
 *
 * What it edits is the ground every feature stands on: the three programs an MkDocs site is built with —
 * Python, pip and MkDocs itself. Each of them is looked for, each of them can be named by hand instead, and
 * the page states for each what was found and what is actually in use. A build that does nothing because
 * `mkdocs` is not on the `PATH` is the case this page answers: it is read here rather than guessed.
 *
 * Everything belonging to *one feature* of a site is not edited here — a feature owns its own settings, and
 * this page is the node they hang under. It knows none of its children: a feature registers its page against
 * the id of this one, which is the only thing the plugin and a feature share here.
 *
 * Applying throws the cached answers away, so a corrected program takes effect at once rather than after a
 * restart — the answer of pip goes with them, because which pip answers follows the interpreter named here.
 *
 * @param project the project the settings belong to
 */
class MkDocsSettingsConfigurable(private val project: Project) : Configurable {

    /** One field per program, in the order they build on each other. */
    private val fields = MkDocsTool.entries.associateWith(::fieldFor)

    override fun getDisplayName(): String = MkDocsBundle.message("settings.title")

    override fun createComponent(): JComponent = panel {
        row { label(MkDocsBundle.message("settings.description")) }
        group(MkDocsBundle.message("settings.tools.group")) {
            fields.forEach { (tool, field) ->
                row(MkDocsBundle.message("settings.tools.${tool.key}.label")) {
                    cell(field).align(Align.FILL)
                }
            }
            row {
                comment(MkDocsBundle.message("settings.tools.comment"))
            }
            // Nothing re-checks an installation on its own — it does not change while the IDE runs. This is
            // the way to say that it changed after all, and it works without anything on the page being
            // modified, which is why it is a button of its own rather than part of applying.
            row {
                button(MkDocsBundle.message("settings.tools.reload.button")) { reset() }
            }
        }
    }

    override fun isModified(): Boolean = fields.any { (tool, field) -> field.path != configured(tool) }

    override fun apply() {
        fields.values.firstNotNullOfOrNull { it.errorText }?.let { throw ConfigurationException(it) }
        val settings = project.service<MkDocsInstallationSettings>()
        fields.forEach { (tool, field) -> settings.setPath(tool.key, field.path) }
        invalidate()
    }

    override fun reset() {
        // The search is thrown away first: this is what re-checking runs through as well, and a page opened
        // on a stale answer would state an installation that is gone. pip goes with it, because which pip
        // answers follows the interpreter this page names.
        invalidate()
        // In the order of the enum, so that the interpreter is found before pip and MkDocs are derived it.
        fields.forEach { (tool, field) ->
            field.reloadCandidates()
            field.path = configured(tool)
        }
    }

    /**
     * Throws away every cached answer about an installation.
     */
    private fun invalidate() {
        service<MkDocsToolService>().invalidate()
        service<MkDocsPipService>().invalidate()
    }

    /**
     * Returns the program configured for [tool], or an empty string for the automatic answer.
     *
     * @param tool the program that is asked about
     */
    private fun configured(tool: MkDocsTool): String =
        project.service<MkDocsInstallationSettings>().pathOf(tool.key)

    /**
     * Returns the field naming [tool], worded out of the bundle of the plugin.
     *
     * @param tool the program the field names
     */
    private fun fieldFor(tool: MkDocsTool): MkDocsExecutableComboBox = MkDocsExecutableComboBox(
        project,
        tool,
        MkDocsBundle.message("settings.tools.${tool.key}.chooser"),
        MkDocsExecutableComboBox.Texts(
            automatic = MkDocsBundle.message("settings.tools.auto"),
            automaticNone = MkDocsBundle.message("settings.tools.auto.none"),
            custom = MkDocsBundle.message("settings.tools.custom"),
            inUse = MkDocsBundle.message("settings.tools.inUse"),
            inUseNone = MkDocsBundle.message("settings.tools.inUse.none"),
        ),
        MkDocsBundle.message("settings.tools.${tool.key}.progress"),
        ::problemWith,
    )

    /**
     * Returns what is wrong with the program at [path], worded for the user, or `null` if nothing is.
     *
     * The finding itself comes from [MkDocsToolInstallation]; what it reads as belongs to the bundle, which
     * is why the two are apart.
     *
     * @param path the path that was chosen by hand
     */
    private fun problemWith(path: String): String? =
        when (MkDocsToolInstallation.problemOf(path)) {
            MkDocsToolInstallation.Problem.NOT_FOUND -> MkDocsBundle.message("settings.tools.error.notFound")
            MkDocsToolInstallation.Problem.NOT_A_FILE -> MkDocsBundle.message("settings.tools.error.notAFile")
            MkDocsToolInstallation.Problem.NOT_EXECUTABLE ->
                MkDocsBundle.message("settings.tools.error.notExecutable")

            null -> null
        }
}
