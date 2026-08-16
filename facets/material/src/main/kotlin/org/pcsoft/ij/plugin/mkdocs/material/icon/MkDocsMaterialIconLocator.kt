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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings

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
 * Every step is a handful of [VirtualFile.findFileByRelativePath] calls against known names. Nothing here
 * ever walks the project: a recursive search from the project root would touch every file of a checkout to
 * answer a question that has four plausible answers.
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
        if (siteRoot == null || !siteRoot.isValid) return null
        siteRoot.findFileByRelativePath(ICONS_INSIDE_PACKAGE)?.takeIf { it.isDirectory }?.let { return it }

        for (name in ENVIRONMENTS) {
            val environment = siteRoot.findChild(name)?.takeIf { it.isDirectory } ?: continue
            packagesOf(environment)?.findFileByRelativePath(ICONS_INSIDE_PACKAGE)
                ?.takeIf { it.isDirectory }
                ?.let { return it }
        }
        return null
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
