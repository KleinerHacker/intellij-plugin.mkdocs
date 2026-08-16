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
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * The icons the installed *Material for MkDocs* offers, per site.
 *
 * The theme addresses an icon as `set/name` — `material/check`, `fontawesome/brands/github`,
 * `octicons/repo-16` — and those names are simply the SVG files below `material/templates/.icons` of the
 * installed package. So the index is a directory listing: [MkDocsMaterialIconLocator] finds the directory,
 * this walks the sets in it and remembers the names.
 *
 * Held per site rather than per project, because two sites of one project may well have virtual environments
 * of their own, with different versions of the theme and therefore different icons in them. The snapshot of a
 * site is built on first use and kept until something invalidates it — a change below `site-packages`, a
 * changed icon path in the settings, or a facet that came or went.
 *
 * Every function must be called inside a read action; none of them may be called on the EDT while the index
 * of a site is still cold, because the first call walks the directory.
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
     * @return the icon, or `null` if the site does not offer it
     */
    fun icon(siteRoot: VirtualFile?, name: String): Icon? =
        find(siteRoot, name)?.let { MkDocsMaterialIconRenderer.render(it) }

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
        return snapshots.computeIfAbsent(siteRoot.url) { build(siteRoot) }
    }

    /**
     * Walks the icon sets of the site at [siteRoot] and collects what they hold.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    private fun build(siteRoot: VirtualFile): Snapshot {
        val root = MkDocsMaterialIconLocator.locate(project, siteRoot) ?: return Snapshot.EMPTY
        val names = mutableListOf<String>()
        VfsUtilCore.visitChildrenRecursively(
            root,
            object : VirtualFileVisitor<Any>(VirtualFileVisitor.limit(MAX_DEPTH)) {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && file.extension.equals(EXTENSION, ignoreCase = true)) {
                        val relative = VfsUtilCore.getRelativePath(file, root, '/')
                        if (relative != null) {
                            names += relative.removeSuffix(".${file.extension}")
                        }
                    }
                    return true
                }
            })
        return Snapshot(root, names.sorted())
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

        /** The extension of the icon files. */
        private const val EXTENSION = "svg"

        /**
         * How many levels below the icon directory are read; `fontawesome/brands/github` needs three.
         *
         * The depth is bounded so a directory that was mistaken for the icon sets cannot turn the walk into a
         * walk of a whole file system.
         */
        private const val MAX_DEPTH = 4

        /**
         * Returns the index of [project].
         *
         * @param project the project whose index is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsMaterialIconIndex = project.service()
    }
}
