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

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the full module system on a real project on disk, in particular the cases a light fixture cannot
 * reproduce: an MkDocs site in a directory that belongs to no IntelliJ module, and a second site inside a
 * module that already represents one — for both of which a module has to be created.
 */
class MkDocsModuleDetectionIT : HeavyPlatformTestCase() {

    private val service: MkDocsModuleService
        get() = project.service<MkDocsModuleService>()

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
        assertEquals("no module to detach from", "", facet.configuration.ownerModuleName)

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
     * Use case: one module contains two documentation sites side by side. A module can carry only one MkDocs
     * facet, so the first site by path stays on the existing module and the second one gets a module of its
     * own. Its root has to leave the existing module first — a directory belongs to a single module only — so
     * it is excluded there.
     */
    fun `test creates its own module for a second site inside one module`() {
        val contentRoot = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "app")
        PsiTestUtil.addContentRoot(module, contentRoot)
        createConfig("app/docs-a/mkdocs.yml", "site_name: Alpha\n")
        createConfig("app/docs-b/mkdocs.yml", "site_name: Beta\n")

        service.sync()

        val ownerFacet = MkDocsFacet.getInstance(module)
        assertNotNull("the existing module keeps the first site", ownerFacet)
        assertEquals("Alpha", ownerFacet!!.configuration.siteName)
        assertFalse("the existing module must not be owned", ownerFacet.configuration.ownsModule)

        val beta = ModuleManager.getInstance(project).findModuleByName("Beta")
        assertNotNull("expected a module of its own for the second site", beta)
        val betaFacet = MkDocsFacet.getInstance(beta!!)
        assertNotNull(betaFacet)
        assertTrue(betaFacet!!.configuration.ownsModule)
        assertEquals(module.name, betaFacet.configuration.ownerModuleName)
        assertEquals("docs-b", ModuleRootManager.getInstance(beta).contentRoots.single().name)

        assertTrue(
            "the second site root must be excluded from the module it was taken from",
            ModuleRootManager.getInstance(module).excludeRoots.any { it.name == "docs-b" },
        )
    }

    /**
     * Use case: the second site of a module is deleted again. Its module is disposed, and the directory has to
     * be handed back to the module it was taken from — otherwise it would stay excluded and invisible to
     * indexing and search for no reason.
     */
    fun `test hands the directory back when the second site disappears`() {
        val contentRoot = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "app")
        PsiTestUtil.addContentRoot(module, contentRoot)
        createConfig("app/docs-a/mkdocs.yml", "site_name: Alpha\n")
        val betaConfig = createConfig("app/docs-b/mkdocs.yml", "site_name: Beta\n")
        service.sync()
        assertNotNull(ModuleManager.getInstance(project).findModuleByName("Beta"))

        VfsTestUtil.deleteFile(betaConfig)
        service.sync()

        assertNull(ModuleManager.getInstance(project).findModuleByName("Beta"))
        assertTrue(
            "the exclusion must be reverted",
            ModuleRootManager.getInstance(module).excludeRoots.none { it.name == "docs-b" },
        )
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
