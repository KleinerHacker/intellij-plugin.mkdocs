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

package org.pcsoft.ij.plugin.mkdocs.schema

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.json.JsonFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialColor
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureFlag
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * Builds the JSON schema an `mkdocs.yml` of a *Material for MkDocs* site is validated and completed against.
 *
 * Two halves make up the result. The shape of the `theme` and `extra` blocks is hand written and shipped as
 * `schema/material-theme.json`, because a schema is the only sensible way to describe nested mappings. The
 * *values* — the feature flags, the palette colours and the schemes — are not written there at all: they live
 * in `material.data` as Kotlin enums, and a second copy inside a resource would drift away from them the first
 * time the theme gains a flag. They are spliced into the schema here instead, so enum and schema cannot
 * disagree by construction.
 *
 * The base MkDocs schema is folded in as well. `material-theme.json` names it through a *relative* reference
 * to the vendored `schema/mkdocs-1.6.json`, but a reference of any kind is resolved against the parent
 * directory of the schema's own `VirtualFile` — and the generated schema is an in-memory file without a parent
 * directory. The generator therefore reads the vendored copy and inlines it, which is what the relative
 * reference asks for and what makes it work outside a directory. The base's own `definitions` are hoisted to
 * the root of the result so its internal `#/definitions/…` references keep resolving.
 *
 * An application service because of the [schemaFile]: the platform holds on to the file it is handed only
 * weakly, so a file created per lookup would be collected between two of them and the schema would vanish
 * from under the editor. Held here, it lives as long as the IDE does.
 */
@Service(Service.Level.APP)
class MkDocsMaterialSchemaGenerator {

    companion object {

        /** The hand written refinement of the `theme` and `extra` blocks. */
        const val MATERIAL_RESOURCE: String = "/schema/material-theme.json"

        /** The vendored copy of the SchemaStore MkDocs schema the refinement builds on. */
        const val BASE_RESOURCE: String = "/schema/mkdocs-1.6.json"

        /** The file name of the vendored copy, as the relative reference in [MATERIAL_RESOURCE] spells it. */
        const val BASE_FILE_NAME: String = "mkdocs-1.6.json"

        /** The name the generated schema is shown under, for example in the status bar widget. */
        const val SCHEMA_FILE_NAME: String = "mkdocs-material.schema.json"

        /** The application wide instance. */
        @JvmStatic
        fun getInstance(): MkDocsMaterialSchemaGenerator = service()
    }

    /**
     * The generated schema as text.
     *
     * Built once, on first access: neither the resources nor the enums can change while the IDE runs.
     */
    val schemaText: String by lazy { generate().toString() }

    /**
     * The generated schema as a file the platform can read.
     *
     * Deliberately a `val` rather than a function building a file per call — see the class documentation.
     */
    val schemaFile: VirtualFile by lazy { LightVirtualFile(SCHEMA_FILE_NAME, JsonFileType.INSTANCE, schemaText) }

    /**
     * Builds the schema from the two bundled resources and the enums of `material.data`.
     *
     * Public so a test can check the result without booting a project.
     *
     * @return the generated schema, serialised as JSON
     */
    fun generate(): JsonObject {
        val root = readResource(MATERIAL_RESOURCE)
        val definitions = root.getAsJsonObject("definitions") ?: JsonObject().also { root.add("definitions", it) }

        inlineBaseSchema(root, definitions)
        spliceFeatureFlags(definitions)
        spliceColours(definitions)

        return root
    }

    /**
     * Replaces the relative reference to the vendored base schema with the schema itself.
     *
     * The base's `$schema` and `$id` are dropped: the result declares its own, and an `$id` nested inside an
     * `allOf` branch would re-base every reference resolved below it. Its `definitions` move to [definitions]
     * so `#/definitions/…` still points at them from the root of the result.
     *
     * @param root the parsed refinement, modified in place
     * @param definitions the `definitions` object of [root]
     */
    private fun inlineBaseSchema(root: JsonObject, definitions: JsonObject) {
        val base = readResource(BASE_RESOURCE)
        base.remove("\$schema")
        base.remove("\$id")
        base.remove("title")
        base.getAsJsonObject("definitions")?.entrySet()?.forEach { (name, value) -> definitions.add(name, value) }
        base.remove("definitions")

        val allOf = root.getAsJsonArray("allOf") ?: return
        val replaced = JsonArray()
        allOf.forEach { branch ->
            val isBaseRef = branch.isJsonObject &&
                branch.asJsonObject.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString == BASE_FILE_NAME
            replaced.add(if (isBaseRef) base else branch)
        }
        root.add("allOf", replaced)
    }

    /**
     * Writes the known feature flags into `theme.features`.
     *
     * Each flag contributes twice: once to the `enum`, which is what completion offers and what validation
     * checks, and once to a `oneOf` branch carrying its description, which is what QuickDoc shows next to the
     * offered value. A plain `enum` cannot carry a description per value.
     *
     * @param definitions the `definitions` object of the schema being built
     */
    private fun spliceFeatureFlags(definitions: JsonObject) {
        val items = definitions.getAsJsonObject("materialTheme")
            ?.getAsJsonObject("properties")
            ?.getAsJsonObject("features")
            ?.getAsJsonObject("items")
            ?: return

        val values = JsonArray()
        val described = JsonArray()
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            values.add(flag.id)
            described.add(JsonObject().apply {
                addProperty("const", flag.id)
                addProperty("description", describe(flag))
            })
        }
        items.add("enum", values)
        items.add("oneOf", described)
    }

    /**
     * Writes the known palette colours and schemes into both palette forms.
     *
     * Both forms are filled: the theme accepts a single palette as a mapping and a colour scheme toggle as a
     * sequence of them, and a value offered in one form but not the other would be an arbitrary difference.
     *
     * @param definitions the `definitions` object of the schema being built
     */
    private fun spliceColours(definitions: JsonObject) {
        val primaries = idsOf(MkDocsMaterialColor.primaries().map { it.id })
        val accents = idsOf(MkDocsMaterialColor.accents().map { it.id })
        val schemes = idsOf(MkDocsMaterialScheme.entries.map { it.id })

        listOf("materialPaletteSingle", "materialPaletteItem").forEach { name ->
            val properties = definitions.getAsJsonObject(name)?.getAsJsonObject("properties") ?: return@forEach
            properties.getAsJsonObject("primary")?.add("enum", primaries.deepCopy())
            properties.getAsJsonObject("accent")?.add("enum", accents.deepCopy())
            properties.getAsJsonObject("scheme")?.add("enum", schemes.deepCopy())
        }
    }

    /**
     * The one line description of [flag], read from the message bundle.
     *
     * Falls back to the bundle key: a description is what completion shows, and a key that has not been
     * translated yet must not leave the entry blank.
     *
     * @param flag the feature flag to describe
     */
    private fun describe(flag: MkDocsMaterialFeatureFlag): String =
        MkDocsBundle.messageOrDefault(flag.descriptionKey, flag.descriptionKey) ?: flag.descriptionKey

    /**
     * Turns [ids] into a JSON array of strings.
     *
     * @param ids the identifiers as they are written in the configuration file
     */
    private fun idsOf(ids: List<String>): JsonArray = JsonArray().apply { ids.forEach { add(it) } }

    /**
     * Reads a bundled JSON resource.
     *
     * @param resource the absolute classpath path of the resource
     * @throws IllegalStateException if the resource is missing from the plugin, which is a packaging error
     */
    private fun readResource(resource: String): JsonObject {
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("Bundled schema resource '$resource' is missing")
        return stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }.asJsonObject
    }
}
