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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers writing and removing a plain top level key, which is how the facet editor puts `docs_dir` and
 * `site_dir` back into the configuration file.
 */
class MkDocsConfigWriterKeyTest : BasePlatformTestCase() {

    /**
     * Use case: the user renames the documentation directory of a site that never wrote `docs_dir`, because it
     * used the MkDocs default. The key has to be added, and everything the author wrote has to survive it.
     */
    fun `test adds a missing key`() {
        val file = configFile("add/mkdocs.yml", "site_name: Handbook\n")

        setKey(file, MkDocsConfig.KEY_DOCS_DIR, "documentation")

        assertEquals("documentation", readDocsDir(file))
        assertTrue("site_name must survive", text(file).contains("site_name: Handbook"))
    }

    /**
     * Use case: the site already names its documentation directory. Only the value may change — the key has to
     * keep the place its author gave it, so the comment above it still belongs to it.
     */
    fun `test replaces an existing value in place`() {
        val file = configFile(
            "replace/mkdocs.yml",
            "site_name: Handbook\n# where the sources live\ndocs_dir: sources\nsite_url: https://x.y\n",
        )

        setKey(file, MkDocsConfig.KEY_DOCS_DIR, "documentation")

        val content = text(file)
        assertEquals("documentation", readDocsDir(file))
        assertTrue("the comment must stay above the key", content.contains("# where the sources live\ndocs_dir:"))
        assertTrue("site_url must survive", content.contains("site_url: https://x.y"))
    }

    /**
     * Use case: a directory name YAML would read as something else — a value carrying a colon. It has to be
     * quoted, otherwise the written file no longer parses as the author's site.
     */
    fun `test quotes a value yaml would read differently`() {
        val file = configFile("quote/mkdocs.yml", "site_name: Handbook\n")

        setKey(file, MkDocsConfig.KEY_SITE_DIR, "out: docs")

        assertEquals("out: docs", readSiteDir(file))
    }

    /**
     * Use case: the user sets the output directory back to what MkDocs assumes anyway. The key is taken out
     * instead of repeating the default, and the rest of the file is untouched.
     */
    fun `test removes a key`() {
        val file = configFile("remove/mkdocs.yml", "site_name: Handbook\nsite_dir: out/docs\nsite_url: https://x.y\n")

        removeKey(file, MkDocsConfig.KEY_SITE_DIR)

        assertNull(runReadActionBlocking { MkDocsConfig.readSiteDir(project, file) })
        assertTrue("site_url must survive", text(file).contains("site_url: https://x.y"))
    }

    /**
     * Use case: a key that is not there is removed. There is nothing to take out, and the file must come out of
     * it byte for byte the same.
     */
    fun `test leaves a file without the key untouched`() {
        val file = configFile("untouched/mkdocs.yml", "site_name: Handbook\n")

        removeKey(file, MkDocsConfig.KEY_SITE_DIR)

        assertEquals("site_name: Handbook\n", text(file))
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun setKey(file: VirtualFile, key: String, value: String) =
        WriteCommandAction.runWriteCommandAction(project) {
            MkDocsConfigWriter.setScalarKey(project, file, key, value)
        }

    private fun removeKey(file: VirtualFile, key: String) =
        WriteCommandAction.runWriteCommandAction(project) {
            MkDocsConfigWriter.removeKey(project, file, key)
        }

    private fun readDocsDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readDocsDir(project, file) }

    private fun readSiteDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readSiteDir(project, file) }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }
}
