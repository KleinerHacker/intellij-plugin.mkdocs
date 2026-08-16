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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.psi.*
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNav

/**
 * The places inside an MkDocs configuration file whose value is a path.
 *
 * MkDocs does not resolve all of them against the same directory: `docs_dir` and `site_dir` are read relative
 * to the configuration file, everything else relative to `docs_dir`. Nor do they all mean the same kind of
 * target — two of them name a directory, the rest name a file. Collecting the differences here keeps the
 * three features built on top of them — the references, the gutter icons and the path check — from each
 * carrying their own idea of what a path in `mkdocs.yml` is.
 *
 * @property directory `true` if the value names a directory, `false` if it names a file
 * @property soft `true` if a target that does not exist is none of the plugin's business
 * @property relativeToDocsDir `true` if the value is resolved against `docs_dir`, `false` if against the site
 *           root
 */
enum class MkDocsPathKind(
    val directory: Boolean,
    val soft: Boolean,
    val relativeToDocsDir: Boolean,
) {

    /** The `docs_dir` key, naming the directory the documentation sources are read from. */
    DOCS_DIR(directory = true, soft = false, relativeToDocsDir = false),

    /**
     * The `site_dir` key, naming the directory the rendered site is written to.
     *
     * Soft, because that directory is build output: before the first build it simply does not exist, and a
     * site that has been built keeps it out of version control — so a fresh checkout would show a red key on
     * every open of the configuration file. Navigation and completion still work whenever the directory
     * happens to be there.
     *
     * It is also the one kind that need not stay inside the site: build output is regularly written next to
     * the checkout, above it through `..` or onto an entirely different volume by absolute path. So neither
     * the path check nor the references treat leaving the site root as a mistake here, while every other kind
     * still does — those name parts of the site, and a part of the site lies inside it.
     */
    SITE_DIR(directory = true, soft = true, relativeToDocsDir = false),

    /** The `logo` key below `theme`, naming the image shown in the header of every page. */
    LOGO(directory = false, soft = false, relativeToDocsDir = true),

    /** The `favicon` key below `theme`, naming the icon the browser shows for the site. */
    FAVICON(directory = false, soft = false, relativeToDocsDir = true),

    /** An entry of the `extra_css` sequence, naming a style sheet the built site loads. */
    EXTRA_CSS(directory = false, soft = false, relativeToDocsDir = true),

    /**
     * An entry of the `extra_javascript` sequence, naming a script the built site loads.
     *
     * Written in two shapes since MkDocs 1.6: the plain `- extra.js`, and the mapping
     * `- path: extra.js` carrying `type` and `defer` next to it. Both name the same thing, and both are
     * resolved against `docs_dir` exactly like a style sheet is.
     */
    EXTRA_JAVASCRIPT(directory = false, soft = false, relativeToDocsDir = true),

    /**
     * The `custom_dir` key below `theme`, naming the directory the theme's own templates are overridden from.
     *
     * Resolved against the configuration file rather than against `docs_dir`: the overrides are not content
     * of the site, they are what renders it, and MkDocs reads them next to `mkdocs.yml`.
     */
    CUSTOM_DIR(directory = true, soft = false, relativeToDocsDir = false),

    /** The target of a `nav` entry, naming a page of the site, at any nesting depth of the navigation. */
    NAV(directory = false, soft = false, relativeToDocsDir = true);

    /**
     * Returns the directory the value of this kind is resolved against, or `null` if it cannot be determined.
     *
     * Must be called inside a read action: finding the documentation directory reads the PSI of the
     * configuration file.
     *
     * @param project the project [configFile] belongs to
     * @param configFile the configuration file the value is written in
     */
    fun baseDirectoryOf(project: Project, configFile: VirtualFile): VirtualFile? {
        val siteRoot = configFile.parent?.takeIf { it.isValid && it.isDirectory } ?: return null
        return if (relativeToDocsDir) MkDocsLayout.docsDirOf(project, siteRoot) else siteRoot
    }

    companion object {

        /** The `logo` key MkDocs reads below `theme`. */
        private const val KEY_LOGO = "logo"

        /** The `favicon` key MkDocs reads below `theme`. */
        private const val KEY_FAVICON = "favicon"

        /** The `custom_dir` key MkDocs reads below `theme`. */
        private const val KEY_CUSTOM_DIR = "custom_dir"

        /** The `theme` key the three keys above live below. */
        private const val KEY_THEME = "theme"

        /** The top level key listing the scripts the built site loads. */
        private const val KEY_EXTRA_JAVASCRIPT = "extra_javascript"

        /** The key naming the script inside the mapping form of an `extra_javascript` entry. */
        private const val KEY_PATH = "path"

        /**
         * How many levels the search for the owning top level key climbs.
         *
         * Only `nav` nests at all, and it is bounded by [MkDocsNav.MAX_DEPTH]. The limit exists so a
         * pathologically nested file cannot turn the walk upwards into a long one.
         */
        private const val MAX_ANCESTORS = MkDocsNav.MAX_DEPTH * 2

        /**
         * Returns the kind of path [scalar] holds, or `null` if it holds none.
         *
         * A scalar qualifies only inside an MkDocs configuration file and only where MkDocs itself reads a
         * path: anything else in the file — a title, a site name, a plugin option — is text, and turning it
         * into a file reference would mark half the file red. A blank value is rejected as well, and so is a
         * target leaving the site: an address is not a path, and neither the reference nor the path check has
         * anything to say about it.
         *
         * Must be called inside a read action.
         *
         * @param scalar the scalar to inspect
         */
        fun of(scalar: YAMLScalar): MkDocsPathKind? {
            val file = scalar.containingFile?.originalFile ?: return null
            if (!MkDocsProject.isConfigFile(file.name)) return null
            if (scalar.textValue.isBlank()) return null
            if (MkDocsNav.isExternal(scalar.textValue)) return null

            return when (val parent = scalar.parent) {
                is YAMLKeyValue -> if (parent.value === scalar) kindOfKey(parent) else null
                is YAMLSequenceItem -> if (parent.value === scalar) kindOfItem(parent) else null
                else -> null
            }
        }

        /**
         * Returns the kind of the value of [keyValue], or `null` if the key does not hold a path.
         *
         * @param keyValue the pair the scalar is the value of
         */
        private fun kindOfKey(keyValue: YAMLKeyValue): MkDocsPathKind? {
            val topLevel = isTopLevel(keyValue)
            return when {
                topLevel && keyValue.keyText == MkDocsConfig.KEY_DOCS_DIR -> DOCS_DIR
                topLevel && keyValue.keyText == MkDocsConfig.KEY_SITE_DIR -> SITE_DIR
                keyValue.keyText == KEY_LOGO && isBelowTheme(keyValue) -> LOGO
                keyValue.keyText == KEY_FAVICON && isBelowTheme(keyValue) -> FAVICON
                keyValue.keyText == KEY_CUSTOM_DIR && isBelowTheme(keyValue) -> CUSTOM_DIR
                // "- path: extra.js" — the mapping form of an extra_javascript entry.
                keyValue.keyText == KEY_PATH && isBelowTopLevelSequence(keyValue, KEY_EXTRA_JAVASCRIPT) ->
                    EXTRA_JAVASCRIPT
                // "- Title: page.md" — the title is the key, the path is what the entry points at.
                !topLevel && isBelowNav(keyValue) -> NAV
                else -> null
            }
        }

        /**
         * Returns the kind of the value of [item], or `null` if the sequence does not hold paths.
         *
         * @param item the sequence entry the scalar is the value of
         */
        private fun kindOfItem(item: YAMLSequenceItem): MkDocsPathKind? {
            val owner = (item.parent as? YAMLSequence)?.parent as? YAMLKeyValue
            if (owner != null && isTopLevel(owner)) {
                when (owner.keyText) {
                    MkDocsConfig.KEY_EXTRA_CSS -> return EXTRA_CSS
                    KEY_EXTRA_JAVASCRIPT -> return EXTRA_JAVASCRIPT
                }
            }
            // "- index.md" — a navigation entry written without a title of its own.
            return if (isBelowNav(item)) NAV else null
        }

        /**
         * Returns `true` if [keyValue] is a key of a mapping that is an entry of the top level sequence [key].
         *
         * Exactly one level of nesting is accepted, which is what the mapping form of an `extra_javascript`
         * entry has: the sequence holds mappings, and the mappings hold plain keys. Anything deeper belongs
         * to something else that happens to sit in the same place.
         *
         * @param keyValue the pair to inspect
         * @param key the top level key the sequence belongs to
         */
        private fun isBelowTopLevelSequence(keyValue: YAMLKeyValue, key: String): Boolean {
            val item = (keyValue.parent as? YAMLMapping)?.parent as? YAMLSequenceItem ?: return false
            val owner = (item.parent as? YAMLSequence)?.parent as? YAMLKeyValue ?: return false
            return owner.keyText == key && isTopLevel(owner)
        }

        /**
         * Returns `true` if [keyValue] is a key of the top level mapping of the file.
         *
         * MkDocs reads its keys at the top level only, so a `docs_dir` sitting inside a plugin configuration
         * is a plugin option that happens to share the name.
         *
         * @param keyValue the pair to inspect
         */
        private fun isTopLevel(keyValue: YAMLKeyValue): Boolean =
            (keyValue.parent as? YAMLMapping)?.parent is YAMLDocument

        /**
         * Returns `true` if [keyValue] is a key of the mapping behind the top level `theme` key.
         *
         * @param keyValue the pair to inspect
         */
        private fun isBelowTheme(keyValue: YAMLKeyValue): Boolean {
            val theme = (keyValue.parent as? YAMLMapping)?.parent as? YAMLKeyValue ?: return false
            return theme.keyText == KEY_THEME && isTopLevel(theme)
        }

        /**
         * Returns `true` if [element] sits anywhere below the top level `nav` key.
         *
         * The navigation nests without a fixed depth — a section holds entries, and an entry may open another
         * section — so membership is decided by walking upwards rather than by inspecting one level.
         *
         * @param element the element to start from
         */
        private fun isBelowNav(element: com.intellij.psi.PsiElement): Boolean {
            var current = element.parent
            var steps = 0
            while (current != null && current !is YAMLDocument && steps < MAX_ANCESTORS) {
                if (current is YAMLKeyValue && current.keyText == MkDocsConfig.KEY_NAV && isTopLevel(current)) {
                    return true
                }
                current = current.parent
                steps++
            }
            return false
        }
    }
}
