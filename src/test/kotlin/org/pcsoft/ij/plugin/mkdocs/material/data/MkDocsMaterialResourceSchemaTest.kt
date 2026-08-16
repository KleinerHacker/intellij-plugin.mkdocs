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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Every YAML resource under `facets/material` names a JSON schema next to it, which is what validates the
 * file while it is being edited in the IDE. That schema is only worth having if it is actually true of the
 * file, so this test checks each resource against its own schema on every build — an entry that has drifted
 * away from the schema would otherwise only show up as a red squiggle nobody happens to look at.
 *
 * The check covers the subset of JSON Schema these files use: `type`, `required`, `properties`,
 * `additionalProperties`, `enum`, `pattern`, `items` and local `$ref` into `$defs`. Anything beyond that is
 * deliberately not implemented rather than silently accepted, so a schema using it fails here loudly.
 */
class MkDocsMaterialResourceSchemaTest {

    private val yaml = ObjectMapper(YAMLFactory())

    /**
     * Use case: `markdown-extensions.yaml` is what the annotator, the quick fix and the settings page read.
     */
    @Test
    fun `the markdown extensions resource matches its schema`() {
        validate("markdown-extensions")
    }

    /**
     * Use case: `feature-flags.yaml` fills `theme.features` in completion and in the generated JSON schema.
     */
    @Test
    fun `the feature flags resource matches its schema`() {
        validate("feature-flags")
    }

    /**
     * Use case: `extra-keys.yaml` describes a tree, so its schema refers to itself — the one place a broken
     * `$ref` would go unnoticed the longest.
     */
    @Test
    fun `the extra keys resource matches its schema`() {
        validate("extra-keys")
    }

    /**
     * Use case: `fonts.yaml` fills the two font drop downs of the settings page.
     */
    @Test
    fun `the fonts resource matches its schema`() {
        validate("fonts")
    }

    /**
     * Use case: `colors.yaml` fills the palette drop downs, and its `hex` values are painted as swatches.
     */
    @Test
    fun `the colors resource matches its schema`() {
        validate("colors")
    }

    /**
     * Use case: a resource that lost its schema comment would still load, but would stop being validated in
     * the IDE — the drift this whole test guards against would then start unnoticed.
     */
    @Test
    fun `every resource points at its schema`() {
        listOf("markdown-extensions", "feature-flags", "extra-keys", "fonts", "colors").forEach { name ->
            val text = resource("/facets/material/$name.yaml").reader(Charsets.UTF_8).use { it.readText() }
            assertTrue(
                "$name.yaml does not name its schema",
                text.lineSequence().first().trim() == "# yaml-language-server: \$schema=./$name.schema.json"
            )
        }
    }

    /**
     * Reads the resource pair written as [name] and checks the document against the schema.
     *
     * @param name the base name shared by the `.yaml` and the `.schema.json`
     */
    private fun validate(name: String) {
        val document = resource("/facets/material/$name.yaml").use { yaml.readTree(it) }
        val schema = resource("/facets/material/$name.schema.json").reader(Charsets.UTF_8)
            .use { JsonParser.parseReader(it) }.asJsonObject
        val errors = mutableListOf<String>()
        check(toGson(document), schema, schema, "$name.yaml", errors)
        if (errors.isNotEmpty()) {
            fail("${errors.size} schema violation(s):\n" + errors.joinToString("\n"))
        }
    }

    /**
     * Checks [value] against [schema], collecting every violation into [errors] instead of failing at the
     * first one — a single run should report everything that is wrong with a file.
     *
     * @param value the part of the document being checked
     * @param schema the schema describing it
     * @param root the root schema, which local `$ref`s are resolved against
     * @param path where in the document [value] sits, for the message
     * @param errors the violations found so far
     */
    private fun check(
        value: JsonElement,
        schema: JsonObject,
        root: JsonObject,
        path: String,
        errors: MutableList<String>
    ) {
        schema.get("\$ref")?.asString?.let { ref ->
            val target = resolve(ref, root)
            if (target == null) errors += "$path: unresolvable \$ref '$ref'" else check(
                value,
                target,
                root,
                path,
                errors
            )
            return
        }

        when (schema.get("type")?.asString) {
            "array" -> {
                if (!value.isJsonArray) {
                    errors += "$path: expected an array"
                    return
                }
                val items = schema.getAsJsonObject("items") ?: return
                value.asJsonArray.forEachIndexed { index, element ->
                    check(element, items, root, "$path[$index]", errors)
                }
            }

            "object" -> {
                if (!value.isJsonObject) {
                    errors += "$path: expected an object"
                    return
                }
                val obj = value.asJsonObject
                val properties = schema.getAsJsonObject("properties") ?: JsonObject()
                schema.getAsJsonArray("required")?.forEach { required ->
                    if (!obj.has(required.asString)) errors += "$path: missing required '${required.asString}'"
                }
                if (schema.get("additionalProperties")?.asBoolean == false) {
                    obj.keySet().filterNot { properties.has(it) }.forEach {
                        errors += "$path: unknown property '$it'"
                    }
                }
                obj.entrySet().forEach { (key, child) ->
                    properties.getAsJsonObject(key)?.let { check(child, it, root, "$path.$key", errors) }
                }
            }

            "string" -> {
                val text = value.asJsonPrimitiveOrNull()?.takeIf { it.isString }?.asString
                if (text == null) {
                    errors += "$path: expected a string"
                    return
                }
                schema.getAsJsonArray("enum")?.let { allowed ->
                    if (allowed.none { it.asString == text }) {
                        errors += "$path: '$text' is not one of ${allowed.map { it.asString }}"
                    }
                }
                schema.get("pattern")?.asString?.let { pattern ->
                    if (!Regex(pattern).matches(text)) errors += "$path: '$text' does not match /$pattern/"
                }
            }

            "boolean" -> {
                if (value.asJsonPrimitiveOrNull()?.isBoolean != true) errors += "$path: expected a boolean"
            }

            null -> Unit

            else -> errors += "$path: the test does not implement type '${schema.get("type")?.asString}'"
        }
    }

    /** Resolves a local `$ref` such as `#/$defs/field` against [root], or `null` if it points nowhere. */
    private fun resolve(ref: String, root: JsonObject): JsonObject? {
        if (!ref.startsWith("#/")) return null
        var current: JsonObject? = root
        ref.removePrefix("#/").split("/").forEach { segment ->
            current = current?.getAsJsonObject(segment.replace("~1", "/").replace("~0", "~"))
        }
        return current
    }

    private fun JsonElement.asJsonPrimitiveOrNull() =
        if (isJsonPrimitive) asJsonPrimitive else null

    /** Turns what Jackson read into the Gson tree the checks work on, so both files are compared as one shape. */
    private fun toGson(node: com.fasterxml.jackson.databind.JsonNode): JsonElement =
        JsonParser.parseString(node.toString())

    private fun resource(path: String) =
        requireNotNull(javaClass.getResourceAsStream(path)) { "bundled resource $path is missing" }
}
