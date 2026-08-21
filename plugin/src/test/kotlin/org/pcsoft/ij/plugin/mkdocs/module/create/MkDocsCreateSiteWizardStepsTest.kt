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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.create.MkDocsMaterialWizardStep
import org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialSiteFeature
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsFeatureWizardStep
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the dynamic half of the site creation wizard: the pages a feature contributes appear and disappear
 * with the tick that selects the feature, they are the last pages of the wizard, they keep what was typed
 * into them, and they contribute to the resulting template.
 */
class MkDocsCreateSiteWizardStepsTest : BasePlatformTestCase() {

    /** Number of pages the wizard owns itself. */
    private val coreStepCount = 5

    /**
     * Use case: a feature is offered but not selected. The wizard then looks exactly as it did before any
     * feature existed — nothing was added behind the feature page.
     */
    fun `test shows no feature page while the feature is unselected`() {
        val feature = FakeFeature()
        registerFeature(feature)

        withWizard { wizard ->
            assertEquals(coreStepCount, wizard.stepsForTest().size)
        }
    }

    /**
     * Use case: the user ticks the feature. Its pages are appended behind the feature page, in the order the
     * feature returned them, and they are the last pages of the wizard — which is what puts *Finish* on the
     * real last page, because [com.intellij.ide.wizard.AbstractWizard] derives it from the step count.
     */
    fun `test appends the feature pages behind the feature page`() {
        val feature = FakeFeature()
        registerFeature(feature)

        withWizard { wizard ->
            select(wizard, feature, true)

            val steps = wizard.stepsForTest()
            assertEquals(coreStepCount + 2, steps.size)
            assertSame(feature.steps[0], steps[coreStepCount])
            assertSame(feature.steps[1], steps[coreStepCount + 1])
            assertSame("the last page belongs to the feature", feature.steps[1], steps.last())
        }
    }

    /**
     * Use case: the user changes their mind and unticks the feature. Its pages have to leave the wizard
     * entirely, not merely be skipped — a skipped page would leave *Finish* on a page that is no longer the
     * last one.
     */
    fun `test drops the feature pages when the feature is unselected again`() {
        val feature = FakeFeature()
        registerFeature(feature)

        withWizard { wizard ->
            select(wizard, feature, true)
            select(wizard, feature, false)

            val steps = wizard.stepsForTest()
            assertEquals(coreStepCount, steps.size)
            assertTrue("no feature page left", steps.none { it is MkDocsFeatureWizardStep })
        }
    }

    /**
     * Use case: the user fills in a feature page, unticks the feature by accident and ticks it again. The
     * very same page objects come back, so nothing typed is lost.
     */
    fun `test keeps what was typed into a feature page across a toggle`() {
        val feature = FakeFeature()
        registerFeature(feature)

        withWizard { wizard ->
            select(wizard, feature, true)
            feature.steps[0].value = "typed"

            select(wizard, feature, false)
            select(wizard, feature, true)

            val steps = wizard.stepsForTest()
            assertSame("the same page instance", feature.steps[0], steps[coreStepCount])
            assertEquals("typed", (steps[coreStepCount] as FakeStep).value)
        }
    }

    /**
     * Use case: the wizard is finished with a feature selected. Every page of that feature contributes to the
     * template, after the pages the wizard owns itself, and the feature instance the wizard collected the
     * input into is the one handed to the creation service.
     */
    fun `test folds the feature pages into the template`() {
        val feature = FakeFeature()
        registerFeature(feature)

        withWizard { wizard ->
            wizard.layoutStep.setNameForTest("site")
            wizard.layoutStep.setLocationForTest(tempDirectory().path)
            wizard.siteInfoStep.setSiteNameForTest("Site")
            wizard.siteInfoStep.setSiteDescriptionForTest("base")
            select(wizard, feature, true)

            val template = wizard.template
            assertNotNull(template)
            assertEquals("base|one|two", template!!.siteDescription)
            assertEquals(listOf<MkDocsSiteFeature>(feature), template.features)
        }
    }

    /**
     * Use case: the user ticks Angular Material, the one feature that really ships pages. Its four settings
     * pages — appearance, features, assets, extensions — have to appear behind the feature page, and they
     * have to belong to the per-wizard instance of the feature rather than to the registered extension.
     */
    fun `test appends the four Angular Material pages`() {
        registerFeature(MkDocsMaterialSiteFeature())

        withWizard { wizard ->
            val offered = wizard.featureStepForTest().offeredFeatures().single()
            select(wizard, offered, true)

            val steps = wizard.stepsForTest()
            assertEquals(coreStepCount + 4, steps.size)
            assertTrue(steps.drop(coreStepCount).all { it is MkDocsMaterialWizardStep })
            assertTrue(steps.drop(coreStepCount).all { (it as MkDocsMaterialWizardStep).feature === offered })
        }
    }

    /** Registers [feature] as the only site feature for the duration of the test. */
    private fun registerFeature(feature: MkDocsSiteFeature) {
        ExtensionTestUtil.maskExtensions(MkDocsSiteFeature.EP_NAME, listOf(feature), testRootDisposable)
    }

    /** Ticks or unticks [feature] on the feature page of [wizard]. */
    private fun select(wizard: MkDocsCreateSiteWizard, feature: MkDocsSiteFeature, selected: Boolean) {
        wizard.featureStepForTest().setSelectedForTest(feature, selected)
    }

    /** The directory the wizard starts from. */
    private fun tempDirectory(): VirtualFile = myFixture.tempDirFixture.findOrCreateDir("wizard")

    /** Runs [block] on a wizard and disposes it afterwards, whatever the outcome. */
    private fun withWizard(block: (MkDocsCreateSiteWizard) -> Unit) {
        val wizard = MkDocsCreateSiteWizard(project, tempDirectory())
        try {
            block(wizard)
        } finally {
            Disposer.dispose(wizard.disposable)
        }
    }

    /** Test double contributing two pages to the wizard. */
    private class FakeFeature : MkDocsSiteFeature {

        override val id: String = "fake"

        override val displayName: String = "Fake"

        override val description: String = "A feature that only exists in this test."

        /** The pages, created once so the test can compare instances. */
        val steps: List<FakeStep> = listOf(FakeStep(this, "one"), FakeStep(this, "two"))

        override fun createSteps(project: Project): List<MkDocsFeatureWizardStep> = steps

        override fun apply(project: Project, site: MkDocsSite) = Unit
    }

    /** Test double for a page contributed by a feature. */
    private class FakeStep(
        override val feature: MkDocsSiteFeature,
        private val marker: String,
    ) : MkDocsFeatureWizardStep {

        /** Stands for whatever the user types into the page. */
        var value: String = ""

        private val panel = JPanel()

        override fun _init() = Unit

        override fun _commit(finishChosen: Boolean) = Unit

        override fun getIcon(): Icon? = null

        override fun getComponent(): JComponent = panel

        override fun getPreferredFocusedComponent(): JComponent? = null

        override fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate =
            template.copy(siteDescription = template.siteDescription + "|" + marker)
    }
}
