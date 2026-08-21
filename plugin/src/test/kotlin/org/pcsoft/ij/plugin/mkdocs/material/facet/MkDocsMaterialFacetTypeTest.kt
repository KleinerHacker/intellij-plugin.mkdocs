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

package org.pcsoft.ij.plugin.mkdocs.material.facet

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the registration of the Angular Material facet type, which is what decides where the facet may
 * appear and what it is stacked on.
 */
class MkDocsMaterialFacetTypeTest : BasePlatformTestCase() {

    /**
     * Use case: the facet is looked up on a module by its type id and written into the `.iml` under its
     * string id. Both are part of the persisted project, so renaming either one silently drops the facet of
     * every existing project — they are pinned here.
     */
    fun `test is registered under the expected ids`() {
        val facetType = MkDocsMaterialFacet.facetType

        assertEquals(MkDocsMaterialFacet.ID, facetType.id)
        assertEquals(MKDOCS_MATERIAL_FACET_STRING_ID, facetType.stringId)
        assertEquals("mkdocs-material", facetType.stringId)
    }

    /**
     * Use case: sub facets are deprecated in the platform, so the type must not declare an underlying one —
     * a deprecated constructor would be the only way to do it. Pairing the facet with the MkDocs facet is
     * the detection's job instead, which the detection test covers.
     */
    fun `test declares no underlying facet type`() {
        assertNull(MkDocsMaterialFacet.facetType.underlyingFacetType)
    }

    /**
     * Use case: the Project Structure dialog lists facets flat, because nested facets are on their way out of
     * the platform. Nothing in the tree says any more that this facet belongs to MkDocs, so its name has to —
     * it is the only cue left besides the icon, and it is what puts the two entries next to each other in the
     * alphabetically sorted list.
     */
    fun `test carries mkdocs in its presentable name`() {
        val presentableName = MkDocsMaterialFacet.facetType.presentableName

        assertTrue(
            "the name must read as part of MkDocs, but was '$presentableName'",
            presentableName.startsWith("MkDocs"),
        )
        assertTrue(presentableName.contains("Angular Material"))
    }

    /**
     * Use case: unlike the MkDocs facet, this one is a decision of the user — it must be addable in the
     * Project Structure dialog, for every module type, because the underlying MkDocs facet already narrows
     * the offer down to modules holding a site.
     */
    fun `test is offered for every module type`() {
        val facetType = MkDocsMaterialFacet.facetType

        assertTrue(facetType.isSuitableModuleType(ModuleTypeManager.getInstance().defaultModuleType))
        for (moduleType in ModuleTypeManager.getInstance().registeredTypes) {
            assertTrue(
                "facet must be offered for module type '${moduleType.id}'",
                facetType.isSuitableModuleType(moduleType),
            )
        }
        assertTrue(facetType.isSuitableModuleType(null as ModuleType<*>?))
    }

    /**
     * Use case: the facet is shown in the Project Structure tree and in the facet chooser, both of which ask
     * the type for an icon. A missing icon is only noticed at run time, so the type is asked here.
     *
     * The icon is the MkDocs logo with the Material glyph badged onto it, which is the second cue — next to
     * the name — that the facet belongs to MkDocs. Badging must not change the footprint: an icon wider or
     * taller than the plain MkDocs one would push the row out of line with the MkDocs facet above it.
     */
    fun `test is badged onto the mkdocs icon without growing it`() {
        val icon = MkDocsMaterialFacet.facetType.icon
        assertNotNull("the facet type must provide an icon", icon)

        assertEquals("badging must not widen the icon", MkDocsIconLoader.Logo.iconWidth, icon!!.iconWidth)
        assertEquals("badging must not heighten the icon", MkDocsIconLoader.Logo.iconHeight, icon.iconHeight)
    }

    /**
     * Use case: the platform creates the facet through its type when a project is loaded or when the user
     * adds it. The created facet has to carry the default configuration and the MkDocs facet it is stacked
     * on, otherwise the tab has nothing to show and the listener nothing to write.
     */
    fun `test creates a facet carrying its configuration`() {
        val facetType = MkDocsMaterialFacet.facetType
        val configuration = facetType.createDefaultConfiguration()

        val facet = facetType.createFacet(myFixture.module, "Angular Material", configuration, null)

        assertEquals(myFixture.module, facet.module)
        assertEquals("Angular Material", facet.name)
        assertSame(configuration, facet.configuration)
    }
}
