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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
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
 * Results are cached: a completion popup asks for the icon of every visible entry on every keystroke, and
 * the sets hold thousands of files. The cache is bounded and thrown away whenever the index is invalidated.
 */
object MkDocsMaterialIconRenderer {

    private val LOG = logger<MkDocsMaterialIconRenderer>()

    /** How many rendered icons are kept before the oldest ones are dropped. */
    private const val CACHE_LIMIT = 500

    /** The rendered icons, keyed by the URL of the file they were loaded from. */
    private val cache = object : LinkedHashMap<String, Icon>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Icon>?): Boolean =
            size > CACHE_LIMIT
    }

    /**
     * Returns [file] as an icon.
     *
     * Never throws and never returns `null`: an icon that cannot be loaded is shown as the Material icon of
     * the plugin, which says *this is an icon of the theme* without claiming to be the icon itself.
     *
     * @param file the SVG file of the icon
     */
    fun render(file: VirtualFile): Icon {
        if (!file.isValid) return MkDocsMaterialIcons.Feature
        synchronized(cache) { cache[file.url] }?.let { return it }

        val icon = try {
            IconLoader.findIcon(file.toNioPath().toUri().toURL(), true) ?: MkDocsMaterialIcons.Feature
        } catch (throwable: Throwable) {
            // Deliberately Throwable: what is read here is a file of a foreign package, and an image loader
            // failing on it must never take the popup that asked for it down with it.
            LOG.debug("Cannot render the icon ${file.url}", throwable)
            MkDocsMaterialIcons.Feature
        }
        synchronized(cache) { cache[file.url] = icon }
        return icon
    }

    /**
     * Throws away every rendered icon.
     *
     * Called from [MkDocsMaterialIconIndex.invalidate]: an installation that changed can hold a different
     * drawing under a name that was already rendered once.
     */
    fun invalidate() {
        synchronized(cache) { cache.clear() }
    }
}
