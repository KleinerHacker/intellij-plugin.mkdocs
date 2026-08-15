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
 * One entry of the `theme.features` sequence of the *Material for MkDocs* theme.
 *
 * Everything a feature needs to be offered, documented and validated hangs off it: the section it belongs to,
 * its description, whether it needs an Insiders build, which other flags it depends on or clashes with, and
 * which Markdown extensions it forces to be enabled.
 *
 * The list of flags is not written in code: it is read from `facets/material/feature-flags.yaml` by
 * [MkDocsMaterialDataService].
 *
 * [conflictsWith] holds what the resource declares, which names one side of a pair only. Ask
 * [MkDocsMaterialFeatureFlags.conflictsOf] for the symmetric closure instead of reading this property when
 * the direction of the declaration must not matter.
 *
 * @property id the identifier as it appears in `theme.features`, for example `navigation.tabs`
 * @property group the section of the page this flag changes
 * @property descriptionKey the bundle key of the one line description shown in QuickDoc, completion and the
 *   settings page
 * @property insiders `true` if the flag only has an effect in an *Insiders* build of the theme
 * @property requires the [id]s of the flags that have to be enabled as well for this flag to do anything
 * @property conflictsWith the [id]s of the flags this one cannot be combined with, as declared
 * @property requiredExtensions the [MkDocsMarkdownExtension.id]s that have to be enabled for this flag to work
 */
data class MkDocsMaterialFeatureFlag(
    val id: String,
    val group: MkDocsMaterialFeatureGroup,
    val descriptionKey: String,
    val insiders: Boolean = false,
    val requires: Set<String> = emptySet(),
    val conflictsWith: Set<String> = emptySet(),
    val requiredExtensions: Set<String> = emptySet()
)
