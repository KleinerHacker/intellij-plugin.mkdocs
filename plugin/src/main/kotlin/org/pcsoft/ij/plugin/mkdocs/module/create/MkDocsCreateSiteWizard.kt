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
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsFeatureWizardStep
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplateError

/**
 * Wizard creating a new MkDocs site.
 *
 * Five pages belong to the wizard itself, ordered by how far a decision reaches: where the files go, what the
 * site is called, where its sources live, what its footer says, and finally which optional features it starts
 * with. Everything after the first two pages is optional and produces no configuration key when left empty.
 *
 * Behind the feature page the wizard shows whatever the selected features contribute, and it adds and drops
 * those pages while the selection changes. Nothing is skipped: *Finish* has to appear on the page that really
 * is the last one, and [AbstractWizard] decides that from the number of registered steps.
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

    /** The first page, read by [MkDocsSiteInfoStep] when it is entered. */
    internal val layoutStep = MkDocsLayoutStep(project, directory.path)

    /** The second page, read by [MkDocsCopyrightStep] when it is entered. */
    internal val siteInfoStep = MkDocsSiteInfoStep(project, directory)

    private val repositoryStep = MkDocsRepositoryStep(project, directory)

    private val copyrightStep = MkDocsCopyrightStep(project)

    private val featureStep = MkDocsFeatureStep(project)

    /** The project the site is created in, needed to build the pages of a feature. */
    private val siteProject: Project = project

    /**
     * The pages of every feature that was ever selected, created once and kept afterwards.
     *
     * Unticking a feature takes its pages out of the wizard but not out of here, so ticking it again brings
     * back exactly the objects the user typed into.
     */
    private val featureSteps = LinkedHashMap<MkDocsSiteFeature, List<MkDocsFeatureWizardStep>>()

    /** `true` once the dialog was resized for the first feature page added to it. */
    private var packedForFeatureSteps = false

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
        featureStep.onSelectionChanged = { syncFeatureSteps() }
        refreshButtons()
    }

    /**
     * Keeps the *Next* button — which reads *Finish* on the last step — in step with the input of the step
     * currently shown.
     *
     * The step object is asked, not its index: the pages behind the feature page come and go, so an index
     * says nothing about which page is in front of the user.
     *
     * The commit hook of each step still refuses invalid input — the button state is the visible half of the
     * same rule, not a replacement for it. Steps that cannot produce invalid input impose no condition, and
     * an earlier step cannot be left invalid: its own commit hook stops that.
     */
    private fun refreshButtons() {
        val error = when (val step = currentStepObject()) {
            is MkDocsValidatingStep -> step.validate()
            is MkDocsFeatureWizardStep -> step.validate()
            else -> null
        }
        nextButton.isEnabled = error == null
    }

    /**
     * Lets the page being entered pull in what earlier pages produced, and names it in the dialog title.
     *
     * Every suggestion a page makes stops as soon as the user edited the field in question, so nothing typed
     * is ever overwritten by moving back and forth.
     */
    override fun updateStep() {
        super.updateStep()
        val step = currentStepObject()
        (step as? MkDocsValidatingStep)?.onEnter(this)
        title = if (step is MkDocsFeatureWizardStep) {
            MkDocsBundle.message("create.site.title.feature", step.feature.displayName)
        } else {
            MkDocsBundle.message("create.site.title")
        }
        refreshButtons()
    }

    /** The step currently shown, or `null` while the wizard holds no step at all. */
    private fun currentStepObject(): Step? = mySteps.getOrNull(currentStep)

    /** The feature pages currently part of the wizard, in the order they are shown. */
    private fun currentFeatureSteps(): List<MkDocsFeatureWizardStep> =
        mySteps.filterIsInstance<MkDocsFeatureWizardStep>()

    /**
     * Brings the pages behind the feature page in line with the selected features.
     *
     * The pages are added to and removed from the step list itself rather than skipped while navigating:
     * [AbstractWizard] derives *Finish* from the number of registered steps, so a skipped step would put
     * *Finish* on the wrong page. Re-adding a page is harmless — the card layout keeps the component it
     * already knows, and [addStep] recognises it.
     */
    private fun syncFeatureSteps() {
        val wanted = featureStep.selectedFeatures.flatMap { feature ->
            featureSteps.getOrPut(feature) { feature.createSteps(siteProject) }
        }
        val present = currentFeatureSteps()
        if (wanted == present) return

        mySteps.removeAll(present.toSet())
        wanted.forEach { addStep(it) }

        if (!packedForFeatureSteps && wanted.isNotEmpty()) {
            packedForFeatureSteps = true
            pack()
        }
        updateStep()
    }

    /**
     * The template describing what the user asked for.
     *
     * Only meaningful after the wizard was closed with *Finish*.
     */
    val template: MkDocsSiteTemplate?
        get() {
            val base = layoutStep.buildTemplate() ?: return null
            val core = copyrightStep.applyTo(repositoryStep.applyTo(siteInfoStep.applyTo(base)))
                .copy(features = featureStep.selectedFeatures)
            return currentFeatureSteps().fold(core) { template, step -> step.applyTo(template) }
        }

    /**
     * Renders [error] the way the first step would, so a failure reported after the wizard closed reads
     * exactly like one inside it.
     *
     * @param error the reason the input was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String = layoutStep.messageFor(error)

    /** The feature page, so a test can switch a feature on and off. */
    @TestOnly
    internal fun featureStepForTest(): MkDocsFeatureStep = featureStep

    /** Every page the wizard currently holds, in the order they are shown. */
    @TestOnly
    internal fun stepsForTest(): List<Step> = mySteps.toList()
}
