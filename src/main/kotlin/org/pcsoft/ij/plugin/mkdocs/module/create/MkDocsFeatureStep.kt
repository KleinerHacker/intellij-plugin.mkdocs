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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Fifth step of the site creation wizard: which optional features the new site starts with.
 *
 * The step is built entirely from the `siteFeature` extension point. Whatever a feature wants to ask beyond
 * the tick itself lives on pages of its own, which the wizard shows behind this one for as long as the
 * feature is selected — this page stays a list of decisions, not a form.
 *
 * The features handed out here are per-wizard instances (see [MkDocsSiteFeature.forWizard]), so the object
 * the wizard collected input into is the very object the creation service applies afterwards.
 *
 * @param project the project the site is created in, used to ask features whether they apply
 */
class MkDocsFeatureStep(project: Project) : MkDocsValidatingStep {

    private val features: List<MkDocsSiteFeature> =
        MkDocsSiteFeature.availableFeatures(project).map { it.forWizard() }

    private val checkBoxes = LinkedHashMap<MkDocsSiteFeature, JCheckBox>()

    /**
     * Called after every change to the selection, so the wizard can add or drop the pages of a feature.
     *
     * Set by [MkDocsCreateSiteWizard]; the step itself knows nothing about the other pages.
     */
    var onSelectionChanged: () -> Unit = {}

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
                    box.component.addActionListener { onSelectionChanged() }
                    checkBoxes[feature] = box.component
                }
                row {
                    comment(feature.description)
                }
            }
        }
    }.apply { border = JBUI.Borders.empty(8) }

    override fun _init() = Unit

    override fun _commit(finishChosen: Boolean) = Unit

    /** No icon, for the same reason as in [MkDocsLayoutStep]. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent? = checkBoxes.values.firstOrNull()

    /**
     * Ticks or unticks the box of [feature] as if the user had clicked it.
     *
     * @param feature the feature to switch
     * @param selected `true` to switch it on
     */
    @TestOnly
    internal fun setSelectedForTest(feature: MkDocsSiteFeature, selected: Boolean) {
        val box = checkBoxes[feature] ?: return
        if (box.isSelected == selected) return
        box.doClick()
    }

    /** The features offered on this page, in registration order. */
    internal fun offeredFeatures(): List<MkDocsSiteFeature> = features
}
