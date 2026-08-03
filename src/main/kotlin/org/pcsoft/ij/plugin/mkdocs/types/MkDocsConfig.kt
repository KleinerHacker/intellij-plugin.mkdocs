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
import org.jetbrains.yaml.psi.YAMLScalar

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

    /**
     * Reads `site_name` from [configFile].
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * Only a scalar value is accepted. A half-written file can make the parser see a sequence or a mapping
     * behind `site_name`, and the text of such a node is no usable name.
     *
     * @return the trimmed value of `site_name`, or `null` if the file is not YAML, the key is absent, its
     *         value is not a scalar, or the value is blank
     */
    fun readSiteName(project: Project, configFile: VirtualFile): String? {
        val yamlFile = PsiManager.getInstance(project).findFile(configFile) as? YAMLFile ?: return null
        val keyValue = YAMLUtil.getQualifiedKeyInFile(yamlFile, KEY_SITE_NAME) ?: return null
        val scalar = keyValue.value as? YAMLScalar ?: return null
        return scalar.textValue.trim().takeIf { it.isNotEmpty() }
    }

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
}
