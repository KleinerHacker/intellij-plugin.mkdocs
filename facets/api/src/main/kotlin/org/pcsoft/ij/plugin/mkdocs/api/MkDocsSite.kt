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

package org.pcsoft.ij.plugin.mkdocs.api

import com.intellij.openapi.vfs.VirtualFile

/**
 * A single MkDocs site discovered in the project.
 *
 * A site is identified by its configuration file (`mkdocs.yml` / `mkdocs.yaml`); its *root* is the
 * directory directly above that file. That root is what becomes an MkDocs module in the IDE.
 *
 * @property root the directory containing [configFile]
 * @property configFile the MkDocs configuration file itself
 * @property siteName the display name of the site — `site_name` from the configuration, or the name of
 *                    [root] when the property is missing or blank
 */
data class MkDocsSite(
    val root: VirtualFile,
    val configFile: VirtualFile,
    val siteName: String,
)
