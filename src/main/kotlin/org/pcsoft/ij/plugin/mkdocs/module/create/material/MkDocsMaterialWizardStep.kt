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

package org.pcsoft.ij.plugin.mkdocs.module.create.material

import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPage
import org.pcsoft.ij.plugin.mkdocs.module.facet.material.MkDocsMaterialSiteFeature
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsFeatureWizardStep
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import javax.swing.Icon
import javax.swing.JComponent

/**
 * One Material settings page shown as a page of the site creation wizard.
 *
 * The step owns nothing: it hands the page's component to the wizard, and on the way out it folds what the
 * page collected into the settings of the feature instance this wizard is filling in. The very same page
 * object appears as a tab of the Angular Material facet, which is why the step has to stay this thin — any
 * behaviour added here would exist in the wizard and be missing in the Project Structure dialog.
 *
 * @param materialFeature the per-wizard feature instance the collected settings are stored in
 * @param page the page this step shows
 */
class MkDocsMaterialWizardStep(
    private val materialFeature: MkDocsMaterialSiteFeature,
    private val page: MkDocsMaterialSettingsPage,
) : MkDocsFeatureWizardStep {

    override val feature: MkDocsSiteFeature
        get() = materialFeature

    /** The name of the page, so a test and a log can tell the four pages apart. */
    val title: String
        get() = page.title

    override fun _init() = Unit

    override fun _commit(finishChosen: Boolean) = Unit

    /** No icon: the wizard names the feature in its title, and the page carries the Material header itself. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = page.component()

    override fun getPreferredFocusedComponent(): JComponent? = null

    override fun validate(): Any? = page.validate()

    override fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate {
        materialFeature.collectFrom(page)
        return template
    }
}
