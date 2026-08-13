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

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigWriter
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature
import javax.swing.Icon

/**
 * The Angular Material feature offered in the site creation wizard.
 *
 * Switching it on writes the Material theme into the configuration file of the new site and attaches the
 * [MkDocsMaterialFacet] to its module. Both steps are what the detection would do on its own the moment it
 * sees the theme — doing them here means the finished site carries the facet without waiting for the next
 * scan.
 *
 * Registered in `plugin.xml` under the `org.pcsoft.ij.plugin.mkdocs.siteFeature` extension point.
 */
class MkDocsMaterialSiteFeature : MkDocsSiteFeature {

    override val id: String = "angular-material"

    override val displayName: String
        get() = MkDocsBundle.message("feature.angularMaterial.name")

    override val description: String
        get() = MkDocsBundle.message("feature.angularMaterial.description")

    override val icon: Icon
        get() = MkDocsIcons.Material

    override fun apply(project: Project, site: MkDocsSite) {
        MkDocsConfigWriter.setThemeName(project, site.configFile, MkDocsConfig.THEME_MATERIAL)

        val module = ModuleUtilCore.findModuleForFile(site.root, project) ?: return
        if (MkDocsMaterialFacet.getInstance(module) != null) return
        // The facet belongs next to the MkDocs facet, so there is nothing to add before the site has one.
        MkDocsFacet.getInstance(module) ?: return

        val facetType = MkDocsMaterialFacet.facetType
        val configuration = MkDocsMaterialFacetConfiguration().apply { themeName = MkDocsConfig.THEME_MATERIAL }
        val facet = facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.addFacet(facet)
        model.commit()
    }
}
