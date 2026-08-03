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

package org.pcsoft.ij.plugin.mkdocs.services

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the full module system on a real project on disk, in particular the case a light fixture cannot
 * reproduce: an MkDocs site in a directory that belongs to no IntelliJ module, for which a module has to be
 * created.
 */
class MkDocsModuleDetectionIT : HeavyPlatformTestCase() {

    private val service: MkDocsModuleService
        get() = MkDocsModuleService.getInstance(project)

    /**
     * Use case: a plain documentation folder is checked out into a project without any build system. There is
     * no module covering it, so the plugin creates one — named after `site_name` and rooted at the directory
     * above `mkdocs.yml` — and marks it with the MkDocs facet.
     */
    fun `test creates a module for a site outside every module`() {
        createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")

        service.sync()

        val module = ModuleManager.getInstance(project).findModuleByName("Handbook")
        assertNotNull("expected a module named after site_name", module)

        val facet = MkDocsFacet.getInstance(module!!)
        assertNotNull(facet)
        assertTrue("a module created by the plugin must be marked as owned", facet!!.configuration.ownsModule)
        assertEquals("Handbook", facet.configuration.siteName)

        val contentRoots = ModuleRootManager.getInstance(module).contentRoots
        assertEquals(1, contentRoots.size)
        assertEquals("handbook", contentRoots.single().name)
    }

    /**
     * Use case: a monorepo with two documentation sites. Each site root becomes its own module, so features
     * can later be enabled per site.
     */
    fun `test creates one module per site`() {
        createConfig("guide/mkdocs.yml", "site_name: Guide\n")
        createConfig("reference/mkdocs.yaml", "site_name: Reference\n")

        service.sync()

        assertEquals(
            listOf("Guide", "Reference"),
            service.getMkDocsModules().map { it.name }.sorted(),
        )
    }

    /**
     * Use case: two sites without `site_name` in directories of the same name at different levels. The module
     * names would collide, so the second one gets a suffix instead of failing.
     */
    fun `test makes colliding module names unique`() {
        createConfig("a/docs/mkdocs.yml", "theme:\n  name: material\n")
        createConfig("b/docs/mkdocs.yml", "theme:\n  name: material\n")

        service.sync()

        val names = service.getMkDocsModules().map { it.name }.sorted()
        assertEquals(2, names.size)
        assertEquals("docs", names[0])
        assertEquals("docs~2", names[1])
    }

    /**
     * Use case: the documentation folder is removed from the working copy. The module the plugin created for
     * it has no reason to exist any more and must be disposed, not left behind as a dangling entry.
     */
    fun `test disposes an owned module when the site disappears`() {
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")
        service.sync()
        assertNotNull(ModuleManager.getInstance(project).findModuleByName("Handbook"))

        VfsTestUtil.deleteFile(configFile)
        service.sync()

        assertNull(ModuleManager.getInstance(project).findModuleByName("Handbook"))
        assertTrue(service.getMkDocsModules().isEmpty())
    }

    private fun createConfig(relativePath: String, text: String): VirtualFile =
        VfsTestUtil.createFile(getOrCreateProjectBaseDir(), relativePath, text)
}
