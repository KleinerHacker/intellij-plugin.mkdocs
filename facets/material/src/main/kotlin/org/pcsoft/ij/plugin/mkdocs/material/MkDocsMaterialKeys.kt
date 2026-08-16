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

package org.pcsoft.ij.plugin.mkdocs.material

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialExtraKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig

/**
 * The keys of an MkDocs configuration file that exist only because the site is built with
 * *Material for MkDocs*.
 *
 * Both places marking the origin of a setting ask here: the completion, which puts the theme's icon next to an
 * entry, and the inlay hint, which puts it in front of the key in the editor. Neither may decide on its own
 * what belongs to the theme — a key marked in one place and left plain in the other would say that the two
 * mean different things.
 *
 * Only the keys MkDocs itself does not read are listed. `theme.name` names the theme and is read by MkDocs;
 * `theme.logo`, `theme.favicon` and `theme.custom_dir` are part of the theme contract of MkDocs and work with
 * any theme; `markdown_extensions` is a top level key of MkDocs. Marking those would claim the theme owns what
 * it merely uses.
 */
object MkDocsMaterialKeys {

    /** The key of the icons the theme puts on its own elements, below `theme`. */
    const val KEY_ICON: String = "${MkDocsConfig.KEY_THEME}.icon"

    /**
     * The dotted paths below `theme` the theme alone reads.
     *
     * `theme.language` is left out on purpose: MkDocs passes the key to whatever theme is configured, and the
     * built in themes read it as well.
     */
    private val THEME_PATHS: Set<String> = setOf(
        MkDocsMaterialConfig.KEY_FEATURES,
        MkDocsMaterialConfig.KEY_PALETTE,
        MkDocsMaterialConfig.KEY_FONT,
        MkDocsMaterialConfig.KEY_DIRECTION,
        KEY_ICON,
    )

    /** How many levels [pathOf] climbs before it gives up; the deepest path of the theme needs four. */
    private const val MAX_ANCESTORS: Int = 8

    /**
     * Returns `true` if [keyValue] is one of the keys the theme brings along.
     *
     * Judged on the whole path, not on the name: `features` means the feature flags of the theme below
     * `theme`, and means nothing at all below a key of some plugin.
     *
     * The caller must hold a read action.
     *
     * @param keyValue the pair to judge
     */
    fun isMaterialKey(keyValue: YAMLKeyValue): Boolean = isMaterialPath(pathOf(keyValue))

    /**
     * Returns `true` if the dotted [path] addresses one of the keys the theme brings along.
     *
     * @param path the dotted path of a key, as [pathOf] builds it
     */
    fun isMaterialPath(path: String): Boolean =
        path in THEME_PATHS || (path.startsWith("${MkDocsMaterialExtraKeys.ROOT}.") && isExtraKey(path))

    /**
     * Returns `true` if [name] is an identifier only the theme knows.
     *
     * The fallback of the completion for the entries it cannot place by position — the schema offers the
     * feature flags and the Markdown extensions of the theme in more than one shape, and an entry reaching the
     * popup through one of them still comes from the theme.
     *
     * The colours, the fonts and the keys below `extra` are deliberately not asked here. Their identifiers are
     * ordinary words — `blue`, `social` — that mean something entirely different elsewhere in the file, and
     * every place they are legitimately offered at is a Material position anyway.
     *
     * @param name the identifier a completion entry inserts
     */
    fun isMaterialId(name: String): Boolean {
        val data = service<MkDocsMaterialDataService>()
        return data.featureFlags.byId(name) != null || data.extensions.byId(name) != null
    }

    /**
     * Returns `true` if the completion entry [lookupString] at [position] comes from the theme.
     *
     * Three cases, in this order: the entry is a value below a key of the theme; the entry is the name of such
     * a key, being typed inside the mapping it belongs to; the entry inserts an identifier only the theme
     * knows. An entry matching none of them is none of the theme's business and stays as it is.
     *
     * The caller must hold a read action.
     *
     * @param position the element completion was invoked at
     * @param lookupString what the entry inserts
     */
    fun isMaterialLookup(position: PsiElement, lookupString: String): Boolean {
        val enclosing = position.parentOfType<YAMLKeyValue>() ?: return isMaterialId(lookupString)
        val path = pathOf(enclosing)

        // Everything below a key of the theme is the theme's as well — `theme.palette.primary` exists for the
        // same reason `theme.palette` does. The hint marks the key alone, the completion cannot: the entry
        // sits at the deepest level, and that is where the popup opens.
        if (isMaterialPath(path) || isBelowMaterialPath(path)) return true

        // A key being typed carries the dummy identifier of the completion. Depending on what is already
        // written, that dummy is a key-value of its own or is not parsed as one at all, so both the path and
        // the path around it have to be tried with the entry appended.
        if (isMaterialPath("$path.$lookupString")) return true
        val around = path.substringBeforeLast('.', "")
        if (around.isNotEmpty() && isMaterialPath("$around.$lookupString")) return true

        return isMaterialId(lookupString)
    }

    /**
     * Returns `true` if [path] lies below one of the keys the theme brings along.
     *
     * @param path the dotted path of a key, as [pathOf] builds it
     */
    private fun isBelowMaterialPath(path: String): Boolean =
        generateSequence(path) { it.substringBeforeLast('.', "").takeIf(String::isNotEmpty) }
            .drop(1)
            .any { isMaterialPath(it) }

    /**
     * Returns the dotted path of [keyValue], as far up as the top level key.
     *
     * The sequences on the way contribute nothing: `extra.social` is what an entry of that sequence is *in*,
     * and the index of the entry says nothing about what the key means.
     *
     * The caller must hold a read action.
     *
     * @param keyValue the pair to describe
     */
    fun pathOf(keyValue: YAMLKeyValue): String {
        val segments = mutableListOf<String>()
        var current: YAMLKeyValue? = keyValue
        var steps = 0
        while (current != null && steps < MAX_ANCESTORS) {
            segments += current.keyText.trim()
            current = current.parentOfType<YAMLKeyValue>()
            steps++
        }
        return segments.reversed().joinToString(".")
    }

    /**
     * Returns `true` if [path] addresses a key below `extra` the theme reads.
     *
     * Only the key directly below `extra` counts. What lies further down describes the value of that key, and
     * marking every level of it would put the icon on half the file.
     *
     * @param path the dotted path of a key, starting with `extra`
     */
    private fun isExtraKey(path: String): Boolean {
        val segments = path.split('.')
        if (segments.size != 2) return false
        return service<MkDocsMaterialDataService>().extraKeys.byName(segments[1]) != null
    }
}
