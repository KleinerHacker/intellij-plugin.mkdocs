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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacet
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers which configuration files the refined *Material for MkDocs* schema is handed to, and that it really
 * arrives: the mapping has to beat the bundled SchemaStore catalogue, which maps `mkdocs.yml` as well.
 */
class MkDocsMaterialSchemaFileProviderIT : HeavyPlatformTestCase() {

    /**
     * Use case: a site rendered with the Material theme, written as `mkdocs.yml`. The `theme` and `extra`
     * blocks carry most of its configuration, so this is the file the refined schema exists for.
     */
    fun `test claims the yml spelling of a material site`() {
        val configFile = createSite("mkdocs.yml", MATERIAL_CONFIG)
        assertNotNull("the detection must have attached the facet", MkDocsMaterialFacet.getInstance(siteModule()))

        assertTrue(provider().isAvailable(configFile))
    }

    /**
     * Use case: the same site written as `mkdocs.yaml`. MkDocs treats both spellings alike, so a site must not
     * lose its completion by being spelled the other way.
     */
    fun `test claims the yaml spelling of a material site`() {
        val configFile = createSite("mkdocs.yaml", MATERIAL_CONFIG)
        assertNotNull("the detection must have attached the facet", MkDocsMaterialFacet.getInstance(siteModule()))

        assertTrue(provider().isAvailable(configFile))
    }

    /**
     * Use case: a site on the built-in MkDocs theme. Handing it the refined schema would offer keys the theme
     * rendering the site never reads, so it has to keep the plain MkDocs schema.
     */
    fun `test claims no site without the material facet`() {
        val configFile = createSite("mkdocs.yml", "site_name: Handbook\n")
        assertNull("the site must not carry the facet", MkDocsMaterialFacet.getInstance(siteModule()))

        assertFalse(provider().isAvailable(configFile))
    }

    /**
     * Use case: the IDE asks for a schema while the project is being closed. Every path the answer is built
     * from — the file index, the facet, the configuration file — is gone by then, so the question has to be
     * answered with a plain `false` instead of an exception in the editor.
     */
    fun `test answers false for a disposed project`() {
        val configFile = createSite("mkdocs.yml", MATERIAL_CONFIG)
        val closed: Project = doCreateAndOpenProject()
        val closedProvider = MkDocsMaterialSchemaFileProvider(closed)
        PlatformTestUtil.forceCloseProjectWithoutSaving(closed)
        assertTrue("the second project must really be gone", closed.isDisposed)

        assertFalse(closedProvider.isAvailable(configFile))
    }

    /**
     * Use case: the schema a Material site is actually validated against. `mkdocs.yml` is mapped by the
     * bundled catalogue as well, and the platform does not treat that as a conflict: a file keeps *every*
     * mapping that claims it, so the refinement is added to the catalogue entry rather than replacing it.
     * The order is what matters — the provider contributed by the factory is asked before the catalogue, so
     * the refined schema is the leading one, and the catalogue entry stays as a harmless second (it declares
     * no `additionalProperties: false`, so it cannot flag the Material specific keys).
     */
    fun `test leads the mapping in front of the bundled catalogue`() {
        val configFile = createSite("mkdocs.yml", MATERIAL_CONFIG)

        val service = JsonSchemaService.Impl.get(project)
        service.reset()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val schemaFiles = service.getSchemaFilesForFile(configFile).toList()

        assertFalse("the file must be mapped to a schema at all", schemaFiles.isEmpty())
        val refined = schemaFiles.firstOrNull()
        assertEquals(
            "the refined schema must lead the mapping, but got ${schemaFiles.map { it.name }}",
            MkDocsMaterialSchemaGenerator.SCHEMA_FILE_NAME,
            refined?.name
        )
        assertTrue(
            "the refined schema must come from this plugin's provider",
            service.getSchemaProvider(refined!!) is MkDocsMaterialSchemaFileProvider
        )
    }

    /** The provider the way the factory builds it for this project. */
    private fun provider(): MkDocsMaterialSchemaFileProvider = MkDocsMaterialSchemaFileProvider(project)

    /**
     * Writes a configuration file and lets the detection pick it up, so the module and its facets exist.
     *
     * @param fileName the spelling of the configuration file to write
     * @param text the content of the configuration file
     */
    private fun createSite(fileName: String, text: String): VirtualFile {
        val configFile = VfsTestUtil.createFile(getOrCreateProjectBaseDir(), "handbook/$fileName", text)
        MkDocsModuleService.getInstance(project).sync()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        return configFile
    }

    private fun siteModule(): Module {
        val module = ModuleManager.getInstance(project).findModuleByName("Handbook")
        assertNotNull("expected a module for the site", module)
        return module!!
    }

    private companion object {

        /** A site declaring the Material theme, which is what the detection turns into the facet. */
        const val MATERIAL_CONFIG: String = "site_name: Handbook\ntheme:\n  name: material\n"
    }
}
