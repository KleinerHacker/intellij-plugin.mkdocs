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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Second step of the site creation wizard: which optional features the new site starts with.
 *
 * The step is built entirely from the `siteFeature` extension point. No feature ships with the plugin yet,
 * so what the user sees today is the empty-state hint — the step exists so the planned MkDocs extensions can
 * be plugged in without touching the wizard.
 *
 * @param project the project the site is created in, used to ask features whether they apply
 */
class MkDocsFeatureStep(project: Project) : Step {

    private val features: List<MkDocsSiteFeature> = MkDocsSiteFeature.availableFeatures(project)

    private val checkBoxes = LinkedHashMap<MkDocsSiteFeature, JCheckBox>()

    /** The features the user switched on, in the order they are registered. */
    val selectedFeatures: List<MkDocsSiteFeature>
        get() = features.filter { checkBoxes[it]?.isSelected == true }

    private val panel: DialogPanel = panel {
        if (features.isEmpty()) {
            row {
                label(MkDocsBundle.message("create.site.features.empty"))
            }
            row {
                comment(MkDocsBundle.message("create.site.features.empty.hint"))
            }
        } else {
            for (feature in features) {
                row {
                    val box = checkBox(feature.displayName)
                    feature.icon?.let { box.component.icon = it }
                    checkBoxes[feature] = box.component
                }
                row {
                    comment(feature.description)
                }
                feature.createSettingsComponent()?.let { settings ->
                    row {
                        cell(settings)
                    }
                }
            }
        }
    }.apply { border = JBUI.Borders.empty(8) }

    override fun _init() = Unit

    override fun _commit(finishChosen: Boolean) = Unit

    /** No icon, for the same reason as in [MkDocsSiteStep]. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent? = checkBoxes.values.firstOrNull()
}
