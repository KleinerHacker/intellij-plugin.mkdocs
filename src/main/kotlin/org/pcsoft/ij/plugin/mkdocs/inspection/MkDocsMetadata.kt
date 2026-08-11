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

package org.pcsoft.ij.plugin.mkdocs.inspection

import com.intellij.openapi.project.Project
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Finding and adding the metadata keys a configuration file should carry.
 *
 * Three places need exactly these two operations — the inspection, its quick fix and the banner shown above
 * the editor — and all three have to agree on what counts as missing and where a new key belongs. Collecting
 * them here is what keeps them from drifting apart.
 *
 * Everything reading PSI must be called inside a read action, everything writing inside a write action.
 */
object MkDocsMetadata {

    /**
     * Returns the metadata keys [file] does not carry, in the order they belong into the file.
     *
     * A file without a top level mapping is missing all of them: it is empty or half-written, and MkDocs
     * would find nothing in it either.
     *
     * @param file the configuration file to inspect
     */
    fun missingKeys(file: YAMLFile): List<String> {
        val present = topLevelMapping(file)
            ?.keyValues
            ?.mapNotNull { it.keyText.trim().takeIf { key -> key.isNotEmpty() } }
            ?.toSet()
            .orEmpty()
        return MkDocsConfig.METADATA_KEYS.filter { it !in present }
    }

    /**
     * Adds [key] to [file] with an empty value.
     *
     * The value is left empty on purpose: nothing here can know what the site is called or who wrote it, and
     * an invented value is harder to notice than an empty one. The key lands where it belongs among the
     * other metadata keys, so adding all three produces the order MkDocs documents rather than the order
     * they happened to be added in. Everything else in the file keeps the arrangement its author gave it.
     *
     * @param project the project [file] belongs to
     * @param file the configuration file to write to
     * @param key the configuration key to add
     */
    fun addKey(project: Project, file: YAMLFile, key: String) {
        val generator = YAMLElementGenerator.getInstance(project)
        val mapping = topLevelMapping(file)

        if (mapping == null) {
            val document = generator.createDummyYamlWithText("$key:").documents.firstOrNull() ?: return
            file.add(document)
            return
        }

        val keyValue = generator.createYamlKeyValue(key, "")
        val anchor = successorOf(mapping, key)
        if (anchor == null) {
            mapping.putKeyValue(keyValue)
        } else {
            mapping.addBefore(keyValue, anchor)
            mapping.addBefore(generator.createEol(), anchor)
        }
    }

    /**
     * Returns the mapping holding the configuration keys, or `null` if [file] has none yet.
     *
     * @param file the configuration file to inspect
     */
    fun topLevelMapping(file: YAMLFile): YAMLMapping? =
        file.documents.firstNotNullOfOrNull { it.topLevelValue as? YAMLMapping }

    /**
     * Returns the key a newly added [key] has to be placed in front of, or `null` if it belongs at the end.
     *
     * Only the metadata keys take part in the ordering — the rest of the file is none of this function's
     * business.
     *
     * @param mapping the top level mapping of the configuration file
     * @param key the configuration key about to be added
     */
    private fun successorOf(mapping: YAMLMapping, key: String): YAMLKeyValue? {
        val position = MkDocsConfig.METADATA_KEYS.indexOf(key)
        if (position < 0) return null
        val successors = MkDocsConfig.METADATA_KEYS.drop(position + 1).toSet()
        return mapping.keyValues.firstOrNull { it.keyText.trim() in successors }
    }
}
