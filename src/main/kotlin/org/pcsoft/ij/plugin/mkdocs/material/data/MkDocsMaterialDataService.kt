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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * The description of the *Material for MkDocs* theme the plugin validates and completes against.
 *
 * What the theme offers — its feature flags, the Markdown extensions those flags force, the keys below
 * `extra`, the curated fonts and the palette colours — is not written in Kotlin. It lives in the bundled
 * YAML resources under `facets/material`, each one described by a JSON schema next to it, so the data can be
 * corrected and extended without touching code and is validated while being edited.
 *
 * The Jackson used to read them is the one the platform bundles as an IDE jar (see `bundledLibrary` in
 * `build.gradle.kts`); nothing is packaged into the plugin.
 *
 * Every resource is read on first access and then kept: the files ship with the plugin and cannot change
 * while the IDE runs. Reading fails softly — a resource that is missing or broken yields an empty list and is
 * logged, because a defect in a bundled file must not take completion down with it.
 *
 * Obtained as an application service:
 * ```
 * val colors = service<MkDocsMaterialDataService>().colors.primaries()
 * ```
 */
@Service(Service.Level.APP)
class MkDocsMaterialDataService {

    private val mapper: ObjectMapper by lazy {
        ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            // A resource may name a field a shipped build does not know yet; that must not fail the read.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    /** The Markdown extensions the theme builds upon. */
    val extensions: MkDocsMarkdownExtensions by lazy {
        val entries = readList<ExtensionEntry>(RESOURCE_EXTENSIONS).map { entry ->
            MkDocsMarkdownExtension(
                id = entry.id,
                pipPackage = entry.pipPackage,
                level = entry.level,
                descriptionKey = entry.descriptionKey,
                docUrl = entry.docUrl,
                defaultOptions = entry.defaultOptions.map { it.key to it.value },
                iconShorthand = entry.iconShorthand
            )
        }
        MkDocsMarkdownExtensions(entries) { featureFlags }
    }

    /** The entries offered for `theme.features`. */
    val featureFlags: MkDocsMaterialFeatureFlags by lazy {
        val entries = readList<FeatureFlagEntry>(RESOURCE_FEATURE_FLAGS).map { entry ->
            MkDocsMaterialFeatureFlag(
                id = entry.id,
                group = entry.group,
                descriptionKey = entry.descriptionKey,
                insiders = entry.insiders,
                requires = entry.requires.toSet(),
                conflictsWith = entry.conflictsWith.toSet(),
                requiredExtensions = entry.requiredExtensions.toSet()
            )
        }
        MkDocsMaterialFeatureFlags(entries)
    }

    /** The keys below `extra` the theme reads. */
    val extraKeys: MkDocsMaterialExtraKeys by lazy {
        val document = read<ExtraKeysDocument>(RESOURCE_EXTRA_KEYS)
        MkDocsMaterialExtraKeys(
            all = document?.fields.orEmpty().map(::toExtraField),
            reserved = document?.reserved.orEmpty().toSet()
        )
    }

    /** The curated font families offered for `theme.font`. */
    val fonts: MkDocsMaterialFonts by lazy {
        MkDocsMaterialFonts(readList<FontEntry>(RESOURCE_FONTS).map { MkDocsMaterialFont(it.id, it.text, it.code, it.custom) })
    }

    /** The colours offered for `theme.palette`. */
    val colors: MkDocsMaterialColors by lazy {
        val entries = readList<ColorEntry>(RESOURCE_COLORS).mapNotNull { entry ->
            val hex = parseHex(entry.hex) ?: run {
                thisLogger().warn("Colour '${entry.id}' in $RESOURCE_COLORS carries a malformed hex value '${entry.hex}'")
                return@mapNotNull null
            }
            MkDocsMaterialColor(entry.id, hex, entry.primary, entry.accent, entry.custom)
        }
        MkDocsMaterialColors(entries)
    }

    private fun toExtraField(entry: ExtraFieldEntry): MkDocsMaterialExtraField = MkDocsMaterialExtraField(
        name = entry.name,
        kind = entry.kind,
        descriptionKey = entry.descriptionKey,
        children = entry.children.map(::toExtraField)
    )

    private fun parseHex(text: String): Int? =
        text.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)

    private inline fun <reified T> readList(resource: String): List<T> =
        read<List<T>>(resource).orEmpty()

    private inline fun <reified T> read(resource: String): T? {
        val stream = javaClass.getResourceAsStream(resource)
        if (stream == null) {
            thisLogger().error("Bundled resource $resource is missing")
            return null
        }
        return try {
            stream.use { mapper.readValue(it, jacksonTypeRef<T>()) }
        } catch (e: Exception) {
            thisLogger().error("Bundled resource $resource cannot be read", e)
            null
        }
    }

    companion object {

        /** The classpath resource describing the Markdown extensions. */
        const val RESOURCE_EXTENSIONS: String = "/facets/material/markdown-extensions.yaml"

        /** The classpath resource describing the feature flags. */
        const val RESOURCE_FEATURE_FLAGS: String = "/facets/material/feature-flags.yaml"

        /** The classpath resource describing the keys below `extra`. */
        const val RESOURCE_EXTRA_KEYS: String = "/facets/material/extra-keys.yaml"

        /** The classpath resource describing the curated fonts. */
        const val RESOURCE_FONTS: String = "/facets/material/fonts.yaml"

        /** The classpath resource describing the palette colours. */
        const val RESOURCE_COLORS: String = "/facets/material/colors.yaml"
    }
}

// The shapes below mirror the YAML resources one to one, which is what keeps Jackson out of the model types
// the rest of the plugin works with. They are what the JSON schemas next to the resources describe.

private class ExtensionEntry(
    val id: String,
    val pipPackage: String? = null,
    val level: MkDocsMarkdownExtensionLevel,
    val descriptionKey: String,
    val docUrl: String,
    val defaultOptions: List<OptionEntry> = emptyList(),
    val iconShorthand: Boolean = false
)

private class OptionEntry(val key: String, val value: String)

private class FeatureFlagEntry(
    val id: String,
    val group: MkDocsMaterialFeatureGroup,
    val descriptionKey: String,
    val insiders: Boolean = false,
    val requires: List<String> = emptyList(),
    val conflictsWith: List<String> = emptyList(),
    val requiredExtensions: List<String> = emptyList()
)

private class ExtraKeysDocument(
    val reserved: List<String> = emptyList(),
    val fields: List<ExtraFieldEntry> = emptyList()
)

private class ExtraFieldEntry(
    val name: String,
    val kind: MkDocsMaterialExtraValueKind,
    val descriptionKey: String,
    val children: List<ExtraFieldEntry> = emptyList()
)

private class FontEntry(
    val id: String,
    val text: Boolean,
    val code: Boolean,
    val custom: Boolean = false
)

private class ColorEntry(
    val id: String,
    val hex: String,
    val primary: Boolean,
    val accent: Boolean,
    val custom: Boolean = false
)
