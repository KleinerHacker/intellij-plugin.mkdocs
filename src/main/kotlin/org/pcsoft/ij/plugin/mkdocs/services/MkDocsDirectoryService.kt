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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringFactory
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfigWriter
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate

/**
 * The technical directories of a site, as the facet editor shows them.
 *
 * @property docsDirName the documentation directory, relative to the site root
 * @property siteDirName the build output directory, relative to the site root
 * @property assetsDirName the assets directory, inside the documentation directory
 * @property stylesheetsDirName the stylesheets directory, inside the documentation directory
 */
data class MkDocsDirectoryLayout(
    val docsDirName: String,
    val siteDirName: String,
    val assetsDirName: String,
    val stylesheetsDirName: String,
)

/**
 * Renames the technical directories of an MkDocs site.
 *
 * Renaming happens through the platform rename refactoring rather than through the virtual file system: every
 * path value of the configuration file is a file reference (see
 * [org.pcsoft.ij.plugin.mkdocs.reference.MkDocsPathReferenceContributor]), so the refactoring rewrites `nav`,
 * `extra_css`, `logo`, `favicon` and `docs_dir` on its own — and it does the same for the links of the
 * Markdown pages. Doing it by hand would mean reimplementing all of that, and worse.
 *
 * What the refactoring cannot know is the pair of keys no reference points at: `docs_dir` of a file not
 * carrying the key yet, and `site_dir`, which names build output rather than a directory of the site. Both are
 * written afterwards, and only while they differ from the MkDocs default.
 *
 * @param project the project the sites live in
 */
@Service(Service.Level.PROJECT)
class MkDocsDirectoryService(private val project: Project) {

    companion object {

        /**
         * Returns the service instance for [project].
         *
         * @param project the project whose service is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsDirectoryService = project.service()
    }

    /**
     * Returns the layout the site described by [configFile] currently has.
     *
     * The names come from two sources, because MkDocs knows only two of them: `docs_dir` and `site_dir` are
     * read from the configuration file, the assets and the stylesheets directory from the facet of [module].
     *
     * @param module the module carrying the site, or `null` if there is none
     * @param configFile the MkDocs configuration file of the site
     */
    fun currentLayout(module: Module?, configFile: VirtualFile): MkDocsDirectoryLayout = runReadActionBlocking {
        MkDocsDirectoryLayout(
            docsDirName = MkDocsLayout.resolveDocsDir(project, configFile),
            siteDirName = MkDocsLayout.resolveSiteDir(project, configFile),
            assetsDirName = MkDocsLayout.assetsDirNameOf(module),
            stylesheetsDirName = MkDocsLayout.stylesheetsDirNameOf(module),
        )
    }

    /**
     * Moves the site described by [configFile] from [current] to [target].
     *
     * The assets and the stylesheets directory are renamed first: both live inside the documentation
     * directory, and renaming that one first would invalidate the directories found for them. `site_dir` is
     * never moved — it holds build output, which the next build writes again, and it regularly lies outside
     * the site.
     *
     * A directory that does not exist is not created here; only the configuration file learns about the new
     * name then. The facet editor refuses that case, but a site can also lose a directory between the check
     * and the apply.
     *
     * Must be called on the EDT, outside a write action — the rename refactoring runs a command of its own.
     *
     * @param module the module carrying the site, or `null` if there is none
     * @param configFile the MkDocs configuration file of the site
     * @param current the layout the site has now
     * @param target the layout the site should have
     */
    fun applyLayout(
        module: Module?,
        configFile: VirtualFile,
        current: MkDocsDirectoryLayout,
        target: MkDocsDirectoryLayout,
    ) {
        val siteRoot = configFile.parent?.takeIf { it.isValid && it.isDirectory } ?: return
        val docsDir = VfsUtilCore.findRelativeFile(current.docsDirName, siteRoot)?.takeIf { it.isDirectory }

        if (docsDir != null) {
            if (target.assetsDirName != current.assetsDirName) {
                renameDirectory(docsDir.findChild(current.assetsDirName), target.assetsDirName)
            }
            if (target.stylesheetsDirName != current.stylesheetsDirName) {
                renameDirectory(docsDir.findChild(current.stylesheetsDirName), target.stylesheetsDirName)
            }
            if (target.docsDirName != current.docsDirName) {
                renameDirectory(docsDir, target.docsDirName)
            }
        }

        writeKey(configFile, MkDocsConfig.KEY_DOCS_DIR, target.docsDirName, MkDocsSiteTemplate.DEFAULT_DOCS_DIR)
        writeKey(configFile, MkDocsConfig.KEY_SITE_DIR, target.siteDirName, MkDocsSiteTemplate.DEFAULT_SITE_DIR)

        if (module != null) {
            MkDocsModuleService.getInstance(project).scheduleSync()
        }
    }

    /**
     * Writes [siteName] into `site_name` of [configFile].
     *
     * The name is what the whole module is called, so the detection has to see the new one: the sync is
     * scheduled here rather than left to the file system listener, which would only notice a change of the
     * file itself.
     *
     * Does nothing when the file already carries the name, or when the name is blank — a site without a name
     * falls back to its directory name, and writing an empty key would leave a header MkDocs renders empty.
     *
     * Must be called on the EDT, outside a write action.
     *
     * @param configFile the MkDocs configuration file of the site
     * @param siteName the name the site should carry
     */
    fun renameSite(configFile: VirtualFile, siteName: String) {
        val trimmed = siteName.trim()
        if (trimmed.isEmpty()) return
        if (runReadActionBlocking { MkDocsConfig.readSiteName(project, configFile) } == trimmed) return

        WriteCommandAction.writeCommandAction(project)
            .withName(MkDocsBundle.message("facet.mkdocs.command.renameSite"))
            .run<RuntimeException> {
                MkDocsConfigWriter.setScalarKey(project, configFile, MkDocsConfig.KEY_SITE_NAME, trimmed)
            }
        MkDocsModuleService.getInstance(project).scheduleSync()
    }

    /**
     * Renames [directory] to [newName], rewriting every reference pointing at it.
     *
     * Does nothing when [directory] is `null`, invalid, no directory, or already carries the name — the last
     * case is one a rename refactoring would refuse anyway.
     *
     * Must be called on the EDT, outside a write action.
     *
     * @param directory the directory to rename, or `null` if it does not exist
     * @param newName the name the directory should carry
     */
    fun renameDirectory(directory: VirtualFile?, newName: String) {
        if (directory == null || !directory.isValid || !directory.isDirectory) return
        if (directory.name == newName || !MkDocsProject.isValidDirectoryName(newName)) return

        val psiDirectory = runReadActionBlocking { PsiManager.getInstance(project).findDirectory(directory) } ?: return
        RefactoringFactory.getInstance(project)
            .createRename(psiDirectory, newName, false, false)
            .run()
    }

    /**
     * Writes [value] into [key] of [configFile], or removes the key when [value] is what MkDocs assumes anyway.
     *
     * The file is left untouched when it already says exactly that — a rename refactoring has usually rewritten
     * `docs_dir` already, and rewriting it a second time would put a second entry into the undo stack for
     * nothing.
     *
     * @param configFile the MkDocs configuration file of the site
     * @param key the configuration key to write
     * @param value the value the key should carry
     * @param default the value MkDocs uses when the key is absent
     */
    private fun writeKey(configFile: VirtualFile, key: String, value: String, default: String) {
        val written = runReadActionBlocking {
            if (key == MkDocsConfig.KEY_DOCS_DIR) {
                MkDocsConfig.readDocsDir(project, configFile)
            } else {
                MkDocsConfig.readSiteDir(project, configFile)
            }
        }
        val wanted = value.takeIf { it != default }
        if (written == wanted) return

        WriteCommandAction.writeCommandAction(project)
            .withName(MkDocsBundle.message("facet.mkdocs.command.applyLayout"))
            .run<RuntimeException> {
                if (wanted == null) {
                    MkDocsConfigWriter.removeKey(project, configFile, key)
                } else {
                    MkDocsConfigWriter.setScalarKey(project, configFile, key, wanted)
                }
            }
    }
}
