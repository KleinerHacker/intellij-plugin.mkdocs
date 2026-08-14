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

package org.pcsoft.ij.plugin.mkdocs.material.data

/**
 * A colour accepted by `theme.palette.primary` or `theme.palette.accent`.
 *
 * The theme takes the colour names of the Material Design palette, written in lower case with a hyphen
 * instead of a space. [hex] is the representative shade of that colour, used to paint the swatch next to the
 * name in completion and in the settings page — it is a preview, not the exact value the theme compiles into
 * its style sheet.
 *
 * Not every colour can be used for both roles: the primary colour also has to work behind white text, which
 * is why `brown`, `grey`, `blue grey`, `black` and `white` exist for it only.
 */
enum class MkDocsMaterialColor(
    /** The identifier as it appears in the configuration file, for example `deep-purple`. */
    val id: String,
    /** A representative RGB shade of the colour, as `0xRRGGBB`. */
    val hex: Int,
    /** `true` if the colour is accepted for `theme.palette.primary`. */
    val primary: Boolean,
    /** `true` if the colour is accepted for `theme.palette.accent`. */
    val accent: Boolean
) {

    /** Material red. */
    RED("red", 0xF44336, true, true),

    /** Material pink. */
    PINK("pink", 0xE91E63, true, true),

    /** Material purple. */
    PURPLE("purple", 0x9C27B0, true, true),

    /** Material deep purple. */
    DEEP_PURPLE("deep-purple", 0x673AB7, true, true),

    /** Material indigo. */
    INDIGO("indigo", 0x3F51B5, true, true),

    /** Material blue. */
    BLUE("blue", 0x2196F3, true, true),

    /** Material light blue. */
    LIGHT_BLUE("light-blue", 0x03A9F4, true, true),

    /** Material cyan. */
    CYAN("cyan", 0x00BCD4, true, true),

    /** Material teal. */
    TEAL("teal", 0x009688, true, true),

    /** Material green. */
    GREEN("green", 0x4CAF50, true, true),

    /** Material light green. */
    LIGHT_GREEN("light-green", 0x8BC34A, true, true),

    /** Material lime. */
    LIME("lime", 0xCDDC39, true, true),

    /** Material yellow. */
    YELLOW("yellow", 0xFFEB3B, true, true),

    /** Material amber. */
    AMBER("amber", 0xFFC107, true, true),

    /** Material orange. */
    ORANGE("orange", 0xFF9800, true, true),

    /** Material deep orange. */
    DEEP_ORANGE("deep-orange", 0xFF5722, true, true),

    /** Material brown — primary only. */
    BROWN("brown", 0x795548, true, false),

    /** Material grey — primary only. */
    GREY("grey", 0x9E9E9E, true, false),

    /** Material blue grey — primary only. */
    BLUE_GREY("blue-grey", 0x607D8B, true, false),

    /** Black — primary only. */
    BLACK("black", 0x000000, true, false),

    /** White — primary only. */
    WHITE("white", 0xFFFFFF, true, false),

    /** `custom` — the colour is defined by the site itself through `--md-*` variables in its own style sheet. */
    CUSTOM("custom", 0x757575, true, true);

    companion object {

        /** The colours accepted for `theme.palette.primary`, in the order the theme documents them. */
        fun primaries(): List<MkDocsMaterialColor> = entries.filter { it.primary }

        /** The colours accepted for `theme.palette.accent`, in the order the theme documents them. */
        fun accents(): List<MkDocsMaterialColor> = entries.filter { it.accent }

        /**
         * Resolves the colour written as [id].
         *
         * @param id the identifier as it appears in the configuration file
         * @return the colour, or `null` if the theme knows no such colour
         */
        fun byId(id: String): MkDocsMaterialColor? = entries.firstOrNull { it.id == id }
    }
}
