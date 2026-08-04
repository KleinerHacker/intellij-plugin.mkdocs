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

import com.intellij.ide.wizard.AbstractWizard
import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError

/**
 * Two step wizard creating a new MkDocs site.
 *
 * Step one asks where the site goes and how its directories are named, step two which optional features it
 * starts with. Invalid input blocks *Next* and *Finish*: [MkDocsSiteStep] reports it from its commit hook,
 * which is where the platform expects a step to refuse. The wizard only collects input — writing anything is
 * left to [org.pcsoft.ij.plugin.mkdocs.services.MkDocsSiteCreationService].
 *
 * @param project the project the site is created in
 * @param initialDirectory the directory prefilled into the location field
 */
class MkDocsCreateSiteWizard(
    project: Project,
    initialDirectory: String,
) : AbstractWizard<Step>(MkDocsBundle.message("create.site.title"), project) {

    private val siteStep = MkDocsSiteStep(project, initialDirectory)

    private val featureStep = MkDocsFeatureStep(project)

    init {
        addStep(siteStep)
        addStep(featureStep)
        init()
        siteStep.onInputChanged = { refreshButtons() }
        refreshButtons()
    }

    /**
     * Keeps *Next* in step with the input: it stays disabled while the first step cannot produce a site.
     *
     * The commit hook of the step still refuses invalid input — the button state is the visible half of the
     * same rule, not a replacement for it.
     */
    private fun refreshButtons() {
        if (currentStep != 0) return
        nextButton.isEnabled = siteStep.validate() == null
    }

    override fun updateStep() {
        super.updateStep()
        refreshButtons()
    }

    /**
     * The template describing what the user asked for.
     *
     * Only meaningful after the wizard was closed with *Finish*.
     */
    val template: MkDocsSiteTemplate?
        get() = siteStep.buildTemplate()?.copy(features = featureStep.selectedFeatures)

    /**
     * Renders [error] the way the first step would, so a failure reported after the wizard closed reads
     * exactly like one inside it.
     *
     * @param error the reason the input was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String = siteStep.messageFor(error)
}
