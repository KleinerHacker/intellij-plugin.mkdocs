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

package org.pcsoft.ij.plugin.mkdocs.material

import org.pcsoft.ij.plugin.mkdocs.MkDocsIconLoader
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import javax.swing.Icon

/**
 * Registry of every icon the Angular Material feature ships.
 *
 * Kept apart from [MkDocsIcons] because the feature owns them: what the theme looks like is none of the
 * plugin's business, and a plugin without this feature ships none of these files.
 *
 * The files follow the same rules as the plugin's own: one SVG per motif on a 48x48 canvas, a `_dark`
 * variant next to it, and the size a place needs set on loading — see [MkDocsIconLoader.load].
 */
object MkDocsMaterialIcons {

    /**
     * The Angular Material motif, for places rendering it on its own.
     *
     * The MkDocs shield of the other icons carrying the Material glyph — a circle filled in its lower half.
     */
    @JvmField
    val Badge: Icon = load("mkdocs-angular-material.svg", 16)

    /**
     * The Angular Material motif at the size an inlay hint renders it at.
     *
     * An inlay sits inside a line of the editor and has to stay below the line height, which the 16 pixels of
     * [Badge] already exceed at the default font size.
     *
     * The place that made the fixing in [MkDocsIconLoader.load] necessary: everything the hints API offers
     * for an icon — `smallScaledIcon`, `ScaleAwarePresentationFactory` — scales a `ScalableIcon` from the size
     * it was *loaded from*, which is the 48 unit canvas of the file, and hands out the full 48 pixels no
     * matter what was set on loading.
     */
    @JvmField
    val Inlay: Icon = load("mkdocs-angular-material.svg", 12)

    /**
     * The Material glyph as overlaid on the MkDocs logo, and nothing else.
     *
     * Drawn as its own shape rather than as a shrunk [Badge]: at badge size the ring of the full drawing
     * closes into a blot, so the carrier disc takes the ring's place and only the filled lower half remains
     * as the glyph. The disc is filled in a strong blue with a white glyph and a white outer rim, against the
     * house colours of the other overlays — those sit on the folder icon of the project view, while this one
     * sits on the near white body of the MkDocs logo, where a light fill would disappear.
     */
    @JvmField
    val Overlay: Icon = load("mkdocs-angular-material-overlay.svg", 8)

    /**
     * Icon of the feature itself: the MkDocs logo badged with the Material glyph.
     *
     * Composed rather than drawn, and deliberately so. The facet used to hang below the MkDocs facet in the
     * Project Structure tree, which said what belongs to what; nested facets are on their way out of the
     * platform (IDEA-309067), and the flat list says nothing. Sharing the MkDocs logo and adding a badge to
     * it puts that statement back where the tree used to make it.
     */
    @JvmField
    val Feature: Icon = MkDocsIconLoader.withBadge(MkDocsIcons.MkDocs, Overlay)

    /**
     * Loads the icon [fileName] from the `icons` resource folder and brings it to [size] pixels.
     *
     * @param fileName name of the SVG below `/icons`, without the `_dark` suffix of the dark variant
     * @param size the edge length in pixels the icon is to be rendered at
     */
    private fun load(fileName: String, size: Int): Icon =
        MkDocsIconLoader.load("/icons/$fileName", size, MkDocsMaterialIcons::class.java)
}
