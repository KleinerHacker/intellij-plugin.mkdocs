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
 * Icon files live in the `icons` resource folder and are named `<name>@<size>.svg`, with a `_dark`
 * suffix for the dark theme variant. Loading goes through [IconLoader] to get lazy loading and caching.
 */
object MkDocsIcons {

    /** The MkDocs logo, used for the MkDocs facet. */
    @JvmField
    val MkDocs: Icon = load("mkdocs@16.svg")

    /** The MkDocs logo at double size, for places that render larger icons. */
    @JvmField
    val MkDocsLarge: Icon = load("mkdocs@32.svg")

    /**
     * Small marker overlaid on the folder icon of an MkDocs site root in the project view.
     *
     * A circle, so it stays apart from the other two site markers even at overlay size — all three can
     * appear directly below one another in the tree.
     */
    @JvmField
    val Badge: Icon = load("mkdocs-badge@8.svg")

    /** The site marker at the regular icon size, for places rendering it on its own. */
    @JvmField
    val BadgeLarge: Icon = load("mkdocs-badge@16.svg")

    /**
     * Marker overlaid on the folder icon of the documentation directory of a site.
     *
     * A portrait sheet with a folded corner, keeping it apart from the circle of [Badge] by silhouette
     * alone.
     */
    @JvmField
    val DocsBadge: Icon = load("mkdocs-docs-badge@8.svg")

    /** The documentation directory marker at the regular icon size, for places rendering it on its own. */
    @JvmField
    val DocsBadgeLarge: Icon = load("mkdocs-docs-badge@16.svg")

    /**
     * Marker overlaid on the folder icon of the assets directory of a site.
     *
     * A landscape picture frame, again to keep the three markers of a site apart by silhouette alone.
     */
    @JvmField
    val AssetsBadge: Icon = load("mkdocs-assets-badge@8.svg")

    /** The assets directory marker at the regular icon size, for places rendering it on its own. */
    @JvmField
    val AssetsBadgeLarge: Icon = load("mkdocs-assets-badge@16.svg")

    /** Icon of a Markdown file below the documentation directory of a site, replacing the generic one. */
    @JvmField
    val MarkdownFile: Icon = load("mkdocs-md@16.svg")

    /** The Markdown page icon at double size, for places that render larger icons. */
    @JvmField
    val MarkdownFileLarge: Icon = load("mkdocs-md@32.svg")

    /**
     * Icon of the *Site Page* tool window, shown on the tool window stripe.
     *
     * Drawn at twenty pixels rather than sixteen, which is the size the stripe of the current IDE user
     * interface renders. A plain outline without the gradient the file icons carry — at that size a gradient
     * turns into a smudge.
     */
    @JvmField
    val SitePageToolWindow: Icon = load("mkdocs-site-page@20.svg")

    /** Icon of an MkDocs configuration file, replacing the generic YAML icon. */
    @JvmField
    val ConfigFile: Icon = load("mkdocs-file@16.svg")

    /** The configuration file icon at double size, for places that render larger icons. */
    @JvmField
    val ConfigFileLarge: Icon = load("mkdocs-file@32.svg")

    private fun load(fileName: String): Icon = IconLoader.getIcon("/icons/$fileName", MkDocsIcons::class.java)
}
