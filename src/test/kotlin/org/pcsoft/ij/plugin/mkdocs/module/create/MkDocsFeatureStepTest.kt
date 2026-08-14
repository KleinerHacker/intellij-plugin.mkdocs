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
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the feature page of the site creation wizard: that a change to the selection is reported, and that
 * the features it hands out are the per-wizard instances the input is collected into.
 */
class MkDocsFeatureStepTest : BasePlatformTestCase() {

    /**
     * Use case: the user ticks and unticks a feature. Both changes have to reach the wizard, because the
     * pages of the feature are added and dropped from that notification.
     */
    fun `test reports every change of the selection`() {
        val feature = FakeFeature()
        registerFeature(feature)

        val step = MkDocsFeatureStep(project)
        var changes = 0
        step.onSelectionChanged = { changes++ }

        val offered = step.offeredFeatures().single()
        step.setSelectedForTest(offered, true)
        assertEquals(1, changes)
        assertEquals(listOf(offered), step.selectedFeatures)

        step.setSelectedForTest(offered, false)
        assertEquals(2, changes)
        assertTrue(step.selectedFeatures.isEmpty())
    }

    /**
     * Use case: two wizards are open at once. The extension itself is an application level singleton, so the
     * page must not hand out that instance — it asks for a wizard local one and offers that, which is also
     * the instance that ends up in the template.
     */
    fun `test offers the per wizard instance of a feature`() {
        val feature = FakeFeature()
        registerFeature(feature)

        val step = MkDocsFeatureStep(project)
        val offered = step.offeredFeatures().single()

        assertNotSame("the extension itself must not be offered", feature, offered)

        step.setSelectedForTest(offered, true)
        assertSame(offered, step.selectedFeatures.single())
    }

    /** Registers [feature] as the only site feature for the duration of the test. */
    private fun registerFeature(feature: MkDocsSiteFeature) {
        ExtensionTestUtil.maskExtensions(MkDocsSiteFeature.EP_NAME, listOf(feature), testRootDisposable)
    }

    /** Test double handing out a fresh instance per wizard. */
    private class FakeFeature : MkDocsSiteFeature {

        override val id: String = "fake"

        override val displayName: String = "Fake"

        override val description: String = "A feature that only exists in this test."

        override fun forWizard(): MkDocsSiteFeature = FakeFeature()

        override fun apply(project: Project, site: MkDocsSite) = Unit
    }
}
