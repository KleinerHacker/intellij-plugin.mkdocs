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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsInstallationLocator
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the installed *Material for MkDocs*, and the icon sets inside it.
 *
 * The icons the theme offers are not a list this plugin could carry: which ones exist depends on the version
 * of `mkdocs-material` that is installed, and the sets are simply the SVG files shipped inside the package.
 * So what has to be found is the installation directory — the `site-packages` pip reports as its `Location`,
 * holding the package and the `*.dist-info` beside it — and it is looked for in exactly two ways:
 *
 * 1. the path configured in the settings, if there is one — the answer for every setup pip cannot answer for;
 * 2. the installation `pip show mkdocs-material` reports, through [MkDocsInstallationLocator].
 *
 * The installation rather than the icon directory, because that is the directory that can be *checked*:
 * [MkDocsMaterialInstallation] reads the metadata pip wrote next to the package, and a directory failing that
 * check is refused on the settings page instead of turning into an empty completion popup.
 *
 * Nothing else is searched. Walking the checkout for directories that look like a virtual environment was
 * what this did before and is deliberately gone: it guessed where an environment could be, while pip knows
 * where the packages of the interpreter in use lie.
 *
 * May be called from anywhere. Almost every caller — a completion popup, an inlay hint, an annotator — runs
 * under a read action, where the platform forbids waiting for a process, so pip is not asked there but told
 * to answer later; see [analyse].
 */
object MkDocsMaterialIconLocator {

    /** The name of the distribution as pip knows it. */
    const val DISTRIBUTION: String = "mkdocs-material"

    /** The path of the icon sets inside the installation directory. */
    const val ICONS_INSIDE_PACKAGE: String = "material/templates/.icons"

    /** Why the highlighting is restarted, for the log of the daemon. */
    private const val RESTART_REASON = "the location of mkdocs-material became known"

    /** The projects an analysis is running for, so a second one is not started next to it. */
    private val analysing = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the installation directory of the theme, or `null` if there is none.
     *
     * @param project the project whose configured path is asked first
     */
    fun locateInstallation(project: Project): String? {
        configured(project)?.let { return it }
        // Most callers sit under a read action, where pip must not be asked; they get nothing now and the
        // answer is fetched where it may be waited for.
        warmUp(project)
        return MkDocsInstallationLocator.detectLocations(DISTRIBUTION)
            .firstOrNull { MkDocsMaterialInstallation.problemOf(it) == null }
    }

    /**
     * Returns the directory holding the icon sets of the theme, or `null` if there is none.
     *
     * @param project the project whose configured path is asked first
     */
    fun locate(project: Project): VirtualFile? = locateInstallation(project)?.let { icons(it) }

    /**
     * Returns the icon sets of the installation pip reports, or `null` if it reports none.
     *
     * What the settings page shows as the installation it found: the configured path is the answer *given* by
     * the user, and a page offering it back as its own discovery would say nothing.
     */
    fun detect(): VirtualFile? = MkDocsInstallationLocator.detect(DISTRIBUTION, ICONS_INSIDE_PACKAGE)

    /**
     * Reads the installation again from scratch, whatever is remembered about it.
     *
     * What the *Reload installation* button of the settings page, the action of the same name and the entry
     * in the completion popup all do. An installation does not change by itself, so nothing re-checks it on
     * its own — this is the way to say that it did change after all: a package installed next to a running
     * IDE, an environment rebuilt, a directory replaced.
     *
     * @param project the project whose editors are refreshed once the answer is there
     */
    fun reload(project: Project) {
        service<MkDocsPipService>().invalidate()
        service<MkDocsMaterialInstallationCache>().invalidate()
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
        analyse(project)
    }

    /**
     * Asks pip where the theme is installed and reads what it found, unless that is already known.
     *
     * Returns at once, because the caller is usually in a place where waiting for a process is forbidden.
     * What comes back later has to reach the editor on its own, since nobody is going to ask again: the icon
     * index throws its empty answer away and the highlighting of the project is restarted, which is what
     * turns the banner of a missing installation off again once it was simply not asked for yet.
     *
     * Nothing is started while an answer is known or while the same question is in flight, so calling this
     * out of a highlighting pass — which runs again and again — costs nothing.
     *
     * @param project the project whose editors are refreshed once the answer is there
     */
    private fun warmUp(project: Project) {
        if (service<MkDocsPipService>().isKnown(DISTRIBUTION)) return
        analyse(project)
    }

    /**
     * Looks the installation up and reads it, visibly, in the background.
     *
     * A task rather than a bare thread: what happens here takes as long as a process and a file of some
     * thousand lines take, and a user watching an empty completion popup is owed the statement that the
     * question is being worked on. The progress sits in the status bar of the IDE, under the title of the
     * feature.
     *
     * @param project the project whose editors are refreshed once the answer is there
     */
    fun analyse(project: Project) {
        if (project.isDisposed || !analysing.add(project.locationHash)) return
        val task = object : Task.Backgroundable(
            project,
            MkDocsMaterialBundle.message("material.analysis.title"),
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = MkDocsMaterialBundle.message("material.analysis.pip")
                // Started off the EDT and outside a read action, which is the whole point of this task: here
                // pip may be asked and the file listing may be read.
                val location = configured(project) ?: service<MkDocsPipService>().location(DISTRIBUTION)
                indicator.checkCanceled()
                if (location != null) {
                    indicator.text = MkDocsMaterialBundle.message("material.analysis.record")
                    val names = MkDocsMaterialInstallation.iconNames(location)
                    indicator.text = MkDocsMaterialBundle.message("material.analysis.icons", names.size)
                }
            }

            override fun onFinished() {
                analysing.remove(project.locationHash)
                if (project.isDisposed) return
                MkDocsMaterialIconIndex.getInstance(project).invalidate()
                DaemonCodeAnalyzer.getInstance(project).restart(RESTART_REASON)
            }
        }
        // Queued from the EDT: a task is scheduled there, and the callers of this sit under a read action on
        // whatever thread the daemon gave them.
        ApplicationManager.getApplication().invokeLater({ if (!project.isDisposed) task.queue() }, project.disposed)
    }

    /**
     * Returns the icon sets below the installation directory [location], or `null` if there are none.
     *
     * @param location the directory pip reports as its `Location`
     */
    private fun icons(location: String): VirtualFile? {
        val path = "$location/$ICONS_INSIDE_PACKAGE"
        val fileSystem = LocalFileSystem.getInstance()
        // An installation lies outside the project, so the VFS may not know it yet. The refresh is only asked
        // for outside a read action: a synchronous refresh under the read lock is what the platform forbids,
        // and inside one the cached state has to do — the next call, made without the lock, picks it up.
        val file = if (ApplicationManager.getApplication().isReadAccessAllowed) {
            fileSystem.findFileByPath(path)
        } else {
            fileSystem.refreshAndFindFileByPath(path)
        }
        return file?.takeIf { it.isValid && it.isDirectory }
    }

    /**
     * Returns the installation configured in the settings of [project], or `null` if there is none.
     *
     * A configured path that is no installation is treated as if it were not configured at all rather than as
     * an instruction to find nothing: an environment that was moved should degrade into the search, not into
     * silence — and the settings page says outright what is wrong with it.
     *
     * @param project the project whose settings are read
     */
    private fun configured(project: Project): String? {
        val path = MkDocsMaterialIconSettings.getInstance(project).iconPath.trim()
        if (path.isEmpty()) return null
        return path.takeIf { MkDocsMaterialInstallation.problemOf(it) == null }
    }
}
