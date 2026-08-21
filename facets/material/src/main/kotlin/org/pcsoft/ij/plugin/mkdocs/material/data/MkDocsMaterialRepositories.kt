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
 * The Markdown extensions read from `material/spec/markdown-extensions.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.extensions]; never constructed by a caller.
 *
 * @property all every known extension, in the order of the resource
 */
class MkDocsMarkdownExtensions internal constructor(
    val all: List<MkDocsMarkdownExtension>,
    private val featureFlags: () -> MkDocsMaterialFeatureFlags
) {

    private val byId: Map<String, MkDocsMarkdownExtension> = all.associateBy { it.id }

    /**
     * Resolves the extension written as [id].
     *
     * @param id the identifier as it appears under `markdown_extensions`
     * @return the extension, or `null` if it is none the theme cares about
     */
    fun byId(id: String): MkDocsMarkdownExtension? = byId[id]

    /** Every extension that is merely a recommendation, in the order of the resource. */
    fun recommended(): List<MkDocsMarkdownExtension> =
        all.filter { it.level == MkDocsMarkdownExtensionLevel.RECOMMENDED }

    /**
     * The extensions the given configuration actually forces.
     *
     * Nothing is required by the theme as such: an extension only shows up here when a feature listed in
     * `theme.features` needs it, or when the site uses the icon shorthands the theme provides. An empty
     * configuration therefore yields an empty set, and the annotator reporting missing extensions as an
     * error stays quiet on a site that uses none of it.
     *
     * @param flags the identifiers currently listed in `theme.features`; unknown ones are ignored
     * @param usesIcons `true` if the site writes icon or emoji shorthands such as `:material-check:`
     * @return the extensions that have to be listed under `markdown_extensions`
     */
    fun requiredBy(flags: Set<String>, usesIcons: Boolean): Set<MkDocsMarkdownExtension> {
        val result = linkedSetOf<MkDocsMarkdownExtension>()
        val knownFlags = featureFlags()
        flags.forEach { flagId ->
            knownFlags.byId(flagId)?.requiredExtensions?.forEach { extensionId ->
                byId(extensionId)?.let(result::add)
            }
        }
        if (usesIcons) all.filter { it.iconShorthand }.forEach(result::add)
        return result
    }
}

/**
 * The feature flags read from `material/spec/feature-flags.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.featureFlags]; never constructed by a caller.
 *
 * @property all every known flag, in the order of the resource
 */
class MkDocsMaterialFeatureFlags internal constructor(
    val all: List<MkDocsMaterialFeatureFlag>
) {

    private val byId: Map<String, MkDocsMaterialFeatureFlag> = all.associateBy { it.id }

    /**
     * The symmetric closure of every declared [MkDocsMaterialFeatureFlag.conflictsWith] relation.
     *
     * The resource names one side of a pair only; this map holds both, so a caller never has to know which
     * side declared it.
     */
    private val conflicts: Map<String, Set<MkDocsMaterialFeatureFlag>> = buildConflicts()

    /**
     * Resolves the flag written as [id].
     *
     * @param id the identifier as it appears in `theme.features`
     * @return the flag, or `null` if the theme knows no such flag
     */
    fun byId(id: String): MkDocsMaterialFeatureFlag? = byId[id]

    /** The identifiers of every known flag, in the order of the resource. */
    fun allIds(): List<String> = all.map { it.id }

    /**
     * The flags [flag] cannot be combined with, in both directions.
     *
     * @param flag the flag to look up
     * @return the conflicting flags, empty if the flag clashes with nothing
     */
    fun conflictsOf(flag: MkDocsMaterialFeatureFlag): Set<MkDocsMaterialFeatureFlag> =
        conflicts[flag.id] ?: emptySet()

    private fun buildConflicts(): Map<String, Set<MkDocsMaterialFeatureFlag>> {
        val collected = mutableMapOf<String, MutableSet<MkDocsMaterialFeatureFlag>>()
        all.forEach { flag ->
            flag.conflictsWith.forEach { otherId ->
                val other = byId[otherId] ?: return@forEach
                collected.getOrPut(flag.id) { linkedSetOf() } += other
                collected.getOrPut(other.id) { linkedSetOf() } += flag
            }
        }
        return collected
    }
}

/**
 * The `extra` keys read from `material/spec/extra-keys.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.extraKeys]; never constructed by a caller.
 *
 * @property all every `extra` key the theme owns, in the order the settings page and the schema list them
 * @property reserved the `extra` keys other features own, which this model must never describe or overwrite
 */
class MkDocsMaterialExtraKeys internal constructor(
    val all: List<MkDocsMaterialExtraField>,
    val reserved: Set<String>
) {

    /**
     * Resolves the `extra` key written as [name].
     *
     * @param name the key as it appears below `extra`
     * @return the field, or `null` if the key belongs to another feature or to the site itself
     */
    fun byName(name: String): MkDocsMaterialExtraField? = all.firstOrNull { it.name == name }

    companion object {

        /** The top level key every field lives under. */
        const val ROOT: String = "extra"
    }
}

/**
 * The curated font families read from `material/spec/fonts.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.fonts]; never constructed by a caller.
 *
 * @property all every listed family, in the order of the resource
 */
class MkDocsMaterialFonts internal constructor(
    val all: List<MkDocsMaterialFont>
) {

    /** The placeholder standing in for a family outside the curated list, or `null` if the resource lacks it. */
    val custom: MkDocsMaterialFont? = all.firstOrNull { it.custom }

    /** The fonts offered for `theme.font.text`, the [custom] placeholder last. */
    fun textFonts(): List<MkDocsMaterialFont> = all.filter { it.text }

    /** The fonts offered for `theme.font.code`, the [custom] placeholder last. */
    fun codeFonts(): List<MkDocsMaterialFont> = all.filter { it.code }

    /**
     * Resolves the font family written as [id].
     *
     * @param id the family name as it appears in the configuration file
     * @return the font, or `null` if it is not part of the curated list
     */
    fun byId(id: String): MkDocsMaterialFont? = all.firstOrNull { !it.custom && it.id == id }
}

/**
 * The palette colours read from `material/spec/colors.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.colors]; never constructed by a caller.
 *
 * @property all every listed colour, in the order of the resource
 */
class MkDocsMaterialColors internal constructor(
    val all: List<MkDocsMaterialColor>
) {

    /** The `custom` placeholder, or `null` if the resource lacks it. */
    val custom: MkDocsMaterialColor? = all.firstOrNull { it.custom }

    /** The colours accepted for `theme.palette.primary`, in the order the theme documents them. */
    fun primaries(): List<MkDocsMaterialColor> = all.filter { it.primary }

    /** The colours accepted for `theme.palette.accent`, in the order the theme documents them. */
    fun accents(): List<MkDocsMaterialColor> = all.filter { it.accent }

    /**
     * Resolves the colour written as [id].
     *
     * @param id the identifier as it appears in the configuration file
     * @return the colour, or `null` if the theme knows no such colour
     */
    fun byId(id: String): MkDocsMaterialColor? = all.firstOrNull { it.id == id }
}

/**
 * The `--md-*` custom properties read from `material/spec/css-variables.yaml`.
 *
 * Obtained from [MkDocsMaterialDataService.cssVariables]; never constructed by a caller.
 *
 * @property all every listed property, in the order of the resource
 */
class MkDocsMaterialCssVariables internal constructor(
    val all: List<MkDocsMaterialCssVariable>
) {

    private val byName: Map<String, MkDocsMaterialCssVariable> = all.associateBy { it.name }

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

    companion object {

        /** The prefix every custom property of the theme carries. */
        const val PREFIX: String = "--md-"
    }
}
