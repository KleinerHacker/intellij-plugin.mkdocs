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
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import javax.swing.Icon
import javax.swing.SwingConstants

/**
 * Central registry of every icon shipped with the plugin.
 *
 * Icon files live in the `icons` resource folder and are named `<name>.svg`, with a `_dark` suffix for the
 * dark theme variant. There is exactly one file per motif — the icons are vectors and the platform scales
 * them to whatever size a place renders. A separate file exists only where the drawing itself differs, which
 * is the case for the `-overlay` variants: at badge size the regular drawing collapses into a blot, so those
 * are drawn as their own, far simpler shapes.
 *
 * Every file is drawn on a 48x48 canvas, which is the size the platform would otherwise render the icon at,
 * so the size a place needs is set here on loading — see [load].
 */
object MkDocsIcons {

    /** The MkDocs logo, used for the MkDocs facet. */
    @JvmField
    val MkDocs: Icon = load("mkdocs.svg", 16)

    /**
     * The Angular Material motif, for places rendering it on its own.
     *
     * The MkDocs shield of the other icons carrying the Material glyph — a circle filled in its lower half.
     */
    @JvmField
    val MaterialBadge: Icon = load("mkdocs-angular-material.svg", 16)

    /**
     * The Angular Material motif at the size an inlay hint renders it at.
     *
     * An inlay sits inside a line of the editor and has to stay below the line height, which the 16 pixels of
     * [MaterialBadge] already exceed at the default font size.
     *
     * The place that made the fixing in [load] necessary: everything the hints API offers for an icon —
     * `smallScaledIcon`, `ScaleAwarePresentationFactory` — scales a `ScalableIcon` from the size it was
     * *loaded from*, which is the 48 unit canvas of the file, and hands out the full 48 pixels no matter what
     * was set on loading.
     */
    @JvmField
    val MaterialInlay: Icon = load("mkdocs-angular-material.svg", 12)

    /**
     * The Material glyph as overlaid on the MkDocs logo, and nothing else.
     *
     * Drawn as its own shape rather than as a shrunk [MaterialBadge]: at badge size the ring of the full
     * drawing closes into a blot, so the carrier disc takes the ring's place and only the filled lower half
     * remains as the glyph. The disc is filled in a strong blue with a white glyph and a white outer rim,
     * against the house colours of the other overlays — those sit on the folder icon of the project view,
     * while this one sits on the near white body of the MkDocs logo, where a light fill would disappear.
     */
    @JvmField
    val MaterialOverlay: Icon = load("mkdocs-angular-material-overlay.svg", 8)

    /**
     * Icon of the Angular Material facet: the MkDocs logo badged with the Material glyph.
     *
     * Composed rather than drawn, and deliberately so. The facet used to hang below the MkDocs facet in the
     * Project Structure tree, which said what belongs to what; nested facets are on their way out of the
     * platform (IDEA-309067), and the flat list says nothing. Sharing the MkDocs logo and adding a badge to
     * it puts that statement back where the tree used to make it.
     */
    @JvmField
    val Material: Icon = withBadge(MkDocs, MaterialOverlay)

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
     * Puts [badge] into the lower right corner of [base].
     *
     * @param base the icon to decorate
     * @param badge the marker to overlay, drawn at overlay size
     * @return a layered icon of the same size as [base]
     */
    @JvmStatic
    fun withBadge(base: Icon, badge: Icon): Icon {
        val layered = LayeredIcon.layeredIcon(arrayOf(base, badge))
        layered.setIcon(badge, 1, SwingConstants.SOUTH_EAST)
        return layered
    }

    /**
     * Loads the icon [fileName] from the `icons` resource folder and brings it to [size] pixels.
     *
     * Every icon file is drawn on a 48x48 canvas, and that canvas is what the platform takes as the size of
     * the icon — so without the scaling step here every icon would be rendered at 48 pixels. The scaling
     * happens on the vector, not on a rasterised image: [IconLoader] hands out a `ScalableIcon` for an SVG,
     * which [IconUtil.scale] re-renders at the requested size rather than resampling it.
     *
     * The scaled icon is then fixed at that size and handed out as a plain [Icon]. [IconUtil.scale] returns a
     * `ScalableIcon`, and a `ScalableIcon` scales from the size it was *loaded from* — the 48 unit canvas —
     * not from the size it was brought to here. Anything scaling it once more therefore throws that size away
     * and renders the icon at 48 pixels, which is what the inlay hints API does to every icon it is handed.
     * A fixed icon has nothing left to scale back up by, so the size set here is the size that is painted.
     *
     * @param fileName name of the SVG below `/icons`, without the `_dark` suffix of the dark variant
     * @param size the edge length in pixels the icon is to be rendered at
     */
    private fun load(fileName: String, size: Int): Icon {
        val icon = IconLoader.getIcon("/icons/$fileName", MkDocsIcons::class.java)
        return FixedSizeIcon(IconUtil.scale(icon, null, size.toFloat() / icon.iconWidth))
    }

    /**
     * An [Icon] painting [delegate] and nothing else, deliberately not a `ScalableIcon`.
     *
     * @property delegate the icon to paint, already at the size it is to keep
     */
    private class FixedSizeIcon(private val delegate: Icon) : Icon {

        override fun paintIcon(component: java.awt.Component?, graphics: java.awt.Graphics?, x: Int, y: Int) =
            delegate.paintIcon(component, graphics, x, y)

        override fun getIconWidth(): Int = delegate.iconWidth

        override fun getIconHeight(): Int = delegate.iconHeight
    }
}
