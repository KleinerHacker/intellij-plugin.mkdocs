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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * An optional feature that can be switched on for an MkDocs site.
 *
 * A feature is the extension mechanism behind the planned MkDocs extensions — Angular Material, I18N, Mike:
 * it contributes an entry to the feature step of the site creation wizard and, once selected, writes whatever
 * it needs into the site. Nothing implements this interface yet; the extension point exists so features can
 * be added later without touching the wizard.
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
}
