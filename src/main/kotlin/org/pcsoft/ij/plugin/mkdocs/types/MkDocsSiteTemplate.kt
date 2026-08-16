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
import java.net.URI
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
 * Every property beyond the first five is optional: an empty value means the corresponding key is left out
 * of the generated configuration file rather than written empty.
 *
 * @property rootPath the directory the site is created in — it becomes the site root and holds `mkdocs.yml`.
 *                    It does not have to exist yet: the wizard appends the site directory name to the
 *                    selected location, and every missing level of the path is created along with the site
 * @property siteName value written to `site_name`
 * @property docsDirName directory the documentation sources go into, written to `docs_dir` when it differs
 *                       from the MkDocs default
 * @property assetsDirName directory for asset files, created inside [docsDirName]
 * @property stylesheetsDirName directory for style sheets, created inside [docsDirName]. MkDocs only loads a
 *                             style sheet once `extra_css` names it, so the directory is a place to put them,
 *                             not a promise that they are used
 * @property siteDirName directory `mkdocs build` writes the rendered site to, written to `site_dir` when it
 *                       differs from the MkDocs default. Unlike the other directories this one may carry
 *                       several levels, because the build systems keep their output nested
 * @property siteAuthor value written to `site_author`
 * @property siteDescription value written to `site_description`
 * @property siteUrl value written to `site_url`
 * @property repoName value written to `repo_name`
 * @property repoUrl value written to `repo_url`
 * @property copyright value written to `copyright`
 * @property features the features to switch on for the new site
 */
data class MkDocsSiteTemplate(
    val rootPath: Path,
    val siteName: String,
    val docsDirName: String = DEFAULT_DOCS_DIR,
    val assetsDirName: String = DEFAULT_ASSETS_DIR,
    val stylesheetsDirName: String = DEFAULT_STYLESHEETS_DIR,
    val siteDirName: String = DEFAULT_SITE_DIR,
    val siteAuthor: String = "",
    val siteDescription: String = "",
    val siteUrl: String = "",
    val repoName: String = "",
    val repoUrl: String = "",
    val copyright: String = "",
    val features: List<MkDocsSiteFeature> = emptyList(),
) {

    companion object {

        /** The directory MkDocs reads the documentation sources from when `docs_dir` is not set. */
        const val DEFAULT_DOCS_DIR: String = "docs"

        /**
         * The directory the plugin puts asset files into by default.
         *
         * MkDocs has no configuration key for this — it is a convention. The directory lives inside the
         * documentation directory so MkDocs ships its content with the site.
         */
        const val DEFAULT_ASSETS_DIR: String = "assets"

        /**
         * The directory the plugin puts style sheets into by default.
         *
         * Like [DEFAULT_ASSETS_DIR] this is a convention rather than an MkDocs key: MkDocs only learns about
         * a style sheet once `extra_css` names it. The directory lives inside the documentation directory,
         * because that is the only place MkDocs ships files from.
         */
        const val DEFAULT_STYLESHEETS_DIR: String = "stylesheets"

        /** The directory MkDocs builds the site into when `site_dir` is not set. */
        const val DEFAULT_SITE_DIR: String = "site"

        /**
         * Returns `true` if [url] can be used as a value of `site_url` or `repo_url`.
         *
         * An empty value is fine — both keys are optional and are then left out entirely. Everything else
         * has to be an absolute `http` or `https` address, because that is the only thing MkDocs turns into
         * a working link.
         *
         * @param url the address as typed by the user
         */
        @JvmStatic
        fun isUsableUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return true
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
            if (!uri.isAbsolute || uri.host.isNullOrBlank()) return false
            return uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        }
    }

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
        !MkDocsProject.isValidDirectoryName(stylesheetsDirName) ->
            MkDocsSiteTemplateError.INVALID_STYLESHEETS_DIR

        !MkDocsProject.isValidSiteDirName(siteDirName) -> MkDocsSiteTemplateError.INVALID_SITE_DIR
        !isUsableUrl(siteUrl) -> MkDocsSiteTemplateError.INVALID_SITE_URL
        !isUsableUrl(repoUrl) -> MkDocsSiteTemplateError.INVALID_REPO_URL
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

    /** No name was given for the site directory. */
    BLANK_NAME("create.site.error.name"),

    /** No site name was given. */
    BLANK_SITE_NAME("create.site.error.siteName"),

    /** The documentation directory name is empty or carries a path of its own. */
    INVALID_DOCS_DIR("create.site.error.docsDir"),

    /** The assets directory name is empty or carries a path of its own. */
    INVALID_ASSETS_DIR("create.site.error.assetsDir"),

    /** The stylesheets directory name is empty or carries a path of its own. */
    INVALID_STYLESHEETS_DIR("create.site.error.stylesheetsDir"),

    /** The output directory is empty, absolute, or climbs out of the site root. */
    INVALID_SITE_DIR("create.site.error.siteDir"),

    /** The site address is neither empty nor an absolute `http` or `https` address. */
    INVALID_SITE_URL("create.site.error.siteUrl"),

    /** The repository address is neither empty nor an absolute `http` or `https` address. */
    INVALID_REPO_URL("create.site.error.repoUrl"),

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
