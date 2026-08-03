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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Needs the platform because the configuration is read through the bundled YAML plugin's PSI.
 */
class MkDocsConfigTest : BasePlatformTestCase() {

    /**
     * Use case: the common case — `site_name` is present and becomes the module name.
     */
    fun `test reads site_name from the configuration`() {
        val file = configFile("docs/mkdocs.yml", "site_name: My Documentation\ntheme:\n  name: material\n")

        assertEquals("My Documentation", readSiteName(file))
        assertEquals("My Documentation", resolveSiteName(file))
    }

    /**
     * Use case: a quoted value — YAML quoting must not leak into the module name.
     */
    fun `test strips quotes and surrounding whitespace`() {
        val file = configFile("docs/mkdocs.yml", "site_name:   \"My Documentation\"   \n")

        assertEquals("My Documentation", readSiteName(file))
    }

    /**
     * Use case: a minimal `mkdocs.yml` without `site_name`. The site still has to get a name, which is taken
     * from the directory above the configuration file.
     */
    fun `test falls back to the directory name when site_name is missing`() {
        val file = configFile("handbook/mkdocs.yml", "theme:\n  name: material\n")

        assertNull(readSiteName(file))
        assertEquals("handbook", resolveSiteName(file))
    }

    /**
     * Use case: `site_name` present but empty. A blank name is useless as a module name, so the fall back
     * has to kick in as well.
     */
    fun `test falls back when site_name is blank`() {
        val file = configFile("handbook/mkdocs.yml", "site_name:   \n")

        assertNull(readSiteName(file))
        assertEquals("handbook", resolveSiteName(file))
    }

    /**
     * Use case: the user is in the middle of typing and the file is not valid YAML. Detection must not throw
     * — it simply falls back to the directory name.
     */
    fun `test tolerates broken yaml`() {
        val file = configFile("handbook/mkdocs.yml", "site_name: [unclosed\n  : :\n")

        assertEquals("handbook", resolveSiteName(file))
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun readSiteName(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readSiteName(project, file) }

    private fun resolveSiteName(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.resolveSiteName(project, file) }
}
