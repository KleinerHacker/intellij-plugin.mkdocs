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

package org.pcsoft.ij.plugin.mkdocs.material.config

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialKeys
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * The values `theme.palette` of *Material for MkDocs* is written with, and where each of them stands.
 *
 * Four keys carry a value of a closed set: the ground the palette is painted on, the two colours, and the
 * media query the palette is chosen by. Several features ask what a caret or a written value stands for —
 * the completion putting a swatch on a colour, the quick documentation explaining it, the inspection judging
 * a media query — and they must never answer it differently. So the places live here and nowhere else.
 *
 * Decided on the whole path, never on the name of the nearest key: `primary` means a colour of the theme
 * below `theme.palette`, and means whatever a plugin makes of it anywhere else. The sequence a palette of
 * several entries is written as contributes nothing to that path, so both shapes of `theme.palette` — the
 * single mapping and the sequence — reach the same answer.
 *
 * A media query is compared without its white space and without regard to case, the same way
 * [MkDocsMaterialConfig] compares the `media` of a palette it reads: `(prefers-color-scheme:dark)` and
 * `(prefers-color-scheme: dark)` are one query, and a browser reads them as one.
 */
object MkDocsMaterialPaletteKeys {

    /** The key naming the media query of a palette entry. */
    const val KEY_MEDIA: String = "media"

    /** The key naming the ground a palette is painted on. */
    const val KEY_SCHEME: String = "scheme"

    /** The key naming the primary colour of a palette. */
    const val KEY_PRIMARY: String = "primary"

    /** The key naming the accent colour of a palette. */
    const val KEY_ACCENT: String = "accent"

    /** The dotted paths of the keys, by the role the value below them plays. */
    private val PATHS: Map<String, Role> = mapOf(
        pathOf(KEY_MEDIA) to Role.MEDIA,
        pathOf(KEY_SCHEME) to Role.SCHEME,
        pathOf(KEY_PRIMARY) to Role.PRIMARY,
        pathOf(KEY_ACCENT) to Role.ACCENT,
    )

    /**
     * The media queries the theme is built around, in the order the completion offers them.
     *
     * Light first, dark second, the system preference last — which is the order the theme's own documentation
     * writes a palette in, and the order an author reads them in.
     */
    val MEDIA_QUERIES: List<Media> = listOf(
        Media(MkDocsMaterialConfig.MEDIA_LIGHT, "material.palette.media.light"),
        Media(MkDocsMaterialConfig.MEDIA_DARK, "material.palette.media.dark"),
        Media(MkDocsMaterialConfig.MEDIA_SYSTEM, "material.palette.media.system"),
    )

    /**
     * Returns what the value [position] sits in stands for, or `null` if it is no value of a palette.
     *
     * The caller must hold a read action.
     *
     * @param position the element to judge, a scalar of the configuration file or an element inside one
     */
    fun roleOf(position: PsiElement): Role? {
        val keyValue = position.parentOfType<YAMLKeyValue>(withSelf = true) ?: return null
        // The key itself is not its value: a key being typed is completed by the schema, not from here. Told
        // apart by the range rather than by the type of the element — the key of a YAML mapping entry is a
        // leaf below the entry and no scalar of its own, so asking for the scalar above an element finds
        // nothing there and would let the key through.
        val value = keyValue.value ?: return null
        if (!value.textRange.contains(position.textRange.startOffset)) return null
        return PATHS[MkDocsMaterialKeys.pathOf(keyValue)]
    }

    /**
     * Returns `true` if [position] sits in the value of a `media` of a palette.
     *
     * @param position the element to judge
     */
    fun isMediaValue(position: PsiElement): Boolean = roleOf(position) == Role.MEDIA

    /**
     * Returns `true` if the dotted [path] addresses the media query of a palette.
     *
     * @param path the dotted path of a key, as [MkDocsMaterialKeys.pathOf] builds it
     */
    fun isMediaPath(path: String): Boolean = PATHS[path] == Role.MEDIA

    /**
     * Returns `true` if [value] is one of the three queries the theme is built around.
     *
     * @param value the query as the file writes it
     */
    fun isKnownMedia(value: String): Boolean = MEDIA_QUERIES.any { matches(it.query, value) }

    /**
     * Returns `true` if [one] and [other] are the same media query.
     *
     * @param one a query
     * @param other the query to compare it against
     */
    fun matches(one: String, other: String): Boolean = normalize(one) == normalize(other)

    /**
     * Returns the colour [value] names, if it is one the theme accepts in [role].
     *
     * The role is asked as well, because the two colour keys do not accept the same set: the primary colour
     * also has to sit behind white text, so `brown`, `grey`, `blue grey`, `black` and `white` exist for it
     * only. Offering the wrong set would name a colour the theme refuses.
     *
     * @param role what the value stands for, as [roleOf] answered
     * @param value the identifier as the file writes it
     * @return the colour, or `null` if the role is no colour or the identifier names none
     */
    fun colorOf(role: Role?, value: String): MkDocsMaterialColor? {
        val colors = service<MkDocsMaterialDataService>().colors
        return when (role) {
            Role.PRIMARY -> colors.primaries()
            Role.ACCENT -> colors.accents()
            else -> return null
        }.firstOrNull { it.id == value.trim() }
    }

    /**
     * Returns the scheme [value] names, or `null` if the theme knows no such scheme.
     *
     * @param value the identifier as the file writes it
     */
    fun schemeOf(value: String): MkDocsMaterialScheme? = MkDocsMaterialScheme.byId(value.trim())

    /**
     * Returns the dotted path of [key] below `theme.palette`.
     *
     * @param key the name of the key
     */
    private fun pathOf(key: String): String = "${MkDocsMaterialConfig.KEY_PALETTE}.$key"

    /**
     * Returns [value] in the shape two media queries are compared in.
     *
     * @param value the query as it was written
     */
    private fun normalize(value: String): String = value.filterNot { it.isWhitespace() }.lowercase()

    /**
     * What the value below one of the keys of a palette stands for.
     */
    enum class Role {

        /** The media query the palette is chosen by. */
        MEDIA,

        /** The ground the palette is painted on. */
        SCHEME,

        /** The primary colour of the palette. */
        PRIMARY,

        /** The accent colour of the palette. */
        ACCENT,
    }

    /**
     * One of the media queries a palette is chosen by.
     *
     * @property query the query as it is written into the configuration file
     * @property descriptionKey the key of the bundle describing when the palette applies
     */
    data class Media(val query: String, val descriptionKey: String)
}
