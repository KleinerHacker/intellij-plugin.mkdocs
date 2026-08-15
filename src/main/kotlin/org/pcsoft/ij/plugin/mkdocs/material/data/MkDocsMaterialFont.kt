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
 * that is not listed stays perfectly usable; the settings page falls back to the entry marked [custom] for it.
 *
 * The list of fonts is not written in code: it is read from `facets/material/fonts.yaml` by
 * [MkDocsMaterialDataService].
 *
 * @property id the family name as it is written into the configuration file and requested from Google Fonts,
 *   empty for the [custom] placeholder
 * @property text `true` if the font is offered for `theme.font.text`
 * @property code `true` if the font is offered for `theme.font.code`
 * @property custom `true` for the placeholder standing in for a family outside the curated list, or for none
 *   at all — `theme.font: false` switches font loading off entirely
 */
data class MkDocsMaterialFont(
    val id: String,
    val text: Boolean,
    val code: Boolean,
    val custom: Boolean = false
)
