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
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPage
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPages
import org.pcsoft.ij.plugin.mkdocs.module.create.material.MkDocsMaterialWizardStep
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfigWriter
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsFeatureWizardStep
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature
import javax.swing.Icon

/**
 * The Angular Material feature offered in the site creation wizard.
 *
 * Switching it on writes the Material theme into the configuration file of the new site, writes what the four
 * settings pages collected next to it, and attaches the [MkDocsMaterialFacet] to its module. All three are
 * what the detection would do on its own the moment it sees the theme — doing them here means the finished
 * site carries them without waiting for the next scan.
 *
 * The pages shown in the wizard are the same page objects the facet shows as tabs, built by
 * [MkDocsMaterialSettingsPages] in both places.
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

    /**
     * What the pages of this wizard collected so far.
     *
     * Empty on the instance registered with the extension point — that one is an application singleton and
     * never sees a wizard, which is what [forWizard] is for.
     */
    var settings: MkDocsMaterialSettings = MkDocsMaterialSettings.EMPTY
        private set

    /**
     * A wizard fills in the instance it was handed, so every wizard needs one of its own — the registered
     * extension is shared by the whole application.
     */
    override fun forWizard(): MkDocsSiteFeature = MkDocsMaterialSiteFeature()

    override fun createSteps(project: Project): List<MkDocsFeatureWizardStep> {
        val pages = MkDocsMaterialSettingsPages(project)
        pages.reset(settings)
        return pages.pages.map { MkDocsMaterialWizardStep(this, it) }
    }

    /**
     * Folds what [page] holds into the settings of this instance.
     *
     * Called by the wizard step of the page while the wizard collects its result; each page only replaces the
     * keys it owns, so the four of them add up.
     *
     * @param page the page that was filled in
     */
    internal fun collectFrom(page: MkDocsMaterialSettingsPage) {
        settings = page.applyTo(settings)
    }

    override fun apply(project: Project, site: MkDocsSite) {
        MkDocsConfigWriter.setThemeName(project, site.configFile, MkDocsConfig.THEME_MATERIAL)
        MkDocsMaterialConfig.write(project, site.configFile, MkDocsMaterialSettings.EMPTY, settings)

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
