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
import com.intellij.facet.FacetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import javax.swing.Icon

/** String id of the Angular Material facet as it appears in the module's `.iml` file. */
const val MKDOCS_MATERIAL_FACET_STRING_ID: String = "mkdocs-material"

/**
 * Registration of the Angular Material facet with the platform.
 *
 * Declared in `plugin.xml` via `com.intellij.facetType`. The facet belongs to an MkDocs site, but it is not
 * declared as a sub facet of the MkDocs facet: the platform is doing away with nested facets (IDEA-309067,
 * "Get rid of nested (child) facets"), and the only way to nest is a constructor deprecated for that reason.
 * The connection between the two is therefore kept by the plugin itself —
 * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService] only ever adds this facet next to an MkDocs
 * facet and drops it together with it.
 *
 * Since the tree no longer shows what belongs to what, the name and the icon have to. The facet is called
 * *MkDocs Angular Material*, so it reads as part of MkDocs and sorts next to the MkDocs facet in the flat
 * list, and its icon is the MkDocs logo itself with the Material glyph badged into the corner — the same
 * relation the tree used to draw, now drawn in the icon.
 */
class MkDocsMaterialFacetType : FacetType<MkDocsMaterialFacet, MkDocsMaterialFacetConfiguration>(
    MkDocsMaterialFacet.ID,
    MKDOCS_MATERIAL_FACET_STRING_ID,
    MkDocsMaterialBundle.message("facet.angularMaterial.name"),
) {

    override fun createDefaultConfiguration(): MkDocsMaterialFacetConfiguration = MkDocsMaterialFacetConfiguration()

    override fun createFacet(
        module: Module,
        name: String,
        configuration: MkDocsMaterialFacetConfiguration,
        underlyingFacet: Facet<*>?,
    ): MkDocsMaterialFacet = MkDocsMaterialFacet(module, name, configuration, underlyingFacet)

    /**
     * Always `true`, so the facet can be added and removed in the Project Structure dialog.
     *
     * Unlike the MkDocs facet this one is a decision of the user: adding it switches the site over to the
     * Material theme, removing it takes the theme out again — the change is carried into `mkdocs.yml` by
     * [MkDocsMaterialFacetListener]. Which module types may hold it is not restricted here, because a module
     * type says nothing about whether the module holds an MkDocs site; a facet added to a module without one
     * finds no configuration file to write to and is dropped again by the next detection run.
     */
    override fun isSuitableModuleType(moduleType: ModuleType<*>?): Boolean = true

    override fun getIcon(): Icon = MkDocsMaterialIcons.Feature
}
