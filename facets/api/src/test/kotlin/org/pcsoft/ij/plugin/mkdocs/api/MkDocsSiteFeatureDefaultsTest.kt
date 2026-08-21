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

package org.pcsoft.ij.plugin.mkdocs.api

import com.intellij.facet.FacetManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the half of [MkDocsSiteFeature] a feature does not have to implement. The plugin calls every one of
 * these members for every registered feature on every detection run, so a feature that only wants a wizard
 * page must be able to ignore them — and ignoring them must not change anything.
 */
class MkDocsSiteFeatureDefaultsTest : BasePlatformTestCase() {

    /**
     * Use case: a feature contributes nothing but a wizard entry. Asking it for wizard pages, an icon or a
     * schema must yield nothing rather than fail, so the wizard and the schema factory can call it blindly.
     */
    fun `test a feature implementing nothing optional answers with nothing`() {
        val feature = MinimalFeature()

        assertNull("a feature without an icon must not claim one", feature.icon)
        assertTrue("a feature must be offered unless it says otherwise", feature.isAvailable(project))
        assertSame("a stateless feature keeps returning itself", feature, feature.forWizard())
        assertTrue("a feature without pages contributes none", feature.createSteps(project).isEmpty())
        assertNull("a feature refining no schema contributes none", feature.schemaProvider(project))
    }

    /**
     * Use case: the detection finds a site and offers it to every registered feature. A feature that has no
     * facet of its own must leave the module's facet model exactly as it was.
     */
    fun `test syncing and removing leave a module without a facet alone`() {
        val configFile = myFixture.addFileToProject("mkdocs.yml", "site_name: Handbook\n").virtualFile
        val site = MkDocsSite(root = configFile.parent, configFile = configFile, siteName = "Handbook")
        val feature = MinimalFeature()
        val before = facetCount()

        feature.syncFacet(myFixture.module, site)
        feature.removeFacet(myFixture.module)

        assertEquals("the default implementations must not touch the facet model", before, facetCount())
    }

    /** The number of facets currently on the fixture module. */
    private fun facetCount(): Int = FacetManager.getInstance(myFixture.module).allFacets.size

    /**
     * A feature implementing nothing but the members that have no sensible default.
     *
     * @property applied what [apply] was called with, so the test can tell "did nothing" from "was never
     *                   called"
     */
    private class MinimalFeature : MkDocsSiteFeature {

        var applied: VirtualFile? = null
            private set

        override val id: String = "test-feature"

        override val displayName: String = "Test Feature"

        override val description: String = "A feature that implements nothing optional."

        override fun apply(project: Project, site: MkDocsSite) {
            applied = site.configFile
        }
    }
}
