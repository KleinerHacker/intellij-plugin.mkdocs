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
     * Returns `true` if [name] is usable as a single directory name inside a site.
     *
     * Rejects blank names and anything carrying a path of its own, so a value from the UI cannot escape the
     * site root.
     *
     * @param name the directory name to check
     */
    fun isValidDirectoryName(name: String): Boolean =
        name.isNotBlank() &&
            name.none { it == '/' || it == '\\' } &&
            name.trim() !in setOf(".", "..")

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
