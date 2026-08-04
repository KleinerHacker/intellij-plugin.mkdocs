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

package org.pcsoft.ij.plugin.mkdocs.module.facet

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the registration of the MkDocs facet type, in particular that the facet cannot be assigned by hand.
 */
class MkDocsFacetTypeTest : BasePlatformTestCase() {

    /**
     * Use case: the user opens Project Structure and hits "+" on a module. MkDocs must not appear in the list
     * of addable facets — a hand-added facet would have no site behind it and would be dropped again by the
     * next detection run. The dialog builds that list from `isSuitableModuleType`, so it has to say no for
     * every module type, including the default one used by the plugin itself.
     */
    fun `test is never offered as an addable facet`() {
        val facetType = MkDocsFacet.facetType

        assertFalse(facetType.isSuitableModuleType(ModuleTypeManager.getInstance().defaultModuleType))
        for (moduleType in ModuleTypeManager.getInstance().registeredTypes) {
            assertFalse(
                "facet must not be offered for module type '${moduleType.id}'",
                facetType.isSuitableModuleType(moduleType),
            )
        }
        assertFalse(facetType.isSuitableModuleType(null as ModuleType<*>?))
    }

    /**
     * Use case: refusing the facet in the UI must not affect the detector. A project containing an
     * `mkdocs.yml` still has to receive the facet programmatically.
     */
    fun `test is still created by the detection`() {
        myFixture.addFileToProject("mkdocs.yml", "site_name: My Documentation\n")

        MkDocsModuleService.getInstance(project).sync()

        val facet = MkDocsFacet.getInstance(myFixture.module)
        assertNotNull("detection must create the facet regardless of the UI restriction", facet)
        assertEquals("My Documentation", facet!!.configuration.siteName)
    }
}
