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
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError

/**
 * Five step wizard creating a new MkDocs site.
 *
 * The steps are ordered by how far a decision reaches: where the files go, what the site is called, where
 * its sources live, what its footer says, and finally which optional features it starts with. Everything
 * after the first two steps is optional and produces no configuration key when left empty.
 *
 * Invalid input blocks *Next* and *Finish*: the step reports it from its commit hook, which is where the
 * platform expects a step to refuse. The wizard only collects input — writing anything is left to
 * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsSiteCreationService].
 *
 * @param project the project the site is created in
 * @param directory the directory the wizard starts from
 */
class MkDocsCreateSiteWizard(
    project: Project,
    directory: VirtualFile,
) : AbstractWizard<Step>(MkDocsBundle.message("create.site.title"), project) {

    private val layoutStep = MkDocsLayoutStep(project, directory.path)

    private val siteInfoStep = MkDocsSiteInfoStep(project, directory)

    private val repositoryStep = MkDocsRepositoryStep(project, directory)

    private val copyrightStep = MkDocsCopyrightStep(project)

    private val featureStep = MkDocsFeatureStep(project)

    init {
        addStep(layoutStep)
        addStep(siteInfoStep)
        addStep(repositoryStep)
        addStep(copyrightStep)
        addStep(featureStep)
        init()
        layoutStep.onInputChanged = { refreshButtons() }
        siteInfoStep.onInputChanged = { refreshButtons() }
        repositoryStep.onInputChanged = { refreshButtons() }
        refreshButtons()
    }

    /**
     * Keeps the *Next* button — which reads *Finish* on the last step — in step with the input of the step
     * currently shown.
     *
     * The commit hook of each step still refuses invalid input — the button state is the visible half of the
     * same rule, not a replacement for it. Steps that cannot produce invalid input impose no condition, and
     * an earlier step cannot be left invalid: its own commit hook stops that.
     */
    private fun refreshButtons() {
        val error = when (currentStep) {
            0 -> layoutStep.validate()
            1 -> siteInfoStep.validate()
            2 -> repositoryStep.validate()
            else -> null
        }
        nextButton.isEnabled = error == null
    }

    /**
     * Carries what earlier steps produced into the step being entered.
     *
     * Both suggestions stop as soon as the user edited the field in question, so nothing typed is ever
     * overwritten by moving back and forth.
     */
    override fun updateStep() {
        super.updateStep()
        when (currentStep) {
            1 -> siteInfoStep.suggestSiteName(layoutStep.name)
            3 -> copyrightStep.suggestNoticeFor(siteInfoStep.currentAuthor())
        }
        refreshButtons()
    }

    /**
     * The template describing what the user asked for.
     *
     * Only meaningful after the wizard was closed with *Finish*.
     */
    val template: MkDocsSiteTemplate?
        get() {
            val base = layoutStep.buildTemplate() ?: return null
            return copyrightStep.applyTo(repositoryStep.applyTo(siteInfoStep.applyTo(base)))
                .copy(features = featureStep.selectedFeatures)
        }

    /**
     * Renders [error] the way the first step would, so a failure reported after the wizard closed reads
     * exactly like one inside it.
     *
     * @param error the reason the input was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String = layoutStep.messageFor(error)
}
