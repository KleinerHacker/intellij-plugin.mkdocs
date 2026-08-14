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
 * A font offered for `theme.font.text` and `theme.font.code`.
 *
 * The theme loads whatever is named here from Google Fonts, so any family hosted there is valid — this list
 * is a curated selection for completion and for the drop downs of the settings page, not a closed set. A font
 * that is not listed stays perfectly usable; the settings page falls back to [CUSTOM] for it.
 */
enum class MkDocsMaterialFont(
    /** The family name as it is written into the configuration file and requested from Google Fonts. */
    val id: String,
    /** `true` if the font is offered for `theme.font.text`. */
    val text: Boolean,
    /** `true` if the font is offered for `theme.font.code`. */
    val code: Boolean
) {

    //region text fonts
    /** Roboto — the default of the theme for body text. */
    ROBOTO("Roboto", true, false),

    /** Open Sans. */
    OPEN_SANS("Open Sans", true, false),

    /** Lato. */
    LATO("Lato", true, false),

    /** Montserrat. */
    MONTSERRAT("Montserrat", true, false),

    /** Nunito. */
    NUNITO("Nunito", true, false),

    /** Nunito Sans. */
    NUNITO_SANS("Nunito Sans", true, false),

    /** Inter. */
    INTER("Inter", true, false),

    /** Poppins. */
    POPPINS("Poppins", true, false),

    /** Raleway. */
    RALEWAY("Raleway", true, false),

    /** Merriweather. */
    MERRIWEATHER("Merriweather", true, false),

    /** Noto Sans. */
    NOTO_SANS("Noto Sans", true, false),

    /** Noto Serif. */
    NOTO_SERIF("Noto Serif", true, false),

    /** Source Sans 3. */
    SOURCE_SANS_3("Source Sans 3", true, false),

    /** Work Sans. */
    WORK_SANS("Work Sans", true, false),

    /** Ubuntu. */
    UBUNTU("Ubuntu", true, false),
    //endregion

    //region code fonts
    /** Roboto Mono — the default of the theme for code. */
    ROBOTO_MONO("Roboto Mono", false, true),

    /** Fira Code. */
    FIRA_CODE("Fira Code", false, true),

    /** JetBrains Mono. */
    JETBRAINS_MONO("JetBrains Mono", false, true),

    /** Source Code Pro. */
    SOURCE_CODE_PRO("Source Code Pro", false, true),

    /** IBM Plex Mono. */
    IBM_PLEX_MONO("IBM Plex Mono", false, true),

    /** Inconsolata. */
    INCONSOLATA("Inconsolata", false, true),

    /** Space Mono. */
    SPACE_MONO("Space Mono", false, true),

    /** Ubuntu Mono. */
    UBUNTU_MONO("Ubuntu Mono", false, true),
    //endregion

    /**
     * A family that is not part of the curated list, or none at all.
     *
     * Written with an empty [id]: `theme.font: false` switches font loading off entirely, and a family typed
     * by hand is kept as it stands.
     */
    CUSTOM("", true, true);

    companion object {

        /** The fonts offered for `theme.font.text`, [CUSTOM] last. */
        fun textFonts(): List<MkDocsMaterialFont> = entries.filter { it.text }

        /** The fonts offered for `theme.font.code`, [CUSTOM] last. */
        fun codeFonts(): List<MkDocsMaterialFont> = entries.filter { it.code }

        /**
         * Resolves the font family written as [id].
         *
         * @param id the family name as it appears in the configuration file
         * @return the font, or `null` if it is not part of the curated list
         */
        fun byId(id: String): MkDocsMaterialFont? = entries.firstOrNull { it != CUSTOM && it.id == id }
    }
}
