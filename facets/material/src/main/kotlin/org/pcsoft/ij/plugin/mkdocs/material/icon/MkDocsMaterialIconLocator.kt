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

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsSiteFiles

/**
 * Finds the directory holding the icon sets of the *Material for MkDocs* theme.
 *
 * The icons the theme offers are not a list this plugin could carry: which ones exist depends on the version
 * of `mkdocs-material` that is installed, and the sets are simply the SVG files shipped inside the package. So
 * what has to be found is the directory `material/templates/.icons` of the installed package, which is looked
 * for in this order:
 *
 * 1. the path configured in the settings, if there is one — the answer for every setup this cannot guess;
 * 2. `material/templates/.icons` directly below the site root, which is where an override directory holding
 *    a copy of the sets would be;
 * 3. the virtual environments a project normally keeps next to its sources: `.venv`, `venv`, `env` and
 *    `.virtualenv`, each in the Windows layout `Lib/site-packages` and in the POSIX layout
 *    `lib/python3.x/site-packages`.
 *
 * Step 2 and step 3 are not asked of the site root alone but of every directory from it up to the root of the
 * project. A site commonly lives in a `docs/` directory while the environment it is built with lies next to
 * the sources, one or two levels above it — asking the site root alone found nothing in exactly the layout
 * this repository itself uses.
 *
 * Every step is a handful of [VirtualFile.findFileByRelativePath] calls against known names. Nothing here
 * ever walks the project downwards: a recursive search from the project root would touch every file of a
 * checkout to answer a question that has a handful of plausible answers.
 */
object MkDocsMaterialIconLocator {

    /** The path of the icon sets inside the installed package. */
    private const val ICONS_INSIDE_PACKAGE = "material/templates/.icons"

    /** The directory names of the virtual environments that are searched. */
    private val ENVIRONMENTS = listOf(".venv", "venv", "env", ".virtualenv")

    /** The path of the packages inside a virtual environment on Windows. */
    private const val SITE_PACKAGES_WINDOWS = "Lib/site-packages"

    /** The directory holding the packages inside a virtual environment everywhere else. */
    private const val LIB = "lib"

    /** The directory the packages of an interpreter lie in, below its `lib` directory. */
    private const val SITE_PACKAGES = "site-packages"

    /** The prefix of the interpreter directories below `lib` of a virtual environment. */
    private const val PYTHON_PREFIX = "python"

    /** How many levels the walk upwards climbs before it gives up. */
    private const val MAX_ANCESTORS = 8

    /**
     * Returns the directory holding the icon sets for the site at [siteRoot], or `null` if there is none.
     *
     * Must be called inside a read action.
     *
     * @param project the project [siteRoot] belongs to
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    fun locate(project: Project, siteRoot: VirtualFile?): VirtualFile? {
        configured(project)?.let { return it }
        return detect(project, siteRoot)
    }

    /**
     * Returns the directory holding the icon sets for the site at [siteRoot] without asking the settings.
     *
     * What the settings page shows as the installation it found: the configured path is the answer *given* by
     * the user, and a page offering it back as its own discovery would say nothing.
     *
     * Must be called inside a read action.
     *
     * @param project the project [siteRoot] belongs to
     * @param siteRoot the directory holding `mkdocs.yml`, or `null` to search from the project root alone
     */
    fun detect(project: Project, siteRoot: VirtualFile?): VirtualFile? {
        val roots = rootsOf(project)
        for (directory in ancestors(siteRoot, roots)) {
            iconsIn(directory)?.let { return it }
        }
        return null
    }

    /**
     * Returns the directories the walk upwards stops at: the content roots of the project and its own root.
     *
     * @param project the project being searched
     */
    private fun rootsOf(project: Project): Set<VirtualFile> {
        val roots = mutableSetOf<VirtualFile>()
        project.basePath?.let { path ->
            LocalFileSystem.getInstance().findFileByPath(path)?.takeIf { it.isValid }?.let { roots += it }
        }
        for (module in ModuleManager.getInstance(project).modules) {
            roots += ModuleRootManager.getInstance(module).contentRoots.filter { it.isValid && it.isDirectory }
        }
        return roots
    }

    /**
     * Returns the icon sets installed anywhere in [project], or `null` if there are none.
     *
     * What the settings page asks, which is not opened on a site: every module holding an MkDocs site is
     * asked for its own installation, and the project root answers for a project whose sites carry none.
     *
     * Must be called inside a read action.
     *
     * @param project the project to search
     */
    fun detectInProject(project: Project): VirtualFile? {
        for (module in ModuleManager.getInstance(project).modules) {
            val siteRoot = MkDocsSiteFiles.findConfigFile(module)?.parent ?: continue
            detect(project, siteRoot)?.let { return it }
        }
        return detect(project, null)
    }

    /**
     * Returns the icon sets installed below [directory], or `null` if none are.
     *
     * @param directory the directory to look in, a site root or one of its ancestors
     */
    private fun iconsIn(directory: VirtualFile): VirtualFile? {
        directory.findFileByRelativePath(ICONS_INSIDE_PACKAGE)?.takeIf { it.isDirectory }?.let { return it }

        for (name in ENVIRONMENTS) {
            val environment = directory.findChild(name)?.takeIf { it.isDirectory } ?: continue
            packagesOf(environment)?.findFileByRelativePath(ICONS_INSIDE_PACKAGE)
                ?.takeIf { it.isDirectory }
                ?.let { return it }
        }
        return null
    }

    /**
     * Returns [siteRoot] and its ancestors up to [projectRoot], nearest first.
     *
     * The walk stops at a root of the project — its own directory or a content root of one of its modules.
     * Above those lies the file system of whoever runs the IDE, and an environment found there belongs to
     * something else. Without a site root the roots alone are asked, which is what the settings page needs
     * before any site is open.
     *
     * @param siteRoot the directory holding `mkdocs.yml`, or `null`
     * @param roots the directories the walk stops at
     */
    private fun ancestors(siteRoot: VirtualFile?, roots: Set<VirtualFile>): List<VirtualFile> {
        val start = siteRoot?.takeIf { it.isValid } ?: return roots.toList()
        val directories = mutableListOf<VirtualFile>()
        var current: VirtualFile? = start
        var steps = 0
        while (current != null && steps < MAX_ANCESTORS) {
            directories += current
            if (current in roots) break
            current = current.parent
            steps++
        }
        directories += roots.filter { it !in directories }
        return directories
    }

    /**
     * Returns the directory configured in the settings of [project], or `null` if there is none.
     *
     * A configured path that does not exist is treated as if it were not configured at all rather than as an
     * instruction to find nothing: an environment that was moved should degrade into the search, not into
     * silence.
     *
     * @param project the project whose settings are read
     */
    private fun configured(project: Project): VirtualFile? {
        val path = MkDocsMaterialIconSettings.getInstance(project).iconPath.trim()
        if (path.isEmpty()) return null
        return LocalFileSystem.getInstance().findFileByPath(path)?.takeIf { it.isValid && it.isDirectory }
    }

    /**
     * Returns the `site-packages` directory of the virtual environment [environment], or `null` if it has
     * none.
     *
     * The two layouts differ in more than their spelling: on Windows the packages lie at a fixed path, while
     * everywhere else they lie below a directory named after the version of the interpreter, which is not
     * known in advance. Only the children of `lib` are listed for that, never anything deeper.
     *
     * @param environment the directory of the virtual environment
     */
    private fun packagesOf(environment: VirtualFile): VirtualFile? {
        environment.findFileByRelativePath(SITE_PACKAGES_WINDOWS)
            ?.takeIf { it.isDirectory }
            ?.let { return it }

        val lib = environment.findChild(LIB)?.takeIf { it.isDirectory } ?: return null
        return lib.children
            .filter { it.isDirectory && it.name.startsWith(PYTHON_PREFIX) }
            .sortedByDescending { it.name }
            .firstNotNullOfOrNull { it.findChild(SITE_PACKAGES)?.takeIf { packages -> packages.isDirectory } }
    }
}
