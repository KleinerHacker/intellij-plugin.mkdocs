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

package org.pcsoft.ij.plugin.mkdocs

/**
 * Recognition of MkDocs project files by name.
 *
 * This is intentionally a pure, platform-independent helper: it answers questions about *file names*
 * only, so it can be unit-tested without booting the IntelliJ Platform. Anything that needs a
 * `VirtualFile`, a `Project` or PSI belongs in a separate, platform-aware layer on top of this.
 */
object MkDocsProject {

    /**
     * Names MkDocs accepts for its configuration file.
     *
     * MkDocs itself defaults to `mkdocs.yml` and additionally recognises the `.yaml` spelling.
     */
    val CONFIG_FILE_NAMES: Set<String> = setOf("mkdocs.yml", "mkdocs.yaml")

    /**
     * Returns `true` if [fileName] is an MkDocs configuration file.
     *
     * The comparison is case-insensitive because the plugin also has to behave sensibly on
     * case-insensitive file systems (Windows, macOS).
     *
     * @param fileName the bare file name, without any directory part
     */
    fun isConfigFile(fileName: String): Boolean =
        CONFIG_FILE_NAMES.any { it.equals(fileName, ignoreCase = true) }
}
