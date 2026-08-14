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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject

/**
 * Reads the few pieces of information the module system needs out of an MkDocs configuration file.
 *
 * Parsing goes through the bundled YAML plugin's PSI instead of a YAML library: the PSI is already built
 * and cached by the IDE, it tolerates the half-written files an editor sees, and it keeps the plugin free
 * of an extra runtime dependency.
 *
 * All functions here must be called inside a read action.
 */
object MkDocsConfig {

    /** The MkDocs configuration key holding the human readable name of the site. */
    const val KEY_SITE_NAME: String = "site_name"

    /** The MkDocs configuration key holding the author of the site. */
    const val KEY_SITE_AUTHOR: String = "site_author"

    /** The MkDocs configuration key holding the description shown to search engines. */
    const val KEY_SITE_DESCRIPTION: String = "site_description"

    /** The MkDocs configuration key holding the address the built site is published under. */
    const val KEY_SITE_URL: String = "site_url"

    /** The MkDocs configuration key holding the display name of the source repository. */
    const val KEY_REPO_NAME: String = "repo_name"

    /** The MkDocs configuration key holding the address of the source repository. */
    const val KEY_REPO_URL: String = "repo_url"

    /** The MkDocs configuration key holding the copyright notice shown in the footer. */
    const val KEY_COPYRIGHT: String = "copyright"

    /** The MkDocs configuration key holding the directory the documentation sources live in. */
    const val KEY_DOCS_DIR: String = "docs_dir"

    /** The MkDocs configuration key holding the directory the rendered site is written to. */
    const val KEY_SITE_DIR: String = "site_dir"

    /** The MkDocs configuration key holding the navigation of the site. */
    const val KEY_NAV: String = "nav"

    /** The MkDocs configuration key holding the theme of the site. */
    const val KEY_THEME: String = "theme"

    /** The MkDocs configuration key holding the name of the theme, below [KEY_THEME]. */
    const val KEY_THEME_NAME: String = "theme.name"

    /** The name MkDocs knows the Material theme under. */
    const val THEME_MATERIAL: String = "material"

    /** The MkDocs configuration key listing the style sheets the built site loads. */
    const val KEY_EXTRA_CSS: String = "extra_css"

    /**
     * The keys a site should carry so its pages carry usable metadata.
     *
     * Listed in the order they belong into the configuration file, which is also the order the inspection
     * reports them in.
     */
    val METADATA_KEYS: List<String> = listOf(KEY_SITE_NAME, KEY_SITE_AUTHOR, KEY_SITE_DESCRIPTION)

    /**
     * Returns the mapping holding the configuration keys of [file], or `null` if it has none yet.
     *
     * A configuration file is a single YAML mapping. Reaching for it here rather than in each caller keeps
     * the readers of structured keys — the navigation, the metadata inspection — on one understanding of
     * what the top level of the file is.
     *
     * @param file the configuration file to inspect
     */
    fun topLevelMapping(file: YAMLFile): YAMLMapping? =
        file.documents.firstNotNullOfOrNull { it.topLevelValue as? YAMLMapping }

    /**
     * Returns the PSI of [configFile], or `null` if it does not parse as YAML.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     */
    fun yamlFileOf(project: Project, configFile: VirtualFile): YAMLFile? =
        PsiManager.getInstance(project).findFile(configFile) as? YAMLFile

    /**
     * Reads the scalar value of [key] from [configFile].
     *
     * Only a scalar value is accepted. A half-written file can make the parser see a sequence or a mapping
     * behind a key, and the text of such a node is no usable value.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @param key the configuration key to read
     * @return the trimmed value, or `null` if the file is not YAML, the key is absent, its value is not a
     *         scalar, or the value is blank
     */
    private fun readScalar(project: Project, configFile: VirtualFile, key: String): String? {
        val yamlFile = yamlFileOf(project, configFile) ?: return null
        val keyValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, key) ?: return null
        val scalar = keyValue.value as? YAMLScalar ?: return null
        return scalar.textValue.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Reads `site_name` from [configFile].
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the trimmed value of `site_name`, or `null` if the file is not YAML, the key is absent, its
     *         value is not a scalar, or the value is blank
     */
    fun readSiteName(project: Project, configFile: VirtualFile): String? =
        readScalar(project, configFile, KEY_SITE_NAME)

    /**
     * Determines the name of the MkDocs module for [configFile].
     *
     * Falls back to the name of the directory containing [configFile] whenever `site_name` is unusable,
     * so a site always has a name.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @return the site name, never blank
     */
    fun resolveSiteName(project: Project, configFile: VirtualFile): String =
        readSiteName(project, configFile)
            ?: configFile.parent?.name?.takeIf { it.isNotEmpty() }
            ?: configFile.nameWithoutExtension

    /**
     * Reads `docs_dir` from [configFile].
     *
     * The value is returned as written — MkDocs resolves it relative to the configuration file, and a site
     * may well point it at a directory of its own naming.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the trimmed value of `docs_dir`, or `null` if it is absent, not a scalar, or blank
     */
    fun readDocsDir(project: Project, configFile: VirtualFile): String? =
        readScalar(project, configFile, KEY_DOCS_DIR)

    /**
     * Determines the documentation directory of the site described by [configFile].
     *
     * Falls back to [MkDocsProject.DEFAULT_DOCS_DIR], which is what MkDocs itself uses when the key is
     * missing.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @return the documentation directory, relative to the site root, never blank
     */
    fun resolveDocsDir(project: Project, configFile: VirtualFile): String =
        readDocsDir(project, configFile) ?: MkDocsProject.DEFAULT_DOCS_DIR

    /**
     * Reads `site_dir` from [configFile].
     *
     * The value is returned as written, for the same reason as in [readDocsDir].
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the trimmed value of `site_dir`, or `null` if it is absent, not a scalar, or blank
     */
    fun readSiteDir(project: Project, configFile: VirtualFile): String? =
        readScalar(project, configFile, KEY_SITE_DIR)

    /**
     * Determines the build output directory of the site described by [configFile].
     *
     * Falls back to [MkDocsProject.DEFAULT_SITE_DIR], which is what MkDocs itself uses when the key is
     * missing.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @return the output directory, relative to the site root, never blank
     */
    fun resolveSiteDir(project: Project, configFile: VirtualFile): String =
        readSiteDir(project, configFile) ?: MkDocsProject.DEFAULT_SITE_DIR

    /**
     * Reads `extra_css` from [configFile].
     *
     * MkDocs takes the key as a sequence of paths relative to `docs_dir`, and only a style sheet named there
     * is loaded by the built site. The values are returned as written, so the caller can resolve them against
     * the documentation directory itself.
     *
     * Anything that is not a scalar sequence entry is skipped: a half-written file makes the parser see
     * mappings and empty entries behind the key, and neither is a usable path. A missing key, a key whose
     * value is a plain scalar and a key with an empty sequence all yield an empty list — in each case the site
     * loads no style sheet.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the referenced paths, relative to the documentation directory, never containing a blank entry
     */
    fun readExtraCss(project: Project, configFile: VirtualFile): List<String> {
        val yamlFile = yamlFileOf(project, configFile) ?: return emptyList()
        val keyValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, KEY_EXTRA_CSS) ?: return emptyList()
        val sequence = keyValue.value as? YAMLSequence ?: return emptyList()
        return sequence.items.mapNotNull { item ->
            (item.value as? YAMLScalar)?.textValue?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Reads the scalar value addressed by the dotted [path] from [configFile].
     *
     * The counterpart of [MkDocsConfigWriter.setNestedScalar]: a path of a single segment reads a top level
     * key, a longer one walks the block mappings below it, as in `theme.palette.primary`.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the key to read
     * @return the trimmed value, or `null` if the file is not YAML, the path does not exist, its value is not
     *         a scalar, or the value is blank
     */
    fun readNestedScalar(project: Project, configFile: VirtualFile, path: String): String? {
        val yamlFile = yamlFileOf(project, configFile) ?: return null
        val segments = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val keyValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, *segments.toTypedArray()) ?: return null
        val scalar = keyValue.value as? YAMLScalar ?: return null
        return scalar.textValue.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Reads the sequence of scalars addressed by the dotted [path] from [configFile].
     *
     * Anything that is not a scalar entry is skipped: a half-written file makes the parser see mappings and
     * empty entries behind the key, and neither is a usable value. A missing key, a key holding a plain scalar
     * and a key holding an empty sequence all yield an empty list.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence to read
     * @return the entries as written, never containing a blank one
     */
    fun readScalarSequence(project: Project, configFile: VirtualFile, path: String): List<String> {
        val sequence = sequenceAt(project, configFile, path) ?: return emptyList()
        return sequence.items.mapNotNull { item ->
            (item.value as? YAMLScalar)?.textValue?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Reads the sequence of mappings addressed by the dotted [path] from [configFile].
     *
     * The shape `extra.social` and `theme.palette` are written in. Each entry is returned as the keys it
     * carries, in the order the file has them; entries that are not mappings are skipped, and inside an entry
     * only scalar values are taken, for the same reason as in [readScalarSequence].
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence to read
     * @return one map per entry, each holding the scalar keys of that entry
     */
    fun readMappingSequence(project: Project, configFile: VirtualFile, path: String): List<Map<String, String>> {
        val sequence = sequenceAt(project, configFile, path) ?: return emptyList()
        return sequence.items
            .mapNotNull { item -> item.value as? YAMLMapping }
            .map { mapping ->
                mapping.keyValues
                    .mapNotNull { keyValue ->
                        (keyValue.value as? YAMLScalar)?.textValue?.trim()?.let { keyValue.keyText.trim() to it }
                    }
                    .toMap()
            }
    }

    /**
     * Returns the sequence addressed by the dotted [path], or `null` if there is none.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     */
    private fun sequenceAt(project: Project, configFile: VirtualFile, path: String): YAMLSequence? {
        val yamlFile = yamlFileOf(project, configFile) ?: return null
        val segments = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val keyValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, *segments.toTypedArray()) ?: return null
        return keyValue.value as? YAMLSequence
    }

    /**
     * Reads the name of the theme from [configFile].
     *
     * MkDocs accepts the theme in two shapes: as a mapping carrying a `name` key, which is what a themed site
     * with settings of its own looks like, and as a plain scalar naming nothing but the theme. Both are read
     * here, so a caller never has to know which one the author picked.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the trimmed name of the theme, or `null` if the file is not YAML, no theme is configured, or the
     *         name is blank
     */
    fun readThemeName(project: Project, configFile: VirtualFile): String? {
        val yamlFile = yamlFileOf(project, configFile) ?: return null
        val nameValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, *KEY_THEME_NAME.split('.').toTypedArray())
        val scalar = nameValue?.value as? YAMLScalar
            ?: (YAMLUtil.getQualifiedKeyInFile(yamlFile, KEY_THEME)?.value as? YAMLScalar)
            ?: return null
        return scalar.textValue.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Tells whether the site described by [configFile] is built with the Material theme.
     *
     * The comparison ignores case: MkDocs matches the theme by its registered name, and an author writing it
     * differently still gets the same theme.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @return `true` if the configured theme is [THEME_MATERIAL]
     */
    fun isMaterialTheme(project: Project, configFile: VirtualFile): Boolean =
        readThemeName(project, configFile)?.equals(THEME_MATERIAL, ignoreCase = true) == true
}
