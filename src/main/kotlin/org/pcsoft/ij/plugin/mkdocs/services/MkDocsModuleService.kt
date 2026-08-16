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
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsFacetSync
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetConfiguration
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature

/**
 * Keeps the IDE's module model in sync with the MkDocs sites found in the project.
 *
 * The rule implemented here: the directory **above** an `mkdocs.yml` / `mkdocs.yaml` is an MkDocs module.
 * It is represented by an [MkDocsFacet] on the IntelliJ module owning that directory; a module of its own
 * is created whenever that module cannot represent the site — because the directory belongs to no module at
 * all, or because the module already represents a different site. The configuration file always wins — a
 * facet is created, updated or dropped to match what is on disk.
 *
 * Which optional features a site uses is none of this service's business. It only tells the registered
 * [MkDocsSiteFeature] extensions that a site was detected or lost; each of them decides what that means for
 * a facet of its own.
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
     *
     * Every scan ends with [MkDocsSitesListener.sitesChanged], even when the set of modules came out
     * unchanged: a scan also runs after a configuration file was written, and its content is what the
     * navigation of a site is read from. The notification goes out on the calling thread, which is usually
     * the pooled thread [scheduleSync] uses.
     */
    fun sync() {
        if (project.isDisposed) return
        val sites = runReadActionBlocking { if (project.isDisposed) emptyList() else findSites() }
        WriteAction.runAndWait<RuntimeException> {
            if (!project.isDisposed) MkDocsFacetSync.running { apply(sites) }
        }
        if (project.isDisposed) return
        project.messageBus.syncPublisher(MkDocsSitesListener.TOPIC).sitesChanged()
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
     * The search starts at the project directory and at every content root, skipping [MkDocsProject.IGNORED_DIRECTORIES].
     * A directory containing both `mkdocs.yml` and `mkdocs.yaml` yields a single site — MkDocs itself would
     * only ever load one of them, and the `.yml` spelling is preferred so the choice does not depend on the
     * order in which the file system hands out the children.
     *
     * Must be called inside a read action.
     *
     * @return one [MkDocsSite] per detected site root, ordered by path
     */
    fun findSites(): List<MkDocsSite> {
        val configFiles = LinkedHashMap<String, VirtualFile>()
        for (searchRoot in searchRoots()) {
            VfsUtilCore.visitChildrenRecursively(searchRoot, object : VirtualFileVisitor<Any?>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        return file == searchRoot || file.name !in MkDocsProject.IGNORED_DIRECTORIES
                    }
                    if (!MkDocsProject.isConfigFile(file.name)) return true
                    val siteRoot = file.parent ?: return true
                    val known = configFiles[siteRoot.path]
                    if (known == null || preferredConfigFile(known, file) == file) {
                        configFiles[siteRoot.path] = file
                    }
                    return true
                }
            })
        }
        return configFiles.values
            .mapNotNull { file ->
                val siteRoot = file.parent ?: return@mapNotNull null
                MkDocsSite(
                    root = siteRoot,
                    configFile = file,
                    siteName = MkDocsConfig.resolveSiteName(project, file),
                )
            }
            .sortedBy { it.root.path }
    }

    /**
     * Picks the configuration file MkDocs itself would load when a directory holds both spellings.
     *
     * MkDocs looks for `mkdocs.yml` first, so that spelling wins; otherwise the first one found is kept.
     */
    private fun preferredConfigFile(known: VirtualFile, candidate: VirtualFile): VirtualFile {
        val preferredName = MkDocsProject.CONFIG_FILE_NAMES.first()
        return if (candidate.name.equals(preferredName, ignoreCase = true)) candidate else known
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
     * A module can carry only one MkDocs facet and therefore represent only one site. Every further site
     * found inside an already represented module gets a module of its own, exactly like a site belonging to
     * no module at all — otherwise it would silently disappear from the model.
     *
     * Must be called inside a write action on the EDT.
     */
    private fun apply(sites: List<MkDocsSite>) {
        val handled = HashSet<Module>()
        for (site in sites) {
            val owner = ModuleUtilCore.findModuleForFile(site.root, project)
            if (owner != null && handled.add(owner)) {
                updateFacet(owner, site, ownsModule = false)
                continue
            }
            val created = createModule(site, owner) ?: continue
            handled.add(created)
            updateFacet(created, site, ownsModule = true, ownerModuleName = owner?.name ?: "")
        }
        for (module in ModuleManager.getInstance(project).modules.toList()) {
            if (module in handled) continue
            dropFacet(module)
        }
    }

    /**
     * Creates a persistent module whose single content root is the site root.
     *
     * Used whenever the site cannot be represented by [owner] — either because there is no owning module, or
     * because it already represents another site. In the latter case the site root has to leave the owner
     * first: IntelliJ lets a directory belong to a single module only, so it is excluded there before it
     * becomes the content root of the new module.
     *
     * @param site the site to create a module for
     * @param owner the module the site root currently belongs to, or `null` if it belongs to none
     * @return the created module, or `null` if the root has no path on the local file system or could not be
     *         detached from [owner]
     */
    private fun createModule(site: MkDocsSite, owner: Module?): Module? {
        val moduleManager = ModuleManager.getInstance(project)
        val directory = runCatching { site.root.toNioPath() }.getOrNull() ?: return null
        if (owner != null && !excludeFromOwner(owner, site.root)) {
            thisLogger().warn(
                "Cannot detach ${site.root.path} from module '${owner.name}', skipping this MkDocs site"
            )
            return null
        }
        val name = uniqueModuleName(site.siteName)
        // The generic default type: MkDocs is language agnostic, and a Java module type would not even be
        // available in the non-Java IDEs of the supported matrix.
        val moduleTypeId = ModuleTypeManager.getInstance().defaultModuleType.id
        val module = moduleManager.newModule(directory.resolve("$name.iml"), moduleTypeId)
        ModuleRootModificationUtil.addContentRoot(module, site.root)
        return module
    }

    /**
     * Excludes [siteRoot] from [owner] so it can become the content root of a module of its own.
     *
     * @return `false` if [owner] has no content root strictly above [siteRoot] — excluding a content root
     *         itself would remove the owner's own root, which is never done
     */
    private fun excludeFromOwner(owner: Module, siteRoot: VirtualFile): Boolean {
        val contentRoot = contentRootAbove(owner, siteRoot) ?: return false
        ModuleRootModificationUtil.updateExcludedFolders(owner, contentRoot, emptyList(), listOf(siteRoot.url))
        return true
    }

    /**
     * Reverts the exclusion made by [excludeFromOwner] before an owned module is disposed.
     *
     * Without this the directory would stay excluded in the former owner after the site is gone, hiding it
     * from indexing and search for no reason.
     *
     * @param module the owned module about to be disposed
     * @param configuration its facet configuration, holding the name of the former owner
     */
    private fun releaseFromOwner(module: Module, configuration: MkDocsFacetConfiguration) {
        if (configuration.ownerModuleName.isEmpty()) return
        val owner = ModuleManager.getInstance(project)
            .findModuleByName(configuration.ownerModuleName) ?: return
        val siteRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull() ?: return
        val contentRoot = contentRootAbove(owner, siteRoot) ?: return
        ModuleRootModificationUtil.updateExcludedFolders(owner, contentRoot, listOf(siteRoot.url), emptyList())
    }

    /**
     * Returns the content root of [module] strictly above [file], or `null` if there is none.
     */
    private fun contentRootAbove(module: Module, file: VirtualFile): VirtualFile? =
        ModuleRootManager.getInstance(module).contentRoots
            .firstOrNull { VfsUtilCore.isAncestor(it, file, true) }

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
     * @param ownerModuleName name of the module the site root was detached from, empty if it belonged to none
     */
    private fun updateFacet(
        module: Module,
        site: MkDocsSite,
        ownsModule: Boolean,
        ownerModuleName: String = "",
    ) {
        val configFilePath = VfsUtilCore.getRelativePath(site.configFile, site.root) ?: site.configFile.name
        val facetManager = FacetManager.getInstance(module)

        val existing = MkDocsFacet.getInstance(module)
        if (existing != null) {
            val configuration = existing.configuration
            if (configuration.siteName != site.siteName || configuration.configFilePath != configFilePath) {
                configuration.siteName = site.siteName
                configuration.configFilePath = configFilePath
                facetManager.facetConfigurationChanged(existing)
            }
            syncFeatureFacets(module, site)
            return
        }

        val facetType = MkDocsFacet.facetType
        val configuration = MkDocsFacetConfiguration().apply {
            siteName = site.siteName
            this.configFilePath = configFilePath
            this.ownsModule = ownsModule
            this.ownerModuleName = ownerModuleName
        }
        val facet = facetType.createFacet(module, facetType.defaultFacetName, configuration, null)
        val model = facetManager.createModifiableModel()
        model.addFacet(facet)
        model.commit()
        syncFeatureFacets(module, site)
    }

    /**
     * Lets every registered site feature bring its own facet in line with [site].
     *
     * A feature facet mirrors the configuration file, which is what makes a feature detected rather than
     * remembered: a site that switches the feature on or off by hand gains or loses the facet with the next
     * detection run. What "in use" means is the feature's own business — this service only says which site
     * was found on which module.
     *
     * Called only for a module that already carries the MkDocs facet. A feature facet is not a sub facet of
     * it — the platform has deprecated those — so the pairing is done here, together with [dropFacet], which
     * takes the feature facets away again with the MkDocs facet.
     *
     * @param module the module representing the site
     * @param site the detected site
     */
    private fun syncFeatureFacets(module: Module, site: MkDocsSite) {
        for (feature in MkDocsSiteFeature.EP_NAME.extensionList) {
            feature.syncFacet(module, site)
        }
    }

    /**
     * Drops the MkDocs facet from [module] once its configuration file is gone.
     *
     * A module this service created itself is disposed entirely instead — it exists for no other reason — and
     * its site root is handed back to the module it was taken from. Everywhere else the feature facets go
     * first, so none of them outlives the site it belongs to.
     */
    private fun dropFacet(module: Module) {
        val facet = MkDocsFacet.getInstance(module) ?: return
        if (facet.configuration.ownsModule) {
            releaseFromOwner(module, facet.configuration)
            ModuleManager.getInstance(project).disposeModule(module)
            return
        }
        for (feature in MkDocsSiteFeature.EP_NAME.extensionList) {
            feature.removeFacet(module)
        }
        val model = FacetManager.getInstance(module).createModifiableModel()
        model.removeFacet(facet)
        model.commit()
    }
}
