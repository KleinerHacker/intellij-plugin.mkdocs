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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import javax.swing.Icon
import javax.swing.SwingConstants

/**
 * Brings an icon file to the size the place rendering it needs.
 *
 * Every icon of the plugin and of its features is drawn on a 48x48 canvas, which is the size the platform
 * would otherwise render it at, so the size has to be set on loading. That is the whole reason this exists —
 * and the reason it is shared rather than private to one registry: a feature ships icons of its own and must
 * be able to load them the same way without reaching into the plugin.
 */
object MkDocsIconLoader {

    /**
     * The MkDocs logo, at the 16 pixels of a facet, a list entry and a menu item.
     *
     * Shared rather than owned by the plugin: it is the one drawing a feature has to be able to build on. The
     * Angular Material facet badges exactly this icon to say that its facet belongs to an MkDocs site, and it
     * must not reach into the plugin to get at it.
     */
    @JvmField
    val Logo: Icon = load("/icons/mkdocs.svg", 16, MkDocsIconLoader::class.java)

    /**
     * A folder, at the 16 pixels of a list entry, for anything standing for a level rather than for a thing.
     *
     * Shared for the same reason the logo is: a feature completing its values one level at a time has to be
     * able to say *this entry holds more* without reaching into the plugin for the drawing. Kept in the blue
     * of the other icons, so a level of this plugin is not mistaken for a directory of the project.
     *
     * A feature marking such a level as its own badges this icon, exactly as it badges the logo.
     */
    @JvmField
    val Folder: Icon = load("/icons/mkdocs-folder.svg", 16, MkDocsIconLoader::class.java)

    /**
     * Loads the icon [path] from the class path and brings it to [size] pixels.
     *
     * The scaling happens on the vector, not on a rasterised image: [IconLoader] hands out a `ScalableIcon`
     * for an SVG, which [IconUtil.scale] re-renders at the requested size rather than resampling it.
     *
     * The scaled icon is then fixed at that size and handed out as a plain [Icon]. [IconUtil.scale] returns a
     * `ScalableIcon`, and a `ScalableIcon` scales from the size it was *loaded from* — the 48 unit canvas —
     * not from the size it was brought to here. Anything scaling it once more therefore throws that size away
     * and renders the icon at 48 pixels, which is what the inlay hints API does to every icon it is handed.
     * A fixed icon has nothing left to scale back up by, so the size set here is the size that is painted.
     *
     * @param path absolute class path of the SVG, without the `_dark` suffix of the dark variant
     * @param size the edge length in pixels the icon is to be rendered at
     * @param owner a class of the module shipping the file, used to find it on the class path
     */
    @JvmStatic
    fun load(path: String, size: Int, owner: Class<*>): Icon {
        val icon = IconLoader.getIcon(path, owner)
        return FixedSizeIcon(IconUtil.scale(icon, null, size.toFloat() / icon.iconWidth))
    }

    /**
     * Brings [icon] to [size] pixels and fixes it there.
     *
     * The counterpart of [load] for an icon that is already loaded — the drawings of an installed
     * *Material for MkDocs* come out of the user's environment, not off the class path, and still have to
     * leave at the size of the place painting them.
     *
     * @param icon the icon to resize
     * @param size the edge length in pixels the icon is to be rendered at
     */
    @JvmStatic
    fun fixSize(icon: Icon, size: Int): Icon {
        if (icon.iconWidth <= 0) return icon
        return FixedSizeIcon(IconUtil.scale(icon, null, size.toFloat() / icon.iconWidth))
    }

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
     * A [ScalableIcon] painting [delegate] and keeping the scaled size as its baseline.
     *
     * @property delegate the icon to paint, already at the size it is to keep
     */
    private class FixedSizeIcon(private val delegate: Icon) : ScalableIcon {

        override fun paintIcon(component: java.awt.Component?, graphics: java.awt.Graphics?, x: Int, y: Int) =
            delegate.paintIcon(component, graphics, x, y)

        override fun getIconWidth(): Int = delegate.iconWidth

        override fun getIconHeight(): Int = delegate.iconHeight

        override fun scale(scaleFactor: Float): Icon {
            if (scaleFactor == 1f) return this
            return FixedSizeIcon(IconUtil.scale(delegate, null, scaleFactor))
        }

        override fun getScale(): Float = 1.0f
    }
}
