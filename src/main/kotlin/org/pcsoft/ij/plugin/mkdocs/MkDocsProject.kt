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

    /** Extension of the Markdown files MkDocs turns into pages. */
    const val PAGE_EXTENSION: String = "md"

    /** The directory MkDocs reads the documentation sources from when `docs_dir` is not set. */
    const val DEFAULT_DOCS_DIR: String = "docs"

    /**
     * The directory the plugin puts asset files into by default.
     *
     * MkDocs has no configuration key for this — it is a convention. The directory lives inside the
     * documentation directory so MkDocs ships its content with the site.
     */
    const val DEFAULT_ASSETS_DIR: String = "assets"

    /** The directory MkDocs builds the site into when `site_dir` is not set. */
    const val DEFAULT_SITE_DIR: String = "site"

    /** Build output directory of a site living in a Maven module. */
    const val MAVEN_SITE_DIR: String = "target/docs"

    /** Build output directory of a site living in a Gradle module. */
    const val GRADLE_SITE_DIR: String = "build/docs"

    /** Build output directory of a site living in a plain IntelliJ IDEA module. */
    const val IDEA_SITE_DIR: String = "out/docs"

    /** File announcing a Maven module. */
    private const val MAVEN_MARKER = "pom.xml"

    /** Files announcing a Gradle module. */
    private val GRADLE_MARKERS = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")

    /** Directory announcing an IntelliJ IDEA project. */
    private const val IDEA_MARKER_DIRECTORY = ".idea"

    /** Extension of the file announcing an IntelliJ IDEA module. */
    private const val IDEA_MODULE_EXTENSION = ".iml"

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
     * Returns `true` if [name] is usable as the build output directory of a site.
     *
     * Unlike [isValidDirectoryName] this allows several levels, because the sensible defaults of the build
     * systems are nested — but the path still has to stay below the site root, so absolute paths, drive
     * letters and `..` are refused.
     *
     * @param name the directory name to check, relative to the site root
     */
    fun isValidSiteDirName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith('/') || name.startsWith('\\') || name.contains(':')) return false
        val segments = name.split('/', '\\')
        return segments.all { it.isNotBlank() } && segments.none { it.trim() == ".." || it.trim() == "." }
    }

    /**
     * Returns the build output directory suggested for a module whose directory holds [entryNames].
     *
     * The build system decides where generated output belongs, so a site inside such a module should not
     * drop its rendered HTML next to the sources. Maven wins over Gradle and Gradle over IDEA, because a
     * directory carrying several marker files is driven by the outermost of them.
     *
     * @param entryNames the names of the files and directories directly inside the directory to inspect
     * @return the matching output directory, or `null` if [entryNames] announces no build system
     */
    fun siteDirFor(entryNames: Collection<String>): String? = when {
        entryNames.any { it.equals(MAVEN_MARKER, ignoreCase = true) } -> MAVEN_SITE_DIR
        entryNames.any { it.lowercase() in GRADLE_MARKERS } -> GRADLE_SITE_DIR
        entryNames.any {
            it.equals(IDEA_MARKER_DIRECTORY, ignoreCase = true) ||
                it.endsWith(IDEA_MODULE_EXTENSION, ignoreCase = true)
        } -> IDEA_SITE_DIR

        else -> null
    }

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

    /**
     * Returns `true` if [fileName] is a Markdown file, and therefore a page of a site.
     *
     * @param fileName the bare file name, without any directory part
     */
    fun isPageFile(fileName: String): Boolean =
        fileName.length > PAGE_EXTENSION.length + 1 &&
            fileName.endsWith(".$PAGE_EXTENSION", ignoreCase = true)
}
