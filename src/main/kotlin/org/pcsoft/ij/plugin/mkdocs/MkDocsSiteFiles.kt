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

package org.pcsoft.ij.plugin.mkdocs

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

/**
 * Finds the files of an MkDocs site inside a module.
 *
 * The platform-aware counterpart of [MkDocsProject], which answers the same kind of question on file names
 * alone. Everything needing a `VirtualFile` or a module belongs here, so [MkDocsProject] can stay testable
 * without booting the IDE.
 *
 * Shared rather than kept next to one caller: the facet UI, the tool window and every optional feature ask
 * the same question — which configuration file does this module stand for — and they must not answer it
 * differently.
 */
object MkDocsSiteFiles {

    /**
     * Locates the MkDocs configuration file of [module].
     *
     * The content roots of [module] are searched recursively, because the site root is not necessarily the
     * module root — a site commonly lives in a `docs/` subdirectory of an otherwise unrelated module.
     * [configFilePath] is stored relative to the *site* root and therefore usually holds nothing but the bare
     * file name; it is used to prefer the configured spelling, and any MkDocs configuration file counts if
     * that spelling is not found. The directories skipped are [MkDocsProject.IGNORED_DIRECTORIES], the same
     * ones the detection skips, so a configuration file inside a build output does not make an empty facet
     * look valid.
     *
     * @param module the module the site belongs to
     * @param configFilePath the configured path, relative to the site root, possibly empty
     * @return the configuration file, or `null` if the module has no MkDocs site
     */
    @JvmStatic
    fun findConfigFile(module: Module, configFilePath: String): VirtualFile? {
        if (module.isDisposed) return null
        val preferredName = configFilePath.substringAfterLast('/').substringAfterLast('\\')
        return runReadActionBlocking {
            if (module.isDisposed) return@runReadActionBlocking null
            var fallback: VirtualFile? = null
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                if (!root.isValid || !root.isDirectory) continue
                var preferred: VirtualFile? = null
                VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (preferred != null) return false
                        if (file.isDirectory) {
                            return file == root || file.name !in MkDocsProject.IGNORED_DIRECTORIES
                        }
                        if (!MkDocsProject.isConfigFile(file.name)) return true
                        if (preferredName.isNotEmpty() && file.name.equals(preferredName, ignoreCase = true)) {
                            preferred = file
                        } else if (fallback == null) {
                            fallback = file
                        }
                        return true
                    }
                })
                preferred?.let { return@runReadActionBlocking it }
            }
            fallback
        }
    }
}
