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

import com.google.gson.JsonParser

/**
 * The part of the page a [MkDocsMaterialCssVariable] paints.
 */
enum class MkDocsMaterialCssVariableGroup(
    /** The bundle key of the heading shown for this group. */
    val titleKey: String
) {

    /** The primary colour of the site: header, sidebar highlights, active entries. */
    PRIMARY("material.cssGroup.primary"),

    /** The accent colour of the site: hovered links, focused controls. */
    ACCENT("material.cssGroup.accent"),

    /** The foreground and background of the page itself. */
    DEFAULT("material.cssGroup.default"),

    /** Code blocks and their syntax highlighting. */
    CODE("material.cssGroup.code"),

    /** The typeset content: links, marked text, key caps, tables. */
    TYPESET("material.cssGroup.typeset"),

    /** Call-out blocks. */
    ADMONITION("material.cssGroup.admonition"),

    /** The footer at the bottom of the page. */
    FOOTER("material.cssGroup.footer"),

    /** The elevation shadows. */
    SHADOW("material.cssGroup.shadow"),

    /** The font families the theme loads. */
    FONT("material.cssGroup.font")
}

/**
 * One `--md-*` CSS custom property of the *Material for MkDocs* theme.
 *
 * Overriding these in a style sheet listed under `extra_css` is the supported way of theming a site beyond
 * the palette, so the plugin offers them in completion and documents them in QuickDoc.
 *
 * @property name the property including the two leading hyphens, for example `--md-primary-fg-color`
 * @property group the part of the page the property paints
 * @property isColor `true` if the value is a colour, which makes the IDE offer a colour swatch for it
 * @property descriptionKey the bundle key of the one line description
 */
data class MkDocsMaterialCssVariable(
    val name: String,
    val group: MkDocsMaterialCssVariableGroup,
    val isColor: Boolean,
    val descriptionKey: String
)

/**
 * The bundled list of [MkDocsMaterialCssVariable]s.
 *
 * The theme has far more custom properties than an enum should carry, so they live in the bundled resource
 * `material/css-variables.json` and are parsed once, on first access.
 */
object MkDocsMaterialCssVariables {

    /** The classpath resource the list is read from. */
    const val RESOURCE: String = "/material/css-variables.json"

    /** The prefix every custom property of the theme carries. */
    const val PREFIX: String = "--md-"

    /** Every known custom property, in the order of the resource. */
    val all: List<MkDocsMaterialCssVariable> by lazy { parse() }

    private val byName: Map<String, MkDocsMaterialCssVariable> by lazy { all.associateBy { it.name } }

    /**
     * Resolves the custom property written as [name].
     *
     * @param name the property including the two leading hyphens
     * @return the property, or `null` if the theme defines no such variable
     */
    fun byName(name: String): MkDocsMaterialCssVariable? = byName[name]

    /**
     * The custom properties belonging to [group], in the order of the resource.
     *
     * @param group the part of the page to list the properties of
     */
    fun byGroup(group: MkDocsMaterialCssVariableGroup): List<MkDocsMaterialCssVariable> =
        all.filter { it.group == group }

    /**
     * Reads the bundled resource.
     *
     * An entry that names an unknown group or misses a field is dropped rather than failing the whole list —
     * the resource is shipped with the plugin, and a broken entry must not take the completion down with it.
     */
    private fun parse(): List<MkDocsMaterialCssVariable> {
        val stream = MkDocsMaterialCssVariables::class.java.getResourceAsStream(RESOURCE) ?: return emptyList()
        val root = stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        if (!root.isJsonArray) return emptyList()
        return root.asJsonArray.mapNotNull { element ->
            val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = entry.get("name")?.asString ?: return@mapNotNull null
            val group = entry.get("group")?.asString
                ?.let { groupName -> MkDocsMaterialCssVariableGroup.entries.firstOrNull { it.name == groupName } }
                ?: return@mapNotNull null
            val descriptionKey = entry.get("descriptionKey")?.asString ?: return@mapNotNull null
            MkDocsMaterialCssVariable(
                name = name,
                group = group,
                isColor = entry.get("isColor")?.asBoolean == true,
                descriptionKey = descriptionKey
            )
        }
    }
}
