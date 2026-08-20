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
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallation
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationCache
import javax.swing.Icon

/**
 * The icons the installed *Material for MkDocs* offers to a site.
 *
 * The theme addresses an icon as `set/name` — `material/check`, `fontawesome/brands/github`,
 * `octicons/repo-16` — and those names are simply the SVG files below `material/templates/.icons` of the
 * installed package. So the index is that listing: [MkDocsMaterialIconLocator] finds the installation of the
 * site and [org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallation] reads the names out of the `RECORD` the installation itself wrote.
 *
 * Nothing is remembered here. What an installation costs to read is kept by
 * [org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationCache], keyed by the installation rather than by the site, which is the key the
 * answers actually depend on: two sites pointed at the same environment share them, and two environments
 * keep them apart on their own. This service is the way in — it turns a site into the installation behind it
 * and asks.
 *
 * Every function must be called inside a read action; none of them may be called on the EDT while the
 * installation has not been read yet, because the first call reads it.
 */
@Service(Service.Level.PROJECT)
class MkDocsMaterialIconIndex(private val project: Project) {

    /**
     * Returns the names of the icons available to the site at [siteRoot], sorted.
     *
     * Empty whenever the theme is not installed where it can be found — which is the normal state of a
     * checkout whose virtual environment has not been created yet, and the reason nothing built on this may
     * treat an empty result as an error. An empty answer is never remembered: without an installation there
     * is nothing to key it on, so the question is simply asked again once pip has answered.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    fun names(siteRoot: VirtualFile?): List<String> {
        if (siteRoot == null || !siteRoot.isValid) return emptyList()
        val location = MkDocsMaterialIconLocator.locateInstallation(project) ?: return emptyList()
        return MkDocsMaterialInstallation.iconNames(location)
    }

    /**
     * Returns the SVG file of the icon [name] of the site at [siteRoot], or `null` if there is none.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     * @param name the name of the icon, as the theme addresses it, for example `material/check`
     */
    fun find(siteRoot: VirtualFile?, name: String): VirtualFile? {
        if (siteRoot == null || !siteRoot.isValid) return null
        if (name.isBlank() || ".." in name.split('/')) return null
        val root = MkDocsMaterialIconLocator.locate(project) ?: return null
        return service<MkDocsMaterialInstallationCache>().fileOf(root, name) {
            root.findFileByRelativePath("$name.svg")?.takeIf { it.isValid && !it.isDirectory }
        }
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
     * Throws away everything that was read out of an installation, so the next question reads it again.
     *
     * Called whenever the installation can have changed: a write below `site-packages`, a changed icon path
     * in the settings, or a Material facet that came or went. The one way to say so — what would otherwise
     * have to be a caller emptying several caches in the right order, which is how a stale one survives.
     */
    fun invalidate() {
        service<MkDocsMaterialInstallationCache>().invalidate()
    }
}
