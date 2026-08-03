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

package org.pcsoft.ij.plugin.mkdocs.services

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetConfiguration
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite

/**
 * Keeps the IDE's module model in sync with the MkDocs sites found in the project.
 *
 * The rule implemented here: the directory **above** an `mkdocs.yml` / `mkdocs.yaml` is an MkDocs module.
 * It is represented by an [MkDocsFacet] on the IntelliJ module owning that directory; a module of its own
 * is created only when the directory belongs to no module at all. The configuration file always wins — a
 * facet is created, updated or dropped to match what is on disk.
 *
 * @param project the project this service works on
 */
@Service(Service.Level.PROJECT)
class MkDocsModuleService(private val project: Project) {

    companion object {

        /** Time window in which incoming sync requests are merged into a single scan. */
        private const val SYNC_MERGE_MILLIS = 300

        /** Identity of the merged sync task inside the update queue. */
        private const val SYNC_TASK_ID = "mkdocs.module.sync"

        /**
         * Directory names never descended into while searching for MkDocs configuration files.
         *
         * These are build outputs, dependency caches and VCS metadata — a `mkdocs.yml` inside one of them is
         * an artefact, not a source site.
         */
        val IGNORED_DIRECTORIES: Set<String> = setOf(
            ".git", ".hg", ".svn", ".idea", ".gradle", ".tox", ".venv", "venv",
            "node_modules", "build", "out", "target", "dist", "__pycache__", "site",
        )

        /**
         * Returns the service instance for [project].
         *
         * @param project the project whose service is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsModuleService = project.service()
    }

    /**
     * Scans the project and applies the result to the module model.
     *
     * Safe to call from any thread: the scan runs in a read action, the module changes in a write action on
     * the EDT. Calling it repeatedly is cheap and idempotent — unchanged modules are left untouched.
     */
    fun sync() {
        if (project.isDisposed) return
        val sites = runReadActionBlocking { if (project.isDisposed) emptyList() else findSites() }
        WriteAction.runAndWait<RuntimeException> {
            if (!project.isDisposed) apply(sites)
        }
    }

    private val syncQueue = MergingUpdateQueue(
        "MkDocsModuleSync",
        SYNC_MERGE_MILLIS,
        true,
        MergingUpdateQueue.ANY_COMPONENT,
        project,
    )

    /**
     * Requests a [sync] without blocking the caller.
     *
     * Bursts of VFS events — a branch switch, a bulk refresh — are merged into a single scan, which then
     * runs on a pooled thread so neither the EDT nor the VFS event dispatch is held up.
     */
    fun scheduleSync() {
        if (project.isDisposed) return
        syncQueue.queue(Update.create(SYNC_TASK_ID) {
            ApplicationManager.getApplication().executeOnPooledThread { sync() }
        })
    }

    /**
     * Returns all modules currently marked as MkDocs modules.
     *
     * Reflects the state of the last [sync]; it does not touch the file system.
     */
    fun getMkDocsModules(): List<Module> =
        ModuleManager.getInstance(project).modules.filter { MkDocsFacet.getInstance(it) != null }

    /**
     * Searches the project for MkDocs configuration files.
     *
     * The search starts at the project directory and at every content root, skipping [IGNORED_DIRECTORIES].
     * A directory containing both `mkdocs.yml` and `mkdocs.yaml` yields a single site — MkDocs itself would
     * only ever load one of them.
     *
     * Must be called inside a read action.
     *
     * @return one [MkDocsSite] per detected site root, ordered by path
     */
    fun findSites(): List<MkDocsSite> {
        val sites = LinkedHashMap<String, MkDocsSite>()
        for (searchRoot in searchRoots()) {
            VfsUtilCore.visitChildrenRecursively(searchRoot, object : VirtualFileVisitor<Any?>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        return file == searchRoot || file.name !in IGNORED_DIRECTORIES
                    }
                    if (!MkDocsProject.isConfigFile(file.name)) return true
                    val siteRoot = file.parent ?: return true
                    sites.getOrPut(siteRoot.path) {
                        MkDocsSite(
                            root = siteRoot,
                            configFile = file,
                            siteName = MkDocsConfig.resolveSiteName(project, file),
                        )
                    }
                    return true
                }
            })
        }
        return sites.values.sortedBy { it.root.path }
    }

    /**
     * Collects the directories the scan starts from: the project directory plus every content root that is
     * not already covered by one of them.
     *
     * The project directory is included on purpose — a documentation folder belonging to no module would
     * otherwise never be found, and that is exactly the case in which a module has to be created.
     */
    private fun searchRoots(): List<VirtualFile> {
        val candidates = buildList {
            project.guessProjectDir()?.let(::add)
            addAll(ProjectRootManager.getInstance(project).contentRoots)
        }.filter { it.isValid && it.isDirectory }.distinctBy { it.path }

        return candidates.filter { candidate ->
            candidates.none { other -> other != candidate && VfsUtilCore.isAncestor(other, candidate, true) }
        }
    }

    /**
     * Brings the module model in line with [sites].
     *
     * Must be called inside a write action on the EDT.
     */
    private fun apply(sites: List<MkDocsSite>) {
        val handled = HashSet<Module>()
        for (site in sites) {
            val module = ModuleUtilCore.findModuleForFile(site.root, project)
            if (module != null) {
                if (handled.add(module)) {
                    updateFacet(module, site, ownsModule = false)
                } else {
                    thisLogger().info("Module '${module.name}' already holds an MkDocs site, ignoring ${site.root.path}")
                }
            } else {
                val created = createModule(site) ?: continue
                handled.add(created)
                updateFacet(created, site, ownsModule = true)
            }
        }
        for (module in ModuleManager.getInstance(project).modules.toList()) {
            if (module in handled) continue
            dropFacet(module)
        }
    }

    /**
     * Creates a persistent module whose single content root is the site root.
     *
     * Used only when the site root belongs to no module; returns `null` if the root has no path on the local
     * file system.
     */
    private fun createModule(site: MkDocsSite): Module? {
        val moduleManager = ModuleManager.getInstance(project)
        val directory = runCatching { site.root.toNioPath() }.getOrNull() ?: return null
        val name = uniqueModuleName(site.siteName)
        // The generic default type: MkDocs is language agnostic, and a Java module type would not even be
        // available in the non-Java IDEs of the supported matrix.
        val moduleTypeId = ModuleTypeManager.getInstance().defaultModuleType.id
        val module = moduleManager.newModule(directory.resolve("$name.iml"), moduleTypeId)
        ModuleRootModificationUtil.addContentRoot(module, site.root)
        return module
    }

    /**
     * Turns [preferred] into a module name that is not taken yet by appending `~2`, `~3`, ….
     *
     * @param preferred the desired name, usually the `site_name` of the site
     */
    private fun uniqueModuleName(preferred: String): String {
        val moduleManager = ModuleManager.getInstance(project)
        val sanitized = preferred.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "mkdocs" }
        if (moduleManager.findModuleByName(sanitized) == null) return sanitized
        var index = 2
        while (moduleManager.findModuleByName("$sanitized~$index") != null) index++
        return "$sanitized~$index"
    }

    /**
     * Adds the MkDocs facet to [module] or refreshes the values of an existing one.
     *
     * @param module the module representing the site
     * @param site the detected site
     * @param ownsModule `true` if [module] was created by this service for [site]
     */
    private fun updateFacet(module: Module, site: MkDocsSite, ownsModule: Boolean) {
        val configFilePath = VfsUtilCore.getRelativePath(site.configFile, site.root) ?: site.configFile.name
        val facetManager = FacetManager.getInstance(module)

        val existing = MkDocsFacet.getInstance(module)
        if (existing != null) {
            val configuration = existing.configuration
            if (configuration.siteName == site.siteName && configuration.configFilePath == configFilePath) return
            configuration.siteName = site.siteName
            configuration.configFilePath = configFilePath
            facetManager.facetConfigurationChanged(existing)
            return
        }

        val facetType = MkDocsFacet.facetType
        val configuration = MkDocsFacetConfiguration().apply {
            siteName = site.siteName
            this.configFilePath = configFilePath
            this.ownsModule = ownsModule
        }
        val facet = facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
        val model = facetManager.createModifiableModel()
        model.addFacet(facet)
        model.commit()
    }

    /**
     * Drops the MkDocs facet from [module] once its configuration file is gone.
     *
     * A module this service created itself is disposed entirely instead — it exists for no other reason.
     */
    private fun dropFacet(module: Module) {
        val facet = MkDocsFacet.getInstance(module) ?: return
        if (facet.configuration.ownsModule) {
            ModuleManager.getInstance(project).disposeModule(module)
            return
        }
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.removeFacet(facet)
        model.commit()
    }
}
