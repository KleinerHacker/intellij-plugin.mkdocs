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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Reads what the creation wizard can learn from the version control system a project lives in.
 *
 * Only the remote address and the name of the committing user are of interest, and neither is part of the
 * generic VCS API of the platform — both are specific to the version control system in use. The extension
 * point exists so the implementation can live in a module that is only loaded when the corresponding VCS
 * plugin is present: without it the wizard simply starts with empty fields.
 */
interface MkDocsScmProvider {

    companion object {

        /** Where implementations register themselves. */
        @JvmField
        val EP_NAME: ExtensionPointName<MkDocsScmProvider> =
            ExtensionPointName.create("org.pcsoft.ij.plugin.mkdocs.scmProvider")
    }

    /**
     * Returns the address of the remote [directory] was cloned from, in whatever form the VCS stores it.
     *
     * @param project the project [directory] belongs to
     * @param directory the directory the site is created in, which does not have to exist yet
     * @return the raw remote address, or `null` if [directory] is under no repository this provider knows
     */
    fun remoteUrl(project: Project, directory: VirtualFile?): String?

    /**
     * Returns the name the VCS records for the committing user.
     *
     * @param project the project [directory] belongs to
     * @param directory the directory the site is created in, which does not have to exist yet
     * @return the user name, or `null` if none is configured
     */
    fun userName(project: Project, directory: VirtualFile?): String?
}
