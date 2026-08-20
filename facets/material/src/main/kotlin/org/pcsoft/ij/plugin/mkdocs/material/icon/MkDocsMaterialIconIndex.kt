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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * The icons the installed *Material for MkDocs* offers, per site.
 *
 * The theme addresses an icon as `set/name` — `material/check`, `fontawesome/brands/github`,
 * `octicons/repo-16` — and those names are simply the SVG files below `material/templates/.icons` of the
 * installed package. So the index is that listing: [MkDocsMaterialIconLocator] finds the installation and
 * [MkDocsMaterialInstallation] reads the names out of the `RECORD` the installation itself wrote.
 *
 * Held per site rather than per project, because two sites of one project may well have virtual environments
 * of their own, with different versions of the theme and therefore different icons in them. The snapshot of a
 * site is built on first use and kept until something invalidates it — a change below `site-packages`, a
 * changed icon path in the settings, or a facet that came or went.
 *
 * Every function must be called inside a read action; none of them may be called on the EDT while the index
 * of a site is still cold, because the first call reads the installation.
 */
@Service(Service.Level.PROJECT)
class MkDocsMaterialIconIndex(private val project: Project) {

    /** The snapshot per site root, keyed by the URL of the directory holding `mkdocs.yml`. */
    private val snapshots = ConcurrentHashMap<String, Snapshot>()

    /**
     * Returns the names of the icons available to the site at [siteRoot], sorted.
     *
     * Empty whenever the theme is not installed where it can be found — which is the normal state of a
     * checkout whose virtual environment has not been created yet, and the reason nothing built on this may
     * treat an empty result as an error.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    fun names(siteRoot: VirtualFile?): List<String> = snapshotOf(siteRoot).names

    /**
     * Returns the SVG file of the icon [name] of the site at [siteRoot], or `null` if there is none.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param name the name of the icon, as the theme addresses it, for example `material/check`
     */
    fun find(siteRoot: VirtualFile?, name: String): VirtualFile? {
        val root = snapshotOf(siteRoot).root ?: return null
        if (name.isBlank() || ".." in name.split('/')) return null
        return root.findFileByRelativePath("$name.svg")?.takeIf { it.isValid && !it.isDirectory }
    }

    /**
     * Returns the icon [name] of the site at [siteRoot], rendered for the editor.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param name the name of the icon, as the theme addresses it
     * @param size the edge length in pixels the icon is to be rendered at
     * @return the icon, or `null` if the site does not offer it
     */
    @JvmOverloads
    fun icon(
        siteRoot: VirtualFile?,
        name: String,
        size: Int = MkDocsMaterialIconRenderer.DEFAULT_SIZE,
    ): Icon? = find(siteRoot, name)?.let { MkDocsMaterialIconRenderer.render(it, size) }

    /**
     * Throws away everything the index remembers, so the next question walks the directories again.
     *
     * Called whenever the installation can have changed: a write below `site-packages`, a changed icon path
     * in the settings, or a Material facet that came or went.
     */
    fun invalidate() {
        snapshots.clear()
        MkDocsMaterialIconRenderer.invalidate()
    }

    /**
     * Returns the snapshot of the site at [siteRoot], building it on first use.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    private fun snapshotOf(siteRoot: VirtualFile?): Snapshot {
        if (siteRoot == null || !siteRoot.isValid) return Snapshot.EMPTY
        snapshots[siteRoot.url]?.let { return it }
        val snapshot = build()
        // An empty answer given while pip has not answered yet is not a finding but a missing answer, and
        // keeping it would leave the icons away until something invalidates the index. What the warm-up
        // fetches invalidates it, so only a decided answer is remembered here.
        if (snapshot.root != null || service<MkDocsPipService>().isKnown(MkDocsMaterialIconLocator.DISTRIBUTION)) {
            snapshots[siteRoot.url] = snapshot
        }
        return snapshot
    }

    /**
     * Reads what the installation the settings point at brought along.
     */
    private fun build(): Snapshot {
        val location = MkDocsMaterialIconLocator.locateInstallation(project) ?: return Snapshot.EMPTY
        val root = MkDocsMaterialIconLocator.locate(project) ?: return Snapshot.EMPTY
        return Snapshot(root, MkDocsMaterialInstallation.iconNames(location))
    }

    /**
     * What the index remembers about one site.
     *
     * @property root the directory holding the icon sets, `null` if the theme was not found
     * @property names the names of the icons below [root], sorted
     */
    private data class Snapshot(val root: VirtualFile?, val names: List<String>) {

        companion object {

            /** The snapshot of a site whose theme could not be found. */
            val EMPTY: Snapshot = Snapshot(null, emptyList())
        }
    }

    companion object {

        /**
         * Returns the index of [project].
         *
         * @param project the project whose index is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsMaterialIconIndex = project.service()
    }
}
