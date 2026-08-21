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
 *
 * The list of colours is not written in code: it is read from `material/spec/colors.yaml` by
 * [MkDocsMaterialDataService].
 *
 * @property id the identifier as it appears in the configuration file, for example `deep-purple`
 * @property hex a representative RGB shade of the colour, as `0xRRGGBB`
 * @property descriptionKey the bundle key of the one line description shown in QuickDoc and behind the offered
 *   value
 * @property primary `true` if the colour is accepted for `theme.palette.primary`
 * @property accent `true` if the colour is accepted for `theme.palette.accent`
 * @property custom `true` for the `custom` placeholder, whose colour the site defines through `--md-*` variables
 */
data class MkDocsMaterialColor(
    val id: String,
    val hex: Int,
    val descriptionKey: String,
    val primary: Boolean,
    val accent: Boolean,
    val custom: Boolean = false
)
