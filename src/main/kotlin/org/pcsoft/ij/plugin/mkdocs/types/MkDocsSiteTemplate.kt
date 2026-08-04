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

import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Everything needed to create a new MkDocs site.
 *
 * Filled in by the creation wizard and handed to
 * [org.pcsoft.ij.plugin.mkdocs.services.MkDocsSiteCreationService].
 *
 * @property rootPath the directory the site is created in — it becomes the site root and holds `mkdocs.yml`.
 *                    It does not have to exist yet: like the new project dialog, the wizard appends the site
 *                    name to the selected directory, and every missing level of the path is created along
 *                    with the site
 * @property siteName value written to `site_name`
 * @property docsDirName directory the documentation sources go into, written to `docs_dir` when it differs
 *                       from the MkDocs default
 * @property assetsDirName directory for asset files, created inside [docsDirName]
 * @property features the features to switch on for the new site
 */
data class MkDocsSiteTemplate(
    val rootPath: Path,
    val siteName: String,
    val docsDirName: String = MkDocsProject.DEFAULT_DOCS_DIR,
    val assetsDirName: String = MkDocsProject.DEFAULT_ASSETS_DIR,
    val features: List<MkDocsSiteFeature> = emptyList(),
) {

    /**
     * The reason this template cannot be turned into a site, or `null` if it can.
     *
     * The same rules guard the wizard and the creation service, so a template that passes here is one that
     * can actually be written.
     */
    fun validate(): MkDocsSiteTemplateError? = when {
        !isUsableRoot() -> MkDocsSiteTemplateError.INVALID_ROOT
        siteName.isBlank() -> MkDocsSiteTemplateError.BLANK_SITE_NAME
        !MkDocsProject.isValidDirectoryName(docsDirName) -> MkDocsSiteTemplateError.INVALID_DOCS_DIR
        !MkDocsProject.isValidDirectoryName(assetsDirName) -> MkDocsSiteTemplateError.INVALID_ASSETS_DIR
        holdsConfigFile() -> MkDocsSiteTemplateError.SITE_EXISTS
        else -> null
    }

    /**
     * Returns `true` if [rootPath] is a directory or can become one.
     *
     * Whatever of the path is missing is created along with the site, however many levels that takes — the
     * only thing that cannot be worked with is a path that already exists as something other than a
     * directory. An absolute path is required so a half-typed location cannot create a directory somewhere
     * relative to the IDE's working directory.
     */
    private fun isUsableRoot(): Boolean = when {
        !rootPath.isAbsolute || rootPath.nameCount == 0 -> false
        rootPath.exists() -> rootPath.isDirectory()
        else -> true
    }

    /**
     * Something worth telling the user about that does not stop the creation, or `null` if there is nothing.
     *
     * Only meaningful for a template that passes [validate].
     */
    fun warn(): MkDocsSiteTemplateWarning? = when {
        !rootPath.isDirectory() -> null
        holdsEntries() -> MkDocsSiteTemplateWarning.NOT_EMPTY
        else -> null
    }

    /** Returns `true` if [rootPath] contains anything at all. */
    private fun holdsEntries(): Boolean =
        runCatching { rootPath.listDirectoryEntries().isNotEmpty() }.getOrDefault(false)

    /** Returns `true` if [rootPath] already holds an MkDocs configuration file. */
    private fun holdsConfigFile(): Boolean {
        if (!rootPath.isDirectory()) return false
        return runCatching {
            rootPath.listDirectoryEntries().any { !Files.isDirectory(it) && MkDocsProject.isConfigFile(it.name) }
        }.getOrDefault(false)
    }
}

/**
 * Why a [MkDocsSiteTemplate] cannot be turned into a site.
 *
 * @property messageKey key of the user visible message in `messages/MkDocsBundle.properties`
 */
enum class MkDocsSiteTemplateError(val messageKey: String) {

    /** The path is no directory, and neither is the directory it would be created in. */
    INVALID_ROOT("create.site.error.root"),

    /** No site name was given. */
    BLANK_SITE_NAME("create.site.error.siteName"),

    /** The documentation directory name is empty or carries a path of its own. */
    INVALID_DOCS_DIR("create.site.error.docsDir"),

    /** The assets directory name is empty or carries a path of its own. */
    INVALID_ASSETS_DIR("create.site.error.assetsDir"),

    /** The target directory already holds an MkDocs configuration file. */
    SITE_EXISTS("create.site.error.exists"),
}

/**
 * Something about a [MkDocsSiteTemplate] the user should know before creating the site, without it being an
 * obstacle.
 *
 * @property messageKey key of the user visible message in `messages/MkDocsBundle.properties`
 */
enum class MkDocsSiteTemplateWarning(val messageKey: String) {

    /** The target directory exists and already contains something — the site is created alongside it. */
    NOT_EMPTY("create.site.warning.notEmpty"),
}
