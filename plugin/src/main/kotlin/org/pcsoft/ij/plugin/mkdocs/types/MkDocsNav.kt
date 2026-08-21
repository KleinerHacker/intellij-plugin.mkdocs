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
import org.jetbrains.yaml.psi.*
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig

/**
 * Reads the `nav` section of an MkDocs configuration file.
 *
 * `nav` is the only place that says in which order and under which headings a site presents its pages, so
 * it is what the navigation tree of the tool window shows. Reading goes through the PSI of the bundled YAML
 * plugin, for the same reasons as in [MkDocsConfig]: the tree has to stay usable while the file is being
 * edited, and a half-written entry must not cost the whole navigation.
 *
 * All functions reading PSI must be called inside a read action.
 */
object MkDocsNav {

    /**
     * How deep the navigation is followed.
     *
     * A hand written navigation never comes close to this. The limit exists so a generated — or a
     * pathologically nested — configuration file cannot turn the tree into an endless structure.
     */
    const val MAX_DEPTH: Int = 20

    /** A URI scheme in front of the target, which makes the entry leave the site. */
    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]+:""")

    /** The prefix of a protocol relative address, which also leaves the site. */
    private const val PROTOCOL_RELATIVE = "//"

    /** The prefix MkDocs allows in front of a relative path without it meaning anything. */
    private const val CURRENT_DIRECTORY = "./"

    /**
     * Reads the navigation described by [configFile].
     *
     * Three outcomes are told apart, because the tool window reports them differently: the site defines no
     * navigation at all, it defines an empty one, or it defines entries.
     *
     * @param project the project [configFile] belongs to, used to obtain the PSI
     * @param configFile an MkDocs configuration file
     * @return the entries of `nav` in the order they are written in, an empty list if `nav` is present but
     *         holds nothing, or `null` if the file carries no usable `nav` at all
     */
    fun read(project: Project, configFile: VirtualFile): List<MkDocsNavNode>? {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return null
        val mapping = MkDocsConfig.topLevelMapping(yamlFile) ?: return null
        val keyValue = mapping.getKeyValueByKey(MkDocsConfig.KEY_NAV) ?: return null

        return when (val value = keyValue.value) {
            // "nav:" without anything behind it is an empty navigation, not a missing one.
            null -> emptyList()
            is YAMLSequence -> itemsOf(value, 0)
            // A scalar or a mapping behind "nav" is not what MkDocs accepts; the file is half-written.
            else -> null
        }
    }

    /**
     * Returns `true` if [target] points outside the site.
     *
     * @param target the target of a `nav` entry as written
     */
    fun isExternal(target: String): Boolean {
        val trimmed = target.trim()
        return trimmed.startsWith(PROTOCOL_RELATIVE) || SCHEME.containsMatchIn(trimmed)
    }

    /**
     * Returns the file [page] points at, or `null` if there is none.
     *
     * MkDocs resolves the targets of `nav` against the documentation directory, so that is what the path is
     * resolved against here. A path leading nowhere is not an error of this function — the entry stays in the
     * tree and is marked as unresolved, which is more useful than dropping it silently.
     *
     * @param docsDir the documentation directory of the site
     * @param page the entry to resolve
     */
    fun resolve(docsDir: VirtualFile, page: MkDocsNavPage): VirtualFile? {
        if (!docsDir.isValid || !docsDir.isDirectory || page.path.isEmpty()) return null
        return docsDir.findFileByRelativePath(page.path)
    }

    /**
     * Turns the items of a `nav` sequence into nodes.
     *
     * @param sequence the sequence to read
     * @param depth how many sections the sequence already sits below
     */
    private fun itemsOf(sequence: YAMLSequence, depth: Int): List<MkDocsNavNode> {
        if (depth >= MAX_DEPTH) return emptyList()
        return sequence.items.flatMap { nodesOf(it, depth) }
    }

    /**
     * Turns a single item of a `nav` sequence into nodes.
     *
     * An item is usually one entry, but MkDocs also accepts a mapping carrying several keys at once, which
     * then stands for several entries.
     *
     * @param item the sequence item to read
     * @param depth how many sections the item sits below
     */
    private fun nodesOf(item: YAMLSequenceItem, depth: Int): List<MkDocsNavNode> =
        when (val value = item.value) {
            is YAMLScalar -> listOfNotNull(nodeOf(null, value.textValue))
            is YAMLMapping -> value.keyValues.mapNotNull { nodeOf(it, depth) }
            else -> emptyList()
        }

    /**
     * Turns one `title: target` pair of a `nav` mapping into a node.
     *
     * @param keyValue the pair to read
     * @param depth how many sections the pair sits below
     * @return the node, or `null` if the pair is not something MkDocs would accept
     */
    private fun nodeOf(keyValue: YAMLKeyValue, depth: Int): MkDocsNavNode? {
        val title = titleOf(keyValue)
        return when (val value = keyValue.value) {
            is YAMLScalar -> nodeOf(title, value.textValue)
            is YAMLSequence -> MkDocsNavSection(title, itemsOf(value, depth + 1))
            // A section announced but left empty still belongs into the tree — it shows what is being built.
            null -> MkDocsNavSection(title, emptyList())
            else -> null
        }
    }

    /**
     * Turns a title and a target into the node the target calls for.
     *
     * @param title the title written in `nav`, or `null` if the entry carries none
     * @param target the target as written
     * @return a link for a target leaving the site, a page otherwise, or `null` if the target is blank
     */
    private fun nodeOf(title: String?, target: String): MkDocsNavNode? {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return null
        return if (isExternal(trimmed)) MkDocsNavLink(title, trimmed) else MkDocsNavPage(title, pathOf(trimmed))
    }

    /**
     * Returns the title [keyValue] carries, or `null` if it carries none.
     *
     * The key is read as a scalar so quoting — which a title with spaces or a colon needs — does not end up
     * in the tree.
     *
     * @param keyValue the pair to read the key of
     */
    private fun titleOf(keyValue: YAMLKeyValue): String? {
        val key = (keyValue.key as? YAMLScalar)?.textValue ?: keyValue.keyText
        return key.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Normalises the path of a `nav` entry so equal targets are written equally.
     *
     * @param target the target as written
     */
    private fun pathOf(target: String): String {
        var path = target.replace('\\', '/')
        while (path.startsWith(CURRENT_DIRECTORY)) {
            path = path.substring(CURRENT_DIRECTORY.length)
        }
        return path
    }
}
