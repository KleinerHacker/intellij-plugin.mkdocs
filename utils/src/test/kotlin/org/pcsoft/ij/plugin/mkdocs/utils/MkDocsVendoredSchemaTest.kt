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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.google.gson.JsonParser
import junit.framework.TestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Guards the vendored copy of the SchemaStore MkDocs schema. It is refreshed by a Gradle task rather than
 * written by hand, so what has to be checked is that the snapshot in the plugin is still a usable MkDocs
 * schema — a truncated download or a moved resource would leave every refined schema without its base.
 */
class MkDocsVendoredSchemaTest : TestCase() {

    /**
     * Use case: the plugin is packaged. The vendored schema has to be on the classpath under the path the
     * generator reads it from, and it has to parse — the generator folds it into every generated schema.
     */
    fun `test the vendored base schema parses`() {
        val root = read()

        assertTrue("the vendored schema must be a JSON object", root.isJsonObject)
        assertEquals("object", root.asJsonObject.get("type").asString)
    }

    /**
     * Use case: the SchemaStore reorganises its schema. The keys MkDocs itself documents have to survive that,
     * because the refinement only adds `theme` and `extra` on top of them.
     */
    fun `test the vendored base schema describes the known keys`() {
        val properties = read().asJsonObject.getAsJsonObject("properties")

        listOf("site_name", "docs_dir", "nav", "theme", "extra").forEach { key ->
            assertTrue("the vendored schema must describe '$key'", properties.has(key))
        }
    }

    private fun read() =
        javaClass.getResourceAsStream(BASE_RESOURCE)!!
            .reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }

    private companion object {

        /**
         * The class path of the vendored copy, spelled the way every reader of it spells it.
         *
         * Refreshed by the `refreshMkDocsSchema` task of this project; a facet refining the schema reads the
         * very same path out of the packaged plugin.
         */
        const val BASE_RESOURCE = "/schema/mkdocs-1.6.json"
    }
}
