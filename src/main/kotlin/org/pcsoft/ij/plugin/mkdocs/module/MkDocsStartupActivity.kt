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

package org.pcsoft.ij.plugin.mkdocs.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Performs the initial MkDocs module detection right after a project has been opened.
 *
 * Later changes are picked up by [MkDocsVfsListener]; this activity only covers the state that already
 * exists on disk when the project opens.
 */
class MkDocsStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        MkDocsModuleService.getInstance(project).scheduleSync()
    }
}
