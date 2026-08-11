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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.VfsTestUtil

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers which files the plugin claims a JSON schema for. The point of the provider is a single gap in the
 * bundled catalogue, so claiming more than that would be a bug.
 */
class MkDocsSchemaFileProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsSchemaFileProvider()

    /**
     * Use case: the spelling the bundled catalogue does not cover. Without this mapping the file is edited
     * without completion and without validation, although MkDocs treats it exactly like `mkdocs.yml`.
     */
    fun `test claims the yaml spelling`() {
        val file = VfsTestUtil.createFile(myFixture.tempDirFixture.getFile(".")!!, "mkdocs.yaml", "site_name: x\n")

        assertTrue(provider.isAvailable(file))
    }

    /**
     * Use case: the spelling the catalogue already maps. Claiming it as well would put two mappings on one
     * file, competing with each other for no gain.
     */
    fun `test leaves the yml spelling to the catalogue`() {
        val file = VfsTestUtil.createFile(myFixture.tempDirFixture.getFile(".")!!, "mkdocs.yml", "site_name: x\n")

        assertFalse(provider.isAvailable(file))
    }

    /**
     * Use case: any other YAML file in the project. The MkDocs schema says nothing about it, and applying it
     * would report every key as unknown.
     */
    fun `test claims no other file`() {
        val file = VfsTestUtil.createFile(myFixture.tempDirFixture.getFile(".")!!, "config.yaml", "a: b\n")

        assertFalse(provider.isAvailable(file))
    }

    /**
     * Use case: the schema itself is not shipped but named as a remote source, so it stays the same schema
     * `mkdocs.yml` is validated against instead of ageing inside the plugin.
     */
    fun `test names the schema as a remote source`() {
        assertNull(provider.schemaFile)
        assertEquals("https://www.schemastore.org/mkdocs-1.6.json", provider.remoteSource)
    }
}
