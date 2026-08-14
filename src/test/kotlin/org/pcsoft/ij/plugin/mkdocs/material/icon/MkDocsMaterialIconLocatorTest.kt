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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.settings.MkDocsSettings
import java.nio.file.Files

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers where the icon sets of the theme are looked for: the layouts of a virtual environment, the
 * configured override, and the case of a site that has no installation at all.
 */
class MkDocsMaterialIconLocatorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            MkDocsSettings.getInstance(project).iconPath = ""
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a checkout on Windows whose virtual environment lies in the usual `.venv` next to the site.
     * The packages sit at a fixed path there, and `mkdocs-material` is one of them.
     */
    fun `test finds the sets in a windows virtual environment`() {
        val root = siteWith(".venv/Lib/site-packages/material/templates/.icons/material/check.svg")

        assertNotNull(locate(root))
    }

    /**
     * Use case: the same checkout on Linux or macOS. The packages lie below a directory named after the
     * version of the interpreter, which cannot be known in advance and has to be listed.
     */
    fun `test finds the sets in a posix virtual environment`() {
        val root = siteWith("venv/lib/python3.12/site-packages/material/templates/.icons/material/check.svg")

        assertNotNull(locate(root))
    }

    /**
     * Use case: a site whose environment is one of the other names that are in circulation. All four are
     * searched, because which one a project uses is a habit rather than a rule.
     */
    fun `test searches every environment name`() {
        val root = siteWith(".virtualenv/Lib/site-packages/material/templates/.icons/material/check.svg")

        assertNotNull(locate(root))
    }

    /**
     * Use case: an interpreter somewhere the search cannot reach — a system wide installation, a container
     * mount. The configured path is the answer, and it wins over everything the search would find.
     */
    fun `test prefers the configured path`() {
        val root = siteWith(".venv/Lib/site-packages/material/templates/.icons/material/check.svg")
        // A real directory on disk rather than one of the fixture: the setting names a path of the local
        // file system, which is what an interpreter outside the project has, and the in-memory file system
        // of the fixture is not reachable through one.
        val directory = Files.createTempDirectory("mkdocs-icons")
        Files.createDirectories(directory.resolve("material"))
        Files.writeString(directory.resolve("material/check.svg"), "<svg/>")
        val elsewhere = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
        assertNotNull(elsewhere)
        MkDocsSettings.getInstance(project).iconPath = elsewhere!!.path

        assertEquals(elsewhere, locate(root))
    }

    /**
     * Use case: a configured path that no longer exists, because the environment was moved. Falling back to
     * the search is more useful than finding nothing at all.
     */
    fun `test falls back to the search for a configured path that is gone`() {
        val root = siteWith(".venv/Lib/site-packages/material/templates/.icons/material/check.svg")
        MkDocsSettings.getInstance(project).iconPath = "/does/not/exist"

        assertNotNull(locate(root))
    }

    /**
     * Use case: a fresh checkout whose environment has not been created yet. Nothing is found, and that is a
     * normal state rather than a fault — everything built on the index has to cope with it.
     */
    fun `test finds nothing without an installation`() {
        val root = myFixture.addFileToProject("site/mkdocs.yml", "site_name: Handbook\n").virtualFile.parent

        assertNull(locate(root))
    }

    /**
     * Writes a site holding [path] and returns its root directory.
     *
     * @param path the file to create below the site root
     */
    private fun siteWith(path: String): VirtualFile {
        myFixture.addFileToProject("site/$path", "<svg/>")
        return myFixture.addFileToProject("site/mkdocs.yml", "site_name: Handbook\n").virtualFile.parent
    }

    /**
     * Runs the locator on [siteRoot] inside a read action.
     *
     * @param siteRoot the directory holding `mkdocs.yml`
     */
    private fun locate(siteRoot: VirtualFile): VirtualFile? =
        runReadActionBlocking { MkDocsMaterialIconLocator.locate(project, siteRoot) }
}
