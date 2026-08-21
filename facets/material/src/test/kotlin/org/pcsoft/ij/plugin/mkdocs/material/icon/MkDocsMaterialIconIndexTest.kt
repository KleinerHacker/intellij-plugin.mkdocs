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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationFixture

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the icon index reads out of an installed theme: the names it offers, the nesting of the sets,
 * and what it does with a site that has no installation.
 */
class MkDocsMaterialIconIndexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and with it the same index. The
        // sites of two tests share a path, so what one of them found would otherwise answer for the other.
        MkDocsMaterialInstallationFixture.uninstall(project)
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstallationFixture.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: the normal installation. Every SVG below the sets is an icon the theme can be pointed at, and
     * the name is the path below the sets without the extension — which is exactly how the configuration file
     * writes it.
     */
    fun `test offers the names the theme addresses the icons by`() {
        val root = siteWith("material/check.svg", "material/alert.svg")

        val names = runReadActionBlocking { index().names(root) }

        assertEquals(listOf("material/alert", "material/check"), names)
    }

    /**
     * Use case: the `fontawesome` set, which splits into `brands`, `regular` and `solid`. The nesting is part
     * of the name, so an icon two levels down has to come out with both levels in front of it.
     */
    fun `test reads the nested sets`() {
        val root = siteWith("fontawesome/brands/github.svg")

        val names = runReadActionBlocking { index().names(root) }

        assertEquals(listOf("fontawesome/brands/github"), names)
    }

    /**
     * Use case: resolving a name back to its file, which is what the completion needs to paint the drawing
     * next to an entry.
     */
    fun `test resolves a name to its file`() {
        val root = siteWith("material/check.svg")

        val file = runReadActionBlocking { index().find(root, "material/check") }

        assertNotNull(file)
        assertEquals("check.svg", file!!.name)
    }

    /**
     * Use case: a name that climbs out of the sets. Nothing may be resolved through it — the name comes out
     * of a configuration file, and a file the theme could never load is not what it addresses.
     */
    fun `test refuses a name climbing out of the sets`() {
        val root = siteWith("material/check.svg")

        assertNull(runReadActionBlocking { index().find(root, "../../check") })
    }

    /**
     * Use case: a fresh checkout where pip reports no installation. An empty list is the answer, and it must
     * not be an error: the completion simply offers nothing until the theme is installed.
     */
    fun `test offers nothing without an installation`() {
        val root = myFixture.addFileToProject("site/mkdocs.yml", "site_name: Handbook\n").virtualFile.parent

        assertTrue(runReadActionBlocking { index().names(root) }.isEmpty())
    }

    /**
     * Use case: the theme is installed while the project is open. Invalidating is what the VFS listener does
     * for it, and the next question has to walk the directories again rather than repeat the empty answer.
     */
    fun `test picks up an installation after being invalidated`() {
        val root = myFixture.addFileToProject("site/mkdocs.yml", "site_name: Handbook\n").virtualFile.parent
        assertTrue(runReadActionBlocking { index().names(root) }.isEmpty())

        MkDocsMaterialInstallationFixture.install(project, listOf("material/check.svg"))

        assertEquals(listOf("material/check"), runReadActionBlocking { index().names(root) })
    }

    /**
     * Installs a theme holding [icons], writes a site and returns its root directory.
     *
     * @param icons the icon files to create below the sets
     */
    private fun siteWith(vararg icons: String): VirtualFile {
        MkDocsMaterialInstallationFixture.install(project, icons.toList())
        return myFixture.addFileToProject("site/mkdocs.yml", "site_name: Handbook\n").virtualFile.parent
    }

    /**
     * Returns the index of the test project.
     */
    private fun index(): MkDocsMaterialIconIndex = project.service<MkDocsMaterialIconIndex>()
}
