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

package org.pcsoft.ij.plugin.mkdocs

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Central registry of every icon shipped with the plugin.
 *
 * Icon files live in the `icons` resource folder and are named `<name>@<size>.png`. Loading goes through
 * [IconLoader] to get lazy loading and caching.
 */
object MkDocsIcons {

    /** The MkDocs logo, used for the MkDocs facet. */
    @JvmField
    val MkDocs: Icon = load("mkdocs@16.png")

    /** The MkDocs logo at double size, for places that render larger icons. */
    @JvmField
    val MkDocsLarge: Icon = load("mkdocs@32.png")

    /** Small marker overlaid on the folder icon of an MkDocs site root in the project view. */
    @JvmField
    val Badge: Icon = load("mkdocs-badge@8.png")

    /** The site marker at the regular icon size, for places rendering it on its own. */
    @JvmField
    val BadgeLarge: Icon = load("mkdocs-badge@16.png")

    /** Icon of an MkDocs configuration file, replacing the generic YAML icon. */
    @JvmField
    val ConfigFile: Icon = load("mkdocs-file@16.png")

    /** The configuration file icon at double size, for places that render larger icons. */
    @JvmField
    val ConfigFileLarge: Icon = load("mkdocs-file@32.png")

    private fun load(fileName: String): Icon = IconLoader.getIcon("/icons/$fileName", MkDocsIcons::class.java)
}
