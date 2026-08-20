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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsIconLoader
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationCache
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.awt.image.RGBImageFilter
import javax.swing.Icon

/**
 * Turns an SVG file of the *Material for MkDocs* icon sets into an icon the IDE can paint.
 *
 * The one place in the plugin that loads an image from a file the user's environment supplied, and it is
 * kept to itself for that reason: the file comes from an installed package, not from this plugin, so it may
 * be anything — an SVG a loader chokes on, a file that vanished between the listing and the read, a set of a
 * newer version using something the platform does not support. None of that may reach the completion popup
 * as an exception, so every failure ends up as [MkDocsMaterialIcons.Feature] and a line in the log.
 *
 * Nothing is read from disk here until the drawing is actually painted. The completion popup renders *every*
 * matching entry, not only the visible ones — that is how it measures its own width — so a set of several
 * thousand icons would otherwise be loaded from scratch on every keystroke. What leaves this object is a
 * handle that knows its size and loads its file on the first paint, which is the few entries the user sees.
 *
 * The handles are kept by [org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationCache], keyed by file *and* size, because the same
 * drawing is handed to a popup at 16 pixels and to an inlay hint at 12. They live there rather than here,
 * next to everything else that follows from an installation and dies with it — this object holds no state of
 * its own.
 */
object MkDocsMaterialIconRenderer {

    private val LOG = logger<MkDocsMaterialIconRenderer>()

    /** The size a place asking for no particular one gets: the size of a list entry and of a popup. */
    const val DEFAULT_SIZE: Int = 16

    /**
     * Returns [file] as an icon.
     *
     * Returns at once and reads nothing: the file behind the icon is opened on the first paint, and only
     * then. Never throws and never returns `null` — an icon that cannot be loaded is painted as the Material
     * icon of the plugin, which says *this is an icon of the theme* without claiming to be the icon itself.
     *
     * The icon carries a fixed size, the same way every icon of the plugin does: an inlay hint scales what
     * it is handed back up to the canvas of the drawing, and the sets of the theme are drawn on canvases of
     * their own — 24 units for the Material set, 16 for the Octicons.
     *
     * @param file the SVG file of the icon
     * @param size the edge length in pixels the icon is to be rendered at
     */
    @JvmOverloads
    fun render(file: VirtualFile, size: Int = DEFAULT_SIZE): Icon {
        if (!file.isValid) return MkDocsMaterialIcons.Feature
        return service<MkDocsMaterialInstallationCache>().iconOf(file, size) { DeferredIcon(file, size) }
    }

    /**
     * Reads the SVG file [file] and hands it back at [size] pixels.
     *
     * @param file the SVG file of the icon
     * @param size the edge length in pixels the icon is to be rendered at
     */
    private fun load(file: VirtualFile, size: Int): Icon {
        if (!file.isValid) return MkDocsMaterialIcons.Feature
        return try {
            IconLoader.findIcon(file.toNioPath().toUri().toURL(), true)
                ?.let { MkDocsIconLoader.fixSize(ForegroundIcon(it), size) }
                ?: MkDocsMaterialIcons.Feature
        } catch (throwable: Throwable) {
            // Deliberately Throwable: what is read here is a file of a foreign package, and an image loader
            // failing on it must never take the popup that asked for it down with it.
            LOG.debug("Cannot render the icon ${file.url}", throwable)
            MkDocsMaterialIcons.Feature
        }
    }

    /**
     * An icon that stands for an SVG file and opens it on the first paint.
     *
     * Its size is known without the file, which is what lets a completion popup measure a set of several
     * thousand entries without touching the disk for any of them.
     *
     * @property file the SVG file of the icon
     * @property size the edge length in pixels the icon is painted at
     */
    private class DeferredIcon(private val file: VirtualFile, private val size: Int) : Icon {

        /** The loaded drawing, read on the first paint. */
        private val loaded: Icon by lazy(LazyThreadSafetyMode.PUBLICATION) { load(file, size) }

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            loaded.paintIcon(component, graphics, x, y)
        }

        override fun getIconWidth(): Int = size

        override fun getIconHeight(): Int = size
    }

    /**
     * An icon of the sets, drawn in the colour the IDE writes its text in.
     *
     * The drawings of the theme are monochrome glyphs and carry no colour of their own, which makes them
     * black — the colour an SVG falls back to. Black is right in a light IDE and all but invisible in a dark
     * one, so every pixel is given the foreground colour of the current theme while its transparency is
     * kept, which is what leaves the shape of the glyph intact.
     *
     * The colour is read while painting rather than while loading: a theme switched at runtime then arrives
     * at the next repaint, and nothing has to be thrown away for it.
     *
     * @property source the icon as the SVG was loaded
     */
    private class ForegroundIcon(private val source: Icon) : Icon {

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            if (source.iconWidth <= 0 || source.iconHeight <= 0) return
            val image = ImageUtil.createImage(
                graphics,
                source.iconWidth,
                source.iconHeight,
                BufferedImage.TYPE_INT_ARGB,
            )
            val imageGraphics = image.createGraphics()
            try {
                source.paintIcon(component, imageGraphics, 0, 0)
            } finally {
                imageGraphics.dispose()
            }
            val foreground = UIUtil.getLabelForeground().rgb and RGB
            val recoloured = ImageUtil.filter(image, object : RGBImageFilter() {

                init {
                    canFilterIndexColorModel = true
                }

                override fun filterRGB(x: Int, y: Int, rgb: Int): Int = (rgb and ALPHA) or foreground
            })
            UIUtil.drawImage(graphics, recoloured, x, y, component)
        }

        override fun getIconWidth(): Int = source.iconWidth

        override fun getIconHeight(): Int = source.iconHeight
    }

    /** The bits of a pixel holding its transparency. */
    private const val ALPHA = 0xFF000000.toInt()

    /** The bits of a pixel holding its colour. */
    private const val RGB = 0x00FFFFFF
}
