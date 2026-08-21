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

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.schema.MkDocsMaterialSchemaFileProvider
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPage
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPages
import org.pcsoft.ij.plugin.mkdocs.material.create.MkDocsMaterialWizardStep
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsFeatureWizardStep
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfigWriter
import javax.swing.Icon
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsSiteFiles

/**
 * The Angular Material feature offered in the site creation wizard.
 *
 * Switching it on writes the Material theme into the configuration file of the new site, writes what the four
 * settings pages collected next to it, and attaches the [MkDocsMaterialFacet] to its module. All three are
 * what the detection would do on its own the moment it sees the theme — doing them here means the finished
 * site carries them without waiting for the next scan.
 *
 * Beyond the wizard this is where the whole feature meets the plugin: [syncFacet], [removeFacet] and
 * [schemaProvider] answer the plugin, so it never has to know that a Material facet or a Material schema
 * exists.
 *
 * The pages shown in the wizard are the same page objects the facet shows as tabs, built by
 * [MkDocsMaterialSettingsPages] in both places.
 *
 * Registered in `plugin.xml` under the `org.pcsoft.ij.plugin.mkdocs.siteFeature` extension point.
 */
class MkDocsMaterialSiteFeature : MkDocsSiteFeature {

    override val id: String = "angular-material"

    override val displayName: String
        get() = MkDocsMaterialBundle.message("feature.angularMaterial.name")

    override val description: String
        get() = MkDocsMaterialBundle.message("feature.angularMaterial.description")

    override val icon: Icon
        get() = MkDocsMaterialIcons.Feature

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
        MkDocsConfigWriter.setThemeName(project, site.configFile, MkDocsMaterialConfig.THEME_MATERIAL)
        MkDocsMaterialConfig.write(project, site.configFile, MkDocsMaterialSettings.EMPTY, settings)

        val module = ModuleUtilCore.findModuleForFile(site.root, project) ?: return
        if (MkDocsMaterialFacet.getInstance(module) != null) return
        // The facet belongs to a site, so there is nothing to add before the module holds one.
        MkDocsSiteFiles.findConfigFile(module) ?: return

        val facetType = MkDocsMaterialFacet.facetType
        val configuration = MkDocsMaterialFacetConfiguration().apply {
            themeName = MkDocsMaterialConfig.THEME_MATERIAL
        }
        val facet = facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.addFacet(facet)
        model.commit()
    }

    /**
     * Attaches the Angular Material facet to [module] or drops it again, following the theme of the site.
     *
     * The facet mirrors `theme.name` of the configuration file, which is what makes the feature detected
     * rather than remembered: a site that switches its theme by hand gains or loses the facet with the next
     * detection run. The name is remembered as written, so the facet tab can show what the file says rather
     * than the canonical spelling.
     */
    override fun syncFacet(module: Module, site: MkDocsSite) {
        val project = module.project
        if (!MkDocsMaterialConfig.isMaterialTheme(project, site.configFile)) {
            removeFacet(module)
            return
        }

        val themeName = MkDocsConfig.readThemeName(project, site.configFile)
            ?: MkDocsMaterialConfig.THEME_MATERIAL
        val existing = MkDocsMaterialFacet.getInstance(module)
        if (existing != null) {
            if (existing.configuration.themeName == themeName) return
            existing.configuration.themeName = themeName
            FacetManager.getInstance(module).facetConfigurationChanged(existing)
            return
        }

        val facetType = MkDocsMaterialFacet.facetType
        val configuration = MkDocsMaterialFacetConfiguration().apply { this.themeName = themeName }
        val facet = facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.addFacet(facet)
        model.commit()
    }

    /**
     * Takes the Angular Material facet off [module], if it carries one.
     *
     * Called both by [syncFacet], when the site stopped using the theme, and by the detection, when the
     * MkDocs facet itself goes away.
     */
    override fun removeFacet(module: Module) {
        val existing = MkDocsMaterialFacet.getInstance(module) ?: return
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.removeFacet(existing)
        model.commit()
    }

    /**
     * Hands the refined Material schema to the configuration files of Material sites.
     *
     * The provider answers for the sites this feature recognises and stays silent for every other one, so the
     * plain MkDocs mapping keeps them.
     */
    override fun schemaProvider(project: Project): JsonSchemaFileProvider =
        MkDocsMaterialSchemaFileProvider(project)
}
