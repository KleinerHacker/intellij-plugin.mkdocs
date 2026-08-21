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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsScmProvider
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsScmUrl

/**
 * Answers what the creation wizard wants to know about the repository a new site is created in.
 *
 * Asks every registered [MkDocsScmProvider] and takes the first usable answer. With no provider registered —
 * which is the case whenever the corresponding VCS plugin is missing or switched off — every answer is
 * `null`, and the wizard falls back to empty fields.
 *
 * @param project the project the wizard runs in
 */
@Service(Service.Level.PROJECT)
class MkDocsScmService(private val project: Project) {

    /**
     * Returns the web address of the repository [directory] lives in, ready for `repo_url`.
     *
     * @param directory the directory the site is created in, or `null` if it is not known yet
     * @return the address, or `null` if no provider knows a repository for [directory]
     */
    fun repositoryUrl(directory: VirtualFile?): String? =
        rawRemoteUrl(directory)?.let { MkDocsScmUrl.toWebUrl(it) }

    /**
     * Returns the name of the repository [directory] lives in, ready for `repo_name`.
     *
     * @param directory the directory the site is created in, or `null` if it is not known yet
     * @return the name, or `null` if no provider knows a repository for [directory]
     */
    fun repositoryName(directory: VirtualFile?): String? =
        rawRemoteUrl(directory)?.let { MkDocsScmUrl.toRepositoryName(it) }

    /**
     * Returns the name the version control system records for the committing user.
     *
     * @param directory the directory the site is created in, or `null` if it is not known yet
     * @return the user name, or `null` if none is configured
     */
    fun userName(directory: VirtualFile?): String? =
        providers().firstNotNullOfOrNull { provider ->
            runCatching { provider.userName(project, directory) }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    /** The remote address as the version control system stores it, before any normalisation. */
    private fun rawRemoteUrl(directory: VirtualFile?): String? =
        providers().firstNotNullOfOrNull { provider ->
            runCatching { provider.remoteUrl(project, directory) }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    /**
     * The registered providers.
     *
     * The extension point is looked up lazily rather than kept: the VCS plugin backing it can be loaded and
     * unloaded while the IDE runs.
     */
    private fun providers(): List<MkDocsScmProvider> = MkDocsScmProvider.EP_NAME.extensionList
}
