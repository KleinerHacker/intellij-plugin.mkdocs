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
import com.intellij.facet.FacetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import javax.swing.Icon

/** String id of the MkDocs facet as it appears in the module's `.iml` file. */
const val MKDOCS_FACET_STRING_ID: String = "mkdocs"

/**
 * Registration of the MkDocs facet with the platform.
 *
 * Declared in `plugin.xml` via `com.intellij.facetType`.
 */
class MkDocsFacetType : FacetType<MkDocsFacet, MkDocsFacetConfiguration>(
    MkDocsFacet.ID,
    MKDOCS_FACET_STRING_ID,
    MkDocsBundle.message("facet.mkdocs.name"),
) {

    override fun createDefaultConfiguration(): MkDocsFacetConfiguration = MkDocsFacetConfiguration()

    override fun createFacet(
        module: Module,
        name: String,
        configuration: MkDocsFacetConfiguration,
        underlyingFacet: Facet<*>?,
    ): MkDocsFacet = MkDocsFacet(module, name, configuration, underlyingFacet)

    /**
     * Always `false`, which removes MkDocs from the facet list offered by the Project Structure dialog.
     *
     * The facet is not something the user configures: it mirrors the MkDocs configuration file found on disk
     * and is created, updated and dropped by
     * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService]. A hand-added facet would carry no site
     * information at all and would be removed again by the very next detection run, so it is not offered in
     * the first place. Creating the facet programmatically is unaffected by this method.
     *
     * An MkDocs site may still live inside any kind of module — no module type is excluded anywhere else.
     */
    override fun isSuitableModuleType(moduleType: ModuleType<*>?): Boolean = false

    override fun getIcon(): Icon = MkDocsIcons.MkDocs
}
