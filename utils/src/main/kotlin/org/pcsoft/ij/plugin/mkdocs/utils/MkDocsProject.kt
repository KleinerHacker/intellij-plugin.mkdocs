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

package org.pcsoft.ij.plugin.mkdocs.utils

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

    /** Extension of the style sheets a site pulls in through `extra_css`. */
    const val STYLESHEET_EXTENSION: String = "css"

    /**
     * Name of the file pinning the Python packages a site is built with.
     *
     * MkDocs does not read it — `pip` does. It is a convention, but a firm one: it is how the MkDocs version,
     * the theme and the plugins of a site are recorded, and it lives next to the configuration file.
     */
    const val REQUIREMENTS_FILE_NAME: String = "requirements.txt"

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
     * Returns `true` if [fileName] is the requirements file of a site.
     *
     * The comparison is case-insensitive for the same reason as in [isConfigFile]. Whether the file really
     * belongs to a site is not decided here — that depends on its location, not on its name.
     *
     * @param fileName the bare file name, without any directory part
     */
    fun isRequirementsFile(fileName: String): Boolean =
        REQUIREMENTS_FILE_NAME.equals(fileName, ignoreCase = true)

    /**
     * Returns `true` if [fileName] is a Markdown file, and therefore a page of a site.
     *
     * @param fileName the bare file name, without any directory part
     */
    fun isPageFile(fileName: String): Boolean =
        fileName.length > PAGE_EXTENSION.length + 1 &&
                fileName.endsWith(".$PAGE_EXTENSION", ignoreCase = true)

    /**
     * Returns `true` if [fileName] is a style sheet.
     *
     * Whether the style sheet is one MkDocs actually loads is not decided here — that depends on `extra_css`,
     * not on the name.
     *
     * @param fileName the bare file name, without any directory part
     */
    fun isStylesheetFile(fileName: String): Boolean =
        fileName.length > STYLESHEET_EXTENSION.length + 1 &&
                fileName.endsWith(".$STYLESHEET_EXTENSION", ignoreCase = true)

    /**
     * Directory names never descended into while searching for MkDocs configuration files.
     *
     * These are build outputs, dependency caches and VCS metadata — a `mkdocs.yml` inside one of them is an
     * artefact, not a source site. Every search for a configuration file has to skip the same set, otherwise
     * one of them would call a site what another one ignores.
     */
    val IGNORED_DIRECTORIES: Set<String> = setOf(
        ".git", ".hg", ".svn", ".idea", ".gradle", ".tox", ".venv", "venv",
        "node_modules", "build", "out", "target", "dist", "__pycache__", "site",
    )
}
