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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Finds the files an installed Python distribution ships, for every feature of an MkDocs site alike.
 *
 * A feature arrives as a distribution, and the IDE needs files out of it — the icon sets of *Material for
 * MkDocs* below `material/templates/.icons`, the templates of another plugin below its own package. So a
 * caller names two things: the distribution as pip knows it, and the path of what it wants *inside* the
 * installation directory.
 *
 * The installation is looked up through [MkDocsPipService] and through nothing else. Searching the checkout
 * for directories that look like a virtual environment was tried before and is deliberately gone: it guessed
 * where an environment could be while pip simply knows where the packages of the interpreter in use lie.
 *
 * MUST NOT be called on the EDT while the answer of pip is still cold — see [MkDocsPipService].
 */
object MkDocsInstallationLocator {

    /**
     * Returns the directory [pathInPackage] of the installed [distribution], or `null` if there is none.
     *
     * @param distribution the name of the distribution as pip knows it, for example `mkdocs-material`
     * @param pathInPackage the path of the wanted directory below the installation directory
     */
    fun detect(distribution: String, pathInPackage: String): VirtualFile? =
        detectAll(distribution, pathInPackage).firstOrNull()

    /**
     * Returns every directory [pathInPackage] that can be found for [distribution].
     *
     * Today pip names one installation, so the list holds one entry at most — it is a list because a
     * machine carrying more than one interpreter is the case this signature leaves room for. What the
     * settings page offers for choosing is [detectLocations], the installation directory itself.
     *
     * @param distribution the name of the distribution as pip knows it
     * @param pathInPackage the path of the wanted directory below the installation directory
     */
    fun detectAll(distribution: String, pathInPackage: String): List<VirtualFile> =
        detectLocations(distribution).mapNotNull { resolve("$it/$pathInPackage") }

    /**
     * Returns every installation directory that can be found for [distribution].
     *
     * The installation directory is what `pip show` reports as its `Location`: the `site-packages` directory
     * holding the package and the `*.dist-info` directory belonging to it. That is the directory a settings
     * page offers for choosing, because it is the one carrying the metadata a feature can validate against —
     * a subdirectory of the package alone proves nothing about which distribution wrote it.
     *
     * Today pip names one installation, so the list holds one entry at most; it is a list because a machine
     * carrying more than one interpreter is the case this signature leaves room for.
     *
     * @param distribution the name of the distribution as pip knows it
     */
    fun detectLocations(distribution: String): List<String> =
        listOfNotNull(service<MkDocsPipService>().location(distribution))

    /**
     * Returns the directory at [path], or `null` if there is none.
     *
     * An installation lies outside the project, so the VFS may not know it yet and has to be refreshed. That
     * refresh is only asked for outside a read action: a synchronous refresh under the read lock is what the
     * platform forbids, and inside one the cached state has to do — the next call, made without the lock,
     * picks the directory up.
     *
     * @param path the absolute path of the wanted directory
     */
    private fun resolve(path: String): VirtualFile? {
        val fileSystem = LocalFileSystem.getInstance()
        val file = if (ApplicationManager.getApplication().isReadAccessAllowed) {
            fileSystem.findFileByPath(path)
        } else {
            fileSystem.refreshAndFindFileByPath(path)
        }
        return file?.takeIf { it.isValid && it.isDirectory }
    }
}
