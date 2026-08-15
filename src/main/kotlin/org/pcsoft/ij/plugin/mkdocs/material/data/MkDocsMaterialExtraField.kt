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
 * The shape of a value below `extra`.
 */
enum class MkDocsMaterialExtraValueKind {

    /** A plain text value. */
    STRING,

    /** `true` or `false`. */
    BOOLEAN,

    /** A mapping whose keys are described by the children of the field. */
    MAPPING,

    /** A sequence of mappings, each shaped like the children of the field. */
    SEQUENCE_OF_MAPPINGS,

    /** A sequence of plain text values. */
    SEQUENCE_OF_STRINGS,

    /** A mapping with keys chosen by the author and text values. */
    MAP_OF_STRINGS
}

/**
 * One key below `extra` the *Material for MkDocs* theme reads.
 *
 * Described as data rather than as a class per key: the description is what the generated JSON schema and the
 * completion need, and neither cares about behaviour.
 *
 * The keys are not written in code: they are read from `facets/material/extra-keys.yaml` by
 * [MkDocsMaterialDataService].
 *
 * @property name the key as it is written in the configuration file
 * @property kind the shape of the value
 * @property descriptionKey the bundle key of the one line description
 * @property children the keys below this one, empty unless [kind] describes a mapping
 */
data class MkDocsMaterialExtraField(
    val name: String,
    val kind: MkDocsMaterialExtraValueKind,
    val descriptionKey: String,
    val children: List<MkDocsMaterialExtraField> = emptyList()
)
