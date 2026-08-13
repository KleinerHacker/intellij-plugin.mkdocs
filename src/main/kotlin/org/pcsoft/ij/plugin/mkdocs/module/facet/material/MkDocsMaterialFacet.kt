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

package org.pcsoft.ij.plugin.mkdocs.module.facet.material

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.openapi.module.Module

/**
 * The Angular Material facet of an MkDocs site.
 *
 * The facet marks a site whose pages are rendered with the Material theme. It belongs next to the MkDocs
 * facet of the same module — a site is Angular Material only if it is an MkDocs site in the first place — but
 * it is not a sub facet of it: underlying facet types are deprecated in the platform, so
 * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService] keeps the two together instead.
 *
 * @param module the module representing the MkDocs site
 * @param name the name of the facet as shown in the Project Structure dialog
 * @param configuration the persistent state of the facet
 * @param underlyingFacet always `null`, kept because the platform creates every facet through this signature
 */
class MkDocsMaterialFacet(
    module: Module,
    name: String,
    configuration: MkDocsMaterialFacetConfiguration,
    underlyingFacet: Facet<*>?,
) : Facet<MkDocsMaterialFacetConfiguration>(facetType, module, name, configuration, underlyingFacet) {

    companion object {

        /** Type id of the Angular Material facet, used to look it up on a module. */
        @JvmField
        val ID: FacetTypeId<MkDocsMaterialFacet> = FacetTypeId(MKDOCS_MATERIAL_FACET_STRING_ID)

        /** The registered facet type instance. */
        @JvmStatic
        val facetType: FacetType<MkDocsMaterialFacet, MkDocsMaterialFacetConfiguration>
            get() = FacetType.findInstance(MkDocsMaterialFacetType::class.java)

        /**
         * Returns the Angular Material facet of [module], or `null` if the module has none.
         *
         * @param module the module to inspect
         */
        @JvmStatic
        fun getInstance(module: Module): MkDocsMaterialFacet? =
            FacetManager.getInstance(module).getFacetByType(ID)
    }
}
