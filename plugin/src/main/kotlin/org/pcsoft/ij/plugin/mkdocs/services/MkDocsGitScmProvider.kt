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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUserRegistry
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsScmProvider

/**
 * Reads remote and user from Git.
 *
 * Registered only through the optional descriptor that is loaded when the Git plugin is present — Git is
 * bundled with every JetBrains IDE, but the user may switch it off, and the plugin has to keep working then.
 */
class MkDocsGitScmProvider : MkDocsScmProvider {

    override fun remoteUrl(project: Project, directory: VirtualFile?): String? {
        val repository = repositoryFor(project, directory) ?: return null
        val remotes = repository.remotes
        val remote = remotes.firstOrNull { it.name == GitRemote.ORIGIN } ?: remotes.firstOrNull() ?: return null
        return remote.firstUrl
    }

    override fun userName(project: Project, directory: VirtualFile?): String? {
        val root = repositoryFor(project, directory)?.root ?: return null
        return GitUserRegistry.getInstance(project).getUser(root)?.name
    }

    /**
     * Returns the repository [directory] lives in.
     *
     * Falls back to the first repository of the project: the wizard is often started from a directory that
     * does not exist yet, and a project with a single repository has an unambiguous answer anyway.
     *
     * @param project the project [directory] belongs to
     * @param directory the directory the site is created in, or `null` if it is not known yet
     */
    private fun repositoryFor(project: Project, directory: VirtualFile?): GitRepository? {
        val manager = GitRepositoryManager.getInstance(project)
        val direct = directory?.let { manager.getRepositoryForFileQuick(it) }
        return direct ?: manager.repositories.firstOrNull()
    }
}
