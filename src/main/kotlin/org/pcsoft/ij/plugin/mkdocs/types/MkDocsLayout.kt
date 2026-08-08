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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Answers where things are inside an MkDocs site.
 *
 * The rules are spread over three places — the file system, the MkDocs configuration file and the facet —
 * and several features need the same answers: the project view decorator, the file icon provider and the
 * suggestions of the *New Directory* dialog. Collecting them here keeps those three from drifting apart.
 *
 * Everything reading PSI must be called inside a read action; the pure file system functions do not need
 * one.
 */
object MkDocsLayout {

    /**
     * Returns the MkDocs configuration file directly inside [directory], or `null` if there is none.
     *
     * @param directory the directory to inspect
     */
    fun configFileOf(directory: VirtualFile): VirtualFile? {
        if (!directory.isValid || !directory.isDirectory) return null
        return MkDocsProject.CONFIG_FILE_NAMES.firstNotNullOfOrNull { directory.findChild(it) }
    }

    /**
     * Returns `true` if [directory] directly contains an MkDocs configuration file.
     *
     * @param directory the directory to inspect
     */
    fun isSiteRoot(directory: VirtualFile): Boolean = configFileOf(directory) != null

    /**
     * Returns the root of the site [file] belongs to, or `null` if it belongs to none.
     *
     * Walks upwards until a directory holding a configuration file is found, so a page nested deeply inside
     * the documentation directory still finds its site. The innermost site wins, which is what a site nested
     * inside another one means.
     *
     * @param file the file or directory to start from
     */
    fun findSiteRoot(file: VirtualFile): VirtualFile? {
        if (!file.isValid) return null
        var current: VirtualFile? = if (file.isDirectory) file else file.parent
        while (current != null && current.isValid) {
            if (isSiteRoot(current)) return current
            current = current.parent
        }
        return null
    }

    /**
     * Returns the documentation directory of the site rooted at [siteRoot], or `null` if it does not exist.
     *
     * `docs_dir` may carry several levels, which is why the value is resolved against the site root instead
     * of being compared as a bare name.
     *
     * @param project the project [siteRoot] belongs to
     * @param siteRoot the root directory of the site
     */
    fun docsDirOf(project: Project, siteRoot: VirtualFile): VirtualFile? {
        val configFile = configFileOf(siteRoot) ?: return null
        val relative = MkDocsConfig.resolveDocsDir(project, configFile)
        return VfsUtilCore.findRelativeFile(relative, siteRoot)?.takeIf { it.isDirectory }
    }

    /**
     * Returns `true` if [directory] is the documentation directory of the site it belongs to.
     *
     * @param project the project [directory] belongs to
     * @param directory the directory to inspect
     */
    fun isDocsDirectory(project: Project, directory: VirtualFile): Boolean {
        if (!directory.isValid || !directory.isDirectory) return false
        val siteRoot = findSiteRoot(directory)?.takeIf { it != directory } ?: return false
        return docsDirOf(project, siteRoot) == directory
    }

    /**
     * Returns `true` if [file] lives inside the documentation directory of a site.
     *
     * Nested directories count: everything below `docs_dir` is shipped by MkDocs, however deeply it is
     * buried. The documentation directory itself does not count — it is a directory, not a page.
     *
     * @param project the project [file] belongs to
     * @param file the file to inspect
     */
    fun isInsideDocsDir(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid) return false
        val siteRoot = findSiteRoot(file) ?: return false
        val docsDir = docsDirOf(project, siteRoot) ?: return false
        return VfsUtilCore.isAncestor(docsDir, file, true)
    }

    /**
     * Returns the name of the assets directory configured for [module].
     *
     * MkDocs has no configuration key for it, so the facet is the only place the name can come from.
     *
     * @param module the module owning the site, or `null` if there is none
     */
    fun assetsDirNameOf(module: Module?): String {
        val configured = module?.let { MkDocsFacet.getInstance(it) }?.configuration?.assetsDirName
        return configured?.takeIf { it.isNotBlank() } ?: MkDocsProject.DEFAULT_ASSETS_DIR
    }

    /**
     * Returns `true` if [directory] is the assets directory of the site it belongs to.
     *
     * @param project the project [directory] belongs to
     * @param module the module owning [directory]
     * @param directory the directory to inspect
     */
    fun isAssetsDirectory(project: Project, module: Module, directory: VirtualFile): Boolean {
        if (!directory.isValid || !directory.isDirectory) return false
        val docsDir = directory.parent ?: return false
        if (!isDocsDirectory(project, docsDir)) return false
        return directory.name == assetsDirNameOf(module)
    }

    /**
     * Returns the build output directory a site created at [path] should use.
     *
     * The build system of the surrounding module decides where generated output belongs. [path] usually does
     * not exist yet — the wizard appends the site name to the selected directory — so the search starts at
     * the innermost existing directory of the path and walks upwards from there.
     *
     * @param path the directory the site will be created in
     * @return the value for `site_dir`, falling back to the MkDocs default when no build system is found
     */
    fun detectSiteDir(path: Path): String {
        var current: Path? = path.toAbsolutePath()
        while (current != null) {
            if (current.isDirectory()) {
                val names = runCatching { current.listDirectoryEntries().map { it.name } }.getOrNull()
                if (names != null) {
                    MkDocsProject.siteDirFor(names)?.let { return it }
                }
            }
            current = current.parent
        }
        return MkDocsProject.DEFAULT_SITE_DIR
    }
}
