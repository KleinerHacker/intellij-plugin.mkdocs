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

package org.pcsoft.ij.plugin.mkdocs.material.schema

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Guards the one property the generated schema exists for: the values it offers are the values the Kotlin
 * model knows, so a feature flag or a colour added to `material.data` cannot be missing from completion, and a
 * value offered by completion cannot be one the theme has never heard of.
 */
class MkDocsMaterialSchemaGeneratorTest : BasePlatformTestCase() {

    /** The theme description the generator splices into the schema. */
    private val data get() = service<MkDocsMaterialDataService>()

    private val generator: MkDocsMaterialSchemaGenerator get() = MkDocsMaterialSchemaGenerator.getInstance()

    private val schema: JsonObject get() = generator.generate()

    private val definitions: JsonObject get() = schema.getAsJsonObject("definitions")

    /**
     * Use case: the IDE reads the generated schema as text. Whatever the splicing did, the result has to stay
     * a JSON document — a broken one would take completion and validation of every Material site down.
     */
    fun `test generates parsable json`() {
        val text = generator.schemaText

        val parsed = JsonParser.parseString(text)

        assertTrue("the generated schema must be a JSON object", parsed.isJsonObject)
        assertTrue("the generated schema must describe the theme", parsed.asJsonObject.has("definitions"))
    }

    /**
     * Use case: a feature flag is added to the bundled feature flag resource. Completion in `theme.features` has to
     * offer it right away, and validation must not report it as unknown, without anybody editing a resource.
     */
    fun `test offers every known feature flag`() {
        val enum = featureItems().getAsJsonArray("enum").map { it.asString }

        data.featureFlags.all.forEach { flag ->
            assertTrue("theme.features must offer '${flag.id}'", flag.id in enum)
        }
        assertEquals("theme.features must offer nothing else", data.featureFlags.all.size, enum.size)
    }

    /**
     * Use case: the reader completes a feature flag and wants to know what it does. Every offered value has to
     * carry a description, otherwise the completion list is a wall of identifiers.
     */
    fun `test describes every feature flag`() {
        val described = featureItems().getAsJsonArray("oneOf")
            .map { it.asJsonObject }
            .associate { it.get("const").asString to it.get("description").asString }

        data.featureFlags.all.forEach { flag ->
            val description = described[flag.id]
            assertNotNull("'${flag.id}' must carry a description", description)
            assertTrue("the description of '${flag.id}' must not be blank", description!!.isNotBlank())
        }
    }

    /**
     * Use case: a palette written as a single mapping. Both colour roles have to offer exactly the colours the
     * theme accepts for them — `brown` is a primary colour but never an accent, and offering it as one would
     * produce a site the theme cannot build.
     */
    fun `test offers the palette colours of the single form`() {
        assertColours("materialPaletteSingle")
    }

    /**
     * Use case: a palette written as a sequence, which is how a colour scheme toggle is configured. The values
     * offered there have to be the same ones the single form offers — a difference would be arbitrary.
     */
    fun `test offers the palette colours of the sequence form`() {
        assertColours("materialPaletteItem")
    }

    /**
     * Use case: `extra.version` belongs to *mike* and `extra.alternate` to the I18N support. Neither feature
     * is implemented yet, and constraining the keys here would make the schema report them as wrong the
     * moment somebody writes them by hand.
     */
    fun `test leaves the reserved extra keys open`() {
        val extra = definitions.getAsJsonObject("materialExtra")
        val properties = extra.getAsJsonObject("properties")

        assertFalse("extra.version must stay unconstrained", properties.has("version"))
        assertFalse("extra.alternate must stay unconstrained", properties.has("alternate"))
        assertFalse("extra must not close its key set", extra.has("additionalProperties"))
    }

    /**
     * Use case: the base MkDocs schema. It is named by a relative reference to the vendored copy — a remote
     * reference would silently drop the whole base branch — and the generator folds that copy in, because the
     * generated schema is an in-memory file without a directory a relative reference could resolve against.
     */
    fun `test folds in the vendored base schema`() {
        val resource = javaClass.getResourceAsStream(MkDocsMaterialSchemaGenerator.MATERIAL_RESOURCE)!!
            .reader(Charsets.UTF_8).use { it.readText() }

        assertTrue(
            "the shipped refinement must reference the vendored copy relatively",
            resource.contains("\"${MkDocsMaterialSchemaGenerator.BASE_FILE_NAME}\"")
        )
        assertFalse(
            "the shipped refinement must never reference a schema over http",
            Regex("\"\\\$ref\"\\s*:\\s*\"https?:").containsMatchIn(resource)
        )

        val base = schema.getAsJsonArray("allOf").first().asJsonObject
        assertTrue("the base schema must be folded in", base.getAsJsonObject("properties").has("site_name"))
        assertTrue(
            "the definitions of the base schema must be hoisted so its own references resolve",
            definitions.has("validationValues")
        )
    }

    /** The `items` schema of `theme.features` of the generated schema. */
    private fun featureItems(): JsonObject =
        definitions.getAsJsonObject("materialTheme")
            .getAsJsonObject("properties")
            .getAsJsonObject("features")
            .getAsJsonObject("items")

    /**
     * Asserts that the palette definition [name] offers exactly the known schemes and colours.
     *
     * @param name the name of the palette definition inside the generated schema
     */
    private fun assertColours(name: String) {
        val properties = definitions.getAsJsonObject(name).getAsJsonObject("properties")

        val primary = properties.getAsJsonObject("primary").getAsJsonArray("enum").map { it.asString }
        val accent = properties.getAsJsonObject("accent").getAsJsonArray("enum").map { it.asString }
        val scheme = properties.getAsJsonObject("scheme").getAsJsonArray("enum").map { it.asString }

        assertEquals("$name.primary", data.colors.primaries().map { it.id }, primary)
        assertEquals("$name.accent", data.colors.accents().map { it.id }, accent)
        assertEquals("$name.scheme", MkDocsMaterialScheme.entries.map { it.id }, scheme)
    }
}
