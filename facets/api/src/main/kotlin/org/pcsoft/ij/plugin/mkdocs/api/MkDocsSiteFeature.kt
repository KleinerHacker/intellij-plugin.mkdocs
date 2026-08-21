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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import javax.swing.Icon

/**
 * An optional feature that can be switched on for an MkDocs site.
 *
 * A feature is the extension mechanism behind the MkDocs extensions — Angular Material today, I18N and Mike
 * later: it contributes an entry to the feature step of the site creation wizard and, once selected, writes
 * whatever it needs into the site. Beyond the wizard it owns everything else that belongs to the feature
 * alone: the facet mirroring it and the refined JSON schema its sites are edited against.
 *
 * This interface is the whole contract between the plugin and a feature. The plugin MUST NOT reference a
 * feature from Kotlin code — every hand-off runs through the members below, which is what lets a feature
 * live in its own Gradle project.
 *
 * Implementations are registered in `plugin.xml` under the `org.pcsoft.ij.plugin.mkdocs.siteFeature`
 * extension point.
 */
interface MkDocsSiteFeature {

    companion object {

        /** The extension point every MkDocs site feature registers with. */
        @JvmField
        val EP_NAME: ExtensionPointName<MkDocsSiteFeature> =
            ExtensionPointName.create("org.pcsoft.ij.plugin.mkdocs.siteFeature")

        /**
         * Returns the features offered for [project], in registration order.
         *
         * @param project the project a site is about to be created in
         */
        @JvmStatic
        fun availableFeatures(project: Project): List<MkDocsSiteFeature> =
            EP_NAME.extensionList.filter { it.isAvailable(project) }
    }

    /** Stable identifier of the feature, used to remember a selection. Never shown to the user. */
    val id: String

    /** Name of the feature as shown in the wizard. */
    val displayName: String

    /** One line telling the user what the feature does, shown below [displayName]. */
    val description: String

    /** Icon shown next to [displayName], or `null` for no icon. */
    val icon: Icon?
        get() = null

    /**
     * Returns `true` if the feature can be offered for [project].
     *
     * Lets a feature hide itself where it cannot work — a missing IDE plugin, an unsupported IDE.
     *
     * @param project the project a site is about to be created in
     */
    fun isAvailable(project: Project): Boolean = true

    /**
     * Returns an instance of this feature dedicated to a single wizard.
     *
     * Extensions are application level singletons: two open wizards would otherwise share the same object,
     * and with it the pending input of the steps created by [createSteps]. A feature that carries such state
     * therefore returns a fresh instance here; a stateless one can keep returning itself.
     *
     * The instance handed out here is the one that ends up in [MkDocsSiteTemplate.features], so
     * [apply] runs on exactly the object the wizard filled in.
     */
    fun forWizard(): MkDocsSiteFeature = this

    /**
     * Creates the wizard pages this feature contributes, in the order they are shown.
     *
     * Called once per wizard, right after [forWizard], and the returned steps are kept for as long as that
     * wizard is open — they may hold user input, and they do keep it while the feature is unticked and
     * ticked again.
     *
     * @param project the project the site is created in
     * @return the pages, or an empty list if the feature has nothing to ask
     */
    fun createSteps(project: Project): List<MkDocsFeatureWizardStep> = emptyList()

    /**
     * Applies the feature to a freshly created site.
     *
     * Called inside a write action, after the site structure exists and the MkDocs facet has been created,
     * so an implementation can edit `mkdocs.yml`, add files and attach a facet of its own.
     *
     * @param project the project the site belongs to
     * @param site the site that was just created
     */
    fun apply(project: Project, site: MkDocsSite)

    /**
     * Brings the facet of this feature in line with [site].
     *
     * Called for every detected site whose module already carries the MkDocs facet, which is what makes a
     * feature detected rather than remembered: an implementation reads the configuration file, attaches its
     * own facet when the feature is in use and drops it again when it is not. A site the feature has nothing
     * to do with therefore leaves through the default implementation, which does nothing.
     *
     * Runs inside the write action of the detection.
     *
     * @param module the module representing the site
     * @param site the detected site
     */
    fun syncFacet(module: Module, site: MkDocsSite) {}

    /**
     * Drops the facet of this feature from [module].
     *
     * Called when the MkDocs facet itself goes away, so a feature facet never outlives the site it belongs
     * to. Unlike [syncFacet] there is no configuration file left to look at.
     *
     * @param module the module the facet is removed from
     */
    fun removeFacet(module: Module) {}

    /**
     * Returns the JSON schema this feature contributes for configuration files, or `null` for none.
     *
     * The providers returned here are offered before the generic MkDocs schema, so a file a feature claims is
     * edited against the refined schema of that feature.
     *
     * @param project the project the schema is asked for
     * @return the provider, or `null` if the feature refines no schema
     */
    fun schemaProvider(project: Project): JsonSchemaFileProvider? = null
}
