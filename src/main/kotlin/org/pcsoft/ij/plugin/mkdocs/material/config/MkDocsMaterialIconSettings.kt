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
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * The one setting of the Angular Material feature that belongs to a project rather than to a site.
 *
 * It exists because icon completion needs a file the plugin cannot find on its own in every setup: the icons
 * the *Material for MkDocs* theme offers are not a list this plugin carries, they are the SVG files of the
 * installed package. [MkDocsMaterialIconLocator][
 * org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator] finds them in the virtual environments
 * a project normally keeps next to its sources, and this setting is the answer for every other setup — an
 * interpreter somewhere else, a system wide installation, a container mount.
 *
 * Kept per project rather than per IDE: the path points into the environment of *this* project, and a second
 * project usually has one of its own.
 *
 * The state is still stored under the name `MkDocsSettings` in `mkdocs.xml`, which is where it was written
 * before the feature moved into its own package. Renaming it would silently drop the path of every project
 * that already carries one.
 */
@Service(Service.Level.PROJECT)
@State(name = "MkDocsSettings", storages = [Storage("mkdocs.xml")])
class MkDocsMaterialIconSettings : PersistentStateComponent<MkDocsMaterialIconSettings> {

    /**
     * The directory holding the icon sets of the theme, or an empty string to search for it.
     *
     * Points at the directory the sets lie *in* — the one holding `material`, `fontawesome` and `octicons` —
     * which inside an installed package is `material/templates/.icons`.
     */
    var iconPath: String = ""

    override fun getState(): MkDocsMaterialIconSettings = this

    override fun loadState(state: MkDocsMaterialIconSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {

        /**
         * Returns the settings of [project].
         *
         * @param project the project whose settings are requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsMaterialIconSettings = project.service()
    }
}
