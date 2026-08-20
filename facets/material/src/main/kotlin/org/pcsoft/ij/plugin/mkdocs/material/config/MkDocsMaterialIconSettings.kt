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

package org.pcsoft.ij.plugin.mkdocs.material.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.OptionTag
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsInstallationSettings

/**
 * The one setting of the Material feature that belongs to a project rather than to a site.
 *
 * It exists because icon completion needs a file the plugin cannot find on its own in every setup: the icons
 * the *Material for MkDocs* theme offers are not a list this plugin carries, they are the SVG files of the
 * installed package. [MkDocsMaterialIconLocator][
 * org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator] asks pip where the package lies, and
 * this setting is the answer for every setup pip cannot answer for — an interpreter somewhere else, a system
 * wide installation, a container mount.
 *
 * The path itself lives in [MkDocsInstallationSettings], which every feature shares, under the key
 * [INSTALLATION_KEY]. What stays here is the state of the older layout: a path written before the shared
 * settings existed is read once and moved over, so nobody loses the path their project already carries.
 *
 * Kept per project rather than per IDE: the path points into the environment of *this* project, and a second
 * project usually has one of its own.
 */
@Service(Service.Level.PROJECT)
@State(name = "MkDocsSettings", storages = [Storage("mkdocs.xml")])
class MkDocsMaterialIconSettings(private val project: Project) :
    PersistentStateComponent<MkDocsMaterialIconSettings.State> {

    /**
     * The serialised part of these settings.
     *
     * A class of its own because the platform instantiates the state through its no argument constructor; the
     * service itself carries the project and cannot be created that way.
     */
    class State {

        /**
         * The path of the older layout, kept only so it can be migrated.
         *
         * It keeps the name of the older layout in the file, so a project written by an older version of the
         * plugin is still read.
         */
        @get:OptionTag("iconPath")
        var legacyIconPath: String = ""
    }

    private var state: State = State()

    /**
     * The installation directory of the theme, or an empty string to ask pip.
     *
     * Points at what pip reports as its `Location`: the `site-packages` directory holding the package and the
     * `*.dist-info` beside it. That directory rather than the icon sets below it, because it is the one that
     * carries the metadata a chosen path can be checked against.
     */
    var iconPath: String
        get() {
            migrate()
            return shared().pathOf(INSTALLATION_KEY)
        }
        set(value) {
            state.legacyIconPath = ""
            shared().setPath(INSTALLATION_KEY, value)
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Moves a path of the older layout into the shared settings, once.
     *
     * A path is only taken over while the shared settings carry none: what the user last chose on the page
     * wins over what an older version of the plugin wrote.
     *
     * Both layouts named the icon directory; what is kept now is the installation directory above it, so a
     * path still ending in `material/templates/.icons` is cut back to the installation it belongs to.
     */
    private fun migrate() {
        val legacy = state.legacyIconPath.trim()
        if (legacy.isNotEmpty()) {
            state.legacyIconPath = ""
            if (shared().pathOf(INSTALLATION_KEY).isEmpty()) shared().setPath(INSTALLATION_KEY, legacy)
        }
        val shared = shared().pathOf(INSTALLATION_KEY).trim().replace('\\', '/').removeSuffix("/")
        if (shared.endsWith(ICONS_INSIDE_PACKAGE)) {
            shared().setPath(INSTALLATION_KEY, shared.removeSuffix(ICONS_INSIDE_PACKAGE).removeSuffix("/"))
        }
    }

    /**
     * Returns the settings every feature shares.
     */
    private fun shared(): MkDocsInstallationSettings = project.service<MkDocsInstallationSettings>()

    companion object {

        /** The path of the icon sets below the installation, as older versions of the plugin stored it. */
        private const val ICONS_INSIDE_PACKAGE = "material/templates/.icons"

        /** The key this feature keeps its installation directory under. */
        const val INSTALLATION_KEY: String = "material"
    }
}
