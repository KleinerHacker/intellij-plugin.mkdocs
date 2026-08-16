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

import javax.swing.Icon
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader

/**
 * Central registry of every icon shipped with the plugin itself.
 *
 * Icon files live in the `icons` resource folder and are named `<name>.svg`, with a `_dark` suffix for the
 * dark theme variant. There is exactly one file per motif — the icons are vectors and the platform scales
 * them to whatever size a place renders. A separate file exists only where the drawing itself differs, which
 * is the case for the `-overlay` variants: at badge size the regular drawing collapses into a blot, so those
 * are drawn as their own, far simpler shapes.
 *
 * The icons of a feature are none of this registry's business — they belong to the feature and live in a
 * registry of its own, next to the code that paints them.
 *
 * Every file is drawn on a 48x48 canvas, which is the size the platform would otherwise render the icon at,
 * so the size a place needs is set here on loading — see [MkDocsIconLoader.load].
 */
object MkDocsIcons {

    /**
     * The MkDocs logo, used for the MkDocs facet.
     *
     * The only icon of the plugin that does not live here: a feature badges it to say that its own facet
     * belongs to an MkDocs site, so the file and the loaded icon sit in [MkDocsIconLoader], which every
     * feature can reach without reaching into the plugin.
     */
    @JvmField
    val MkDocs: Icon = MkDocsIconLoader.Logo

    /**
     * Marker of an MkDocs site root, for places rendering it on its own.
     *
     * A circle, so it stays apart from the other two site markers even at overlay size — all three can
     * appear directly below one another in the tree.
     */
    @JvmField
    val Badge: Icon = load("mkdocs-badge.svg", 16)

    /** The site marker as overlaid on the folder icon of a site root in the project view. */
    @JvmField
    val BadgeOverlay: Icon = load("mkdocs-badge-overlay.svg", 8)

    /**
     * Marker of the documentation directory of a site, for places rendering it on its own.
     *
     * A portrait sheet with a folded corner, keeping it apart from the circle of [Badge] by silhouette
     * alone.
     */
    @JvmField
    val DocsBadge: Icon = load("mkdocs-docs-badge.svg", 16)

    /** The documentation directory marker as overlaid on the folder icon in the project view. */
    @JvmField
    val DocsBadgeOverlay: Icon = load("mkdocs-docs-badge-overlay.svg", 8)

    /**
     * Marker of the build output directory of a site, for places rendering it on its own.
     *
     * A box seen from the side, which is what the directory holds: the built site, packed and ready to be
     * published. The silhouette is closed and squat, so it does not get mistaken for the upright sheet of
     * [DocsBadge] beside it in the gutter of the configuration file.
     */
    @JvmField
    val SiteDirBadge: Icon = load("mkdocs-site-dir-badge.svg", 16)

    /**
     * Marker of the assets directory of a site, for places rendering it on its own.
     *
     * A landscape picture frame, again to keep the three markers of a site apart by silhouette alone.
     */
    @JvmField
    val AssetsBadge: Icon = load("mkdocs-assets-badge.svg", 16)

    /** The assets directory marker as overlaid on the folder icon in the project view. */
    @JvmField
    val AssetsBadgeOverlay: Icon = load("mkdocs-assets-badge-overlay.svg", 8)

    /**
     * Marker of the stylesheets directory of a site, for places rendering it on its own.
     *
     * A brush on a diagonal axis — none of the other three markers has a slanted main axis, so the four stay
     * apart by silhouette even at overlay size.
     */
    @JvmField
    val StylesheetsBadge: Icon = load("mkdocs-stylesheets-badge.svg", 16)

    /** The stylesheets directory marker as overlaid on the folder icon in the project view. */
    @JvmField
    val StylesheetsBadgeOverlay: Icon = load("mkdocs-stylesheets-badge-overlay.svg", 8)

    /**
     * Icon of a style sheet the site loads.
     *
     * Only files named in `extra_css` get it. A style sheet MkDocs does not know about changes nothing about
     * the built site, so it keeps the icon of a plain CSS file.
     */
    @JvmField
    val StylesheetFile: Icon = load("mkdocs-css.svg", 16)

    /** Icon of a Markdown file below the documentation directory of a site, replacing the generic one. */
    @JvmField
    val MarkdownFile: Icon = load("mkdocs-md.svg", 16)

    /**
     * Icon of the *Site Page* tool window, shown on the tool window stripe.
     *
     * A plain outline without the gradient the file icons carry — the stripe renders it small enough that a
     * gradient turns into a smudge.
     */
    @JvmField
    val SitePageToolWindow: Icon = load("mkdocs-site-page.svg", 20)

    /**
     * Icon of a section of the navigation in the *Site Page* tool window.
     *
     * A folder, because a section groups entries without being a page of its own — but drawn in the blue of
     * the other MkDocs icons, so a section of a site is not mistaken for a directory of the project.
     */
    @JvmField
    val NavSection: Icon = load("mkdocs-section.svg", 16)

    /**
     * Icon of the `requirements.txt` next to the configuration file of a site.
     *
     * That file pins the MkDocs version and the theme and plugin packages a site is built with, so it belongs
     * to the site rather than to the project at large. Only the one directly beside `mkdocs.yml` gets it.
     */
    @JvmField
    val RequirementsFile: Icon = load("mkdocs-requirements.svg", 16)

    /** Icon of an MkDocs configuration file, replacing the generic YAML icon. */
    @JvmField
    val ConfigFile: Icon = load("mkdocs-file.svg", 16)

    /**
     * Loads the icon [fileName] from the `icons` resource folder and brings it to [size] pixels.
     *
     * @param fileName name of the SVG below `/icons`, without the `_dark` suffix of the dark variant
     * @param size the edge length in pixels the icon is to be rendered at
     */
    private fun load(fileName: String, size: Int): Icon =
        MkDocsIconLoader.load("/icons/$fileName", size, MkDocsIcons::class.java)
}
