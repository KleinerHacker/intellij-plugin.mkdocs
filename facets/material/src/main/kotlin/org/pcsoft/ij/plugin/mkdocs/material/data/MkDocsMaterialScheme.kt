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
 * A value of `theme.palette.scheme` — the light or dark ground the palette is painted on.
 */
enum class MkDocsMaterialScheme(
    /** The identifier as it appears in `theme.palette.scheme`. */
    val id: String,
    /** The bundle key of the label shown for this scheme. */
    val titleKey: String
) {

    /** `default` — the light scheme, which is what the theme uses when the key is absent. */
    DEFAULT("default", "material.scheme.default"),

    /** `slate` — the dark scheme. */
    SLATE("slate", "material.scheme.slate");

    companion object {

        /**
         * Resolves the scheme written as [id].
         *
         * @param id the identifier as it appears in the configuration file
         * @return the scheme, or `null` if the theme knows no such scheme
         */
        fun byId(id: String): MkDocsMaterialScheme? = entries.firstOrNull { it.id == id }
    }
}
