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

import com.intellij.facet.FacetConfiguration
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate

/**
 * Persistent state of the MkDocs facet.
 *
 * The state is written into the module's `.iml` file, so a detected MkDocs site survives an IDE restart
 * even before the detector had a chance to run again. The MkDocs configuration file always stays the
 * source of truth — the detector overwrites this state whenever the two disagree.
 */
class MkDocsFacetConfiguration : FacetConfiguration, PersistentStateComponent<MkDocsFacetConfiguration.State> {

    /**
     * Serialized form of the facet.
     *
     * @property siteName the `site_name` of the site, or the fall back directory name
     * @property configFilePath path of the MkDocs configuration file, relative to the site root
     * @property ownsModule `true` if the plugin created the module itself because the site root could not be
     *                      represented by an existing module — such a module is disposed again once the site
     *                      disappears
     * @property ownerModuleName name of the module the site root was detached from when the plugin created a
     *                           module for it, empty if the site root belonged to no module. Remembered so the
     *                           exclusion can be reverted once the site disappears
     * @property assetsDirName name of the directory holding the asset files, inside the documentation
     *                         directory. Unlike everything else here this is not derived from the
     *                         configuration file: MkDocs has no key for it, so the value chosen when the site
     *                         was created is remembered instead. Sites that came in through a checkout keep
     *                         the default
     * @property stylesheetsDirName name of the directory holding the style sheets, inside the documentation
     *                             directory. Remembered for the same reason as [assetsDirName]: MkDocs names
     *                             the individual files in `extra_css`, but never the directory holding them
     */
    class State {
        @JvmField
        var siteName: String = ""

        @JvmField
        var configFilePath: String = ""

        @JvmField
        var ownsModule: Boolean = false

        @JvmField
        var ownerModuleName: String = ""

        @JvmField
        var assetsDirName: String = MkDocsSiteTemplate.DEFAULT_ASSETS_DIR

        @JvmField
        var stylesheetsDirName: String = MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR
    }

    private var state = State()

    /** The name of the MkDocs site this facet describes. */
    var siteName: String
        get() = state.siteName
        set(value) {
            state.siteName = value
        }

    /** Path of the MkDocs configuration file, relative to the site root (e.g. `mkdocs.yml`). */
    var configFilePath: String
        get() = state.configFilePath
        set(value) {
            state.configFilePath = value
        }

    /** `true` if the module carrying this facet was created by the plugin for this site. */
    var ownsModule: Boolean
        get() = state.ownsModule
        set(value) {
            state.ownsModule = value
        }

    /** Name of the module the site root was detached from, empty if it belonged to no module. */
    var ownerModuleName: String
        get() = state.ownerModuleName
        set(value) {
            state.ownerModuleName = value
        }

    /** Name of the assets directory inside the documentation directory. */
    var assetsDirName: String
        get() = state.assetsDirName
        set(value) {
            state.assetsDirName = value
        }

    /** Name of the stylesheets directory inside the documentation directory. */
    var stylesheetsDirName: String
        get() = state.stylesheetsDirName
        set(value) {
            state.stylesheetsDirName = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    override fun createEditorTabs(
        editorContext: FacetEditorContext,
        validatorsManager: FacetValidatorsManager,
    ): Array<FacetEditorTab> = arrayOf(MkDocsFacetEditorTab(this, editorContext, validatorsManager))
}
