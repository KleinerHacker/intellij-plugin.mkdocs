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

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.openapi.module.Module

/**
 * Marks a module as containing an MkDocs site.
 *
 * This is the anchor of the whole MkDocs module system: everything that is optional about a site — Angular
 * Material, I18N, Mike — is meant to be added later as its own facet declaring [ID] as its underlying facet
 * type, exactly like framework facets stack on a Java module.
 *
 * @param module the module the facet belongs to
 * @param name the facet name shown in Project Structure
 * @param configuration the persistent facet state
 * @param underlyingFacet always `null` — the MkDocs facet is a root facet
 */
class MkDocsFacet(
    module: Module,
    name: String,
    configuration: MkDocsFacetConfiguration,
    underlyingFacet: Facet<*>?,
) : Facet<MkDocsFacetConfiguration>(facetType, module, name, configuration, underlyingFacet) {

    companion object {

        /** Identifier used to look up the facet on a module and to declare it as an underlying facet type. */
        @JvmField
        val ID: FacetTypeId<MkDocsFacet> = FacetTypeId("mkdocs")

        /** The registered [FacetType] instance for [ID]. */
        val facetType: FacetType<MkDocsFacet, MkDocsFacetConfiguration>
            get() = FacetType.findInstance(MkDocsFacetType::class.java)

        /**
         * Returns the MkDocs facet of [module], or `null` if the module is not an MkDocs module.
         *
         * @param module the module to inspect
         */
        @JvmStatic
        fun getInstance(module: Module): MkDocsFacet? = FacetManager.getInstance(module).getFacetByType(ID)
    }
}
