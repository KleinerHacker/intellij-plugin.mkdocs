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

package org.pcsoft.ij.plugin.mkdocs.material.markdown

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * The icon shorthands *Material for MkDocs* renders inside a page, `:material-check:` and its like.
 *
 * Two features work on them and must agree on what one is: the completion offering them, and the inlay hint
 * painting the drawing in front of one already written.
 *
 * The shorthand is the name of the icon with its slashes turned into dashes, and that step cannot be undone
 * on the name alone — `material-weather-sunny` could be any of a dozen splits. So a shorthand is resolved
 * against the names the installed theme actually offers, never by guessing where the slashes were.
 */
object MkDocsMaterialShorthands {

    /** How far the walk upwards climbs before it gives up looking for a configuration file. */
    private const val MAX_ANCESTORS = 16

    /** A shorthand as it stands in a page: the colons, and between them letters, digits, dashes, underscores. */
    val PATTERN: Regex = Regex(":([A-Za-z0-9][A-Za-z0-9_-]*):")

    /**
     * Returns the shorthand of the icon [name].
     *
     * @param name the name of the icon, as the theme addresses it, for example `material/check`
     */
    fun shorthandOf(name: String): String = ":${name.replace('/', '-')}:"

    /**
     * Returns the name of the icon [shorthand] stands for, or `null` if [names] holds none.
     *
     * @param shorthand the shorthand as it stands in the page, colons included
     * @param names the names the installed theme offers
     */
    fun nameOf(shorthand: String, names: Collection<String>): String? {
        val bare = shorthand.trim(':')
        if (bare.isEmpty()) return null
        return names.firstOrNull { it.replace('/', '-') == bare }
    }

    /**
     * Returns the root of the site [file] is a page of, or `null` if it is a page of none.
     *
     * The walk goes upwards from the file and stops at the first directory holding a configuration file: a
     * page belongs to the nearest site, not to the first one somewhere above it. It gives up at the content
     * root of the module — above that lies whatever else the IDE has open, which the page is not part of.
     *
     * The caller must hold a read action.
     *
     * @param project the project [file] belongs to
     * @param file the Markdown file in question
     */
    fun siteRootOf(project: Project, file: VirtualFile): VirtualFile? {
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val roots = ModuleRootManager.getInstance(module).contentRoots.toSet()
        var directory = file.parent
        var steps = 0
        while (directory != null && steps < MAX_ANCESTORS) {
            if (directory.children.any { !it.isDirectory && MkDocsProject.isConfigFile(it.name) }) {
                return directory
            }
            if (directory in roots) return null
            directory = directory.parent
            steps++
        }
        return null
    }
}
