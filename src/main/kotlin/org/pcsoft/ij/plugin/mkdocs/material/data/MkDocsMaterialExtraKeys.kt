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

/**
 * The `extra` keys owned by the *Material for MkDocs* theme.
 *
 * `extra.version` and `extra.alternate` are deliberately absent: the first belongs to *mike*, the second to
 * the I18N support, and both are described by the features of their own. They are listed in [RESERVED] so a
 * schema built from this model leaves them alone instead of reporting them as unknown.
 */
object MkDocsMaterialExtraKeys {

    /** The top level key every field below lives under. */
    const val ROOT: String = "extra"

    /** `extra.social` — the icons linking to the accounts of the project, shown in the footer. */
    val SOCIAL: MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        "social", MkDocsMaterialExtraValueKind.SEQUENCE_OF_MAPPINGS, "material.extra.social",
        listOf(
            MkDocsMaterialExtraField("icon", MkDocsMaterialExtraValueKind.STRING, "material.extra.social.icon"),
            MkDocsMaterialExtraField("link", MkDocsMaterialExtraValueKind.STRING, "material.extra.social.link"),
            MkDocsMaterialExtraField("name", MkDocsMaterialExtraValueKind.STRING, "material.extra.social.name")
        )
    )

    /** `extra.analytics` — the site analytics provider and the feedback widget below each page. */
    val ANALYTICS: MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        "analytics", MkDocsMaterialExtraValueKind.MAPPING, "material.extra.analytics",
        listOf(
            MkDocsMaterialExtraField("provider", MkDocsMaterialExtraValueKind.STRING, "material.extra.analytics.provider"),
            MkDocsMaterialExtraField("property", MkDocsMaterialExtraValueKind.STRING, "material.extra.analytics.property"),
            MkDocsMaterialExtraField(
                "feedback", MkDocsMaterialExtraValueKind.MAPPING, "material.extra.analytics.feedback",
                listOf(
                    MkDocsMaterialExtraField(
                        "title", MkDocsMaterialExtraValueKind.STRING, "material.extra.analytics.feedback.title"
                    ),
                    MkDocsMaterialExtraField(
                        "ratings", MkDocsMaterialExtraValueKind.SEQUENCE_OF_MAPPINGS,
                        "material.extra.analytics.feedback.ratings",
                        listOf(
                            MkDocsMaterialExtraField(
                                "icon", MkDocsMaterialExtraValueKind.STRING,
                                "material.extra.analytics.feedback.ratings.icon"
                            ),
                            MkDocsMaterialExtraField(
                                "name", MkDocsMaterialExtraValueKind.STRING,
                                "material.extra.analytics.feedback.ratings.name"
                            ),
                            MkDocsMaterialExtraField(
                                "data", MkDocsMaterialExtraValueKind.STRING,
                                "material.extra.analytics.feedback.ratings.data"
                            ),
                            MkDocsMaterialExtraField(
                                "note", MkDocsMaterialExtraValueKind.STRING,
                                "material.extra.analytics.feedback.ratings.note"
                            )
                        )
                    )
                )
            )
        )
    )

    /** `extra.consent` — the cookie consent dialogue shown before analytics are loaded. */
    val CONSENT: MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        "consent", MkDocsMaterialExtraValueKind.MAPPING, "material.extra.consent",
        listOf(
            MkDocsMaterialExtraField("title", MkDocsMaterialExtraValueKind.STRING, "material.extra.consent.title"),
            MkDocsMaterialExtraField(
                "description", MkDocsMaterialExtraValueKind.STRING, "material.extra.consent.description"
            ),
            MkDocsMaterialExtraField(
                "cookies", MkDocsMaterialExtraValueKind.MAP_OF_STRINGS, "material.extra.consent.cookies"
            ),
            MkDocsMaterialExtraField(
                "actions", MkDocsMaterialExtraValueKind.SEQUENCE_OF_STRINGS, "material.extra.consent.actions"
            )
        )
    )

    /** `extra.generator` — whether the "Made with Material for MkDocs" notice is rendered. */
    val GENERATOR: MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        "generator", MkDocsMaterialExtraValueKind.BOOLEAN, "material.extra.generator"
    )

    /** `extra.status` — the badges a page may carry through its `status` front matter key. */
    val STATUS: MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        "status", MkDocsMaterialExtraValueKind.MAP_OF_STRINGS, "material.extra.status"
    )

    /** Every `extra` key the theme owns, in the order the settings page and the schema list them. */
    val ALL: List<MkDocsMaterialExtraField> = listOf(SOCIAL, ANALYTICS, CONSENT, GENERATOR, STATUS)

    /** The `extra` keys other features own, which this model must never describe or overwrite. */
    val RESERVED: Set<String> = setOf("version", "alternate")

    /**
     * Resolves the `extra` key written as [name].
     *
     * @param name the key as it appears below `extra`
     * @return the field, or `null` if the key belongs to another feature or to the site itself
     */
    fun byName(name: String): MkDocsMaterialExtraField? = ALL.firstOrNull { it.name == name }
}
