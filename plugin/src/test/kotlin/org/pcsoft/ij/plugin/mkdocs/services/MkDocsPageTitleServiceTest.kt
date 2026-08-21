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

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Needs the platform for the virtual file system and the document of an open editor.
 */
class MkDocsPageTitleServiceTest : BasePlatformTestCase() {

    /**
     * Use case: the ordinary page of a site. Its first heading is what the navigation tree labels the node
     * with when `nav` gives no title.
     */
    fun `test reads the heading of a page`() {
        val file = createFile("index.md", "# Getting started\n\nText.\n")

        assertEquals("Getting started", service().titleOf(file))
    }

    /**
     * Use case: a page without any heading. The label then has to come from the file name, so the node is
     * still recognisable.
     */
    fun `test falls back to the file name without a heading`() {
        val file = createFile("install.md", "Just a paragraph.\n")

        assertEquals("install", service().titleOf(file))
    }

    /**
     * Use case: the heading of a page is renamed and the file is saved. The tree asks again afterwards and
     * has to get the new title rather than the remembered one.
     */
    fun `test re-reads a page after its content changed`() {
        val file = createFile("page.md", "# Before\n")
        assertEquals("Before", service().titleOf(file))

        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "# After\n") }

        assertEquals("After", service().titleOf(file))
    }

    /**
     * Use case: the heading is being edited and the editor holds unsaved changes. MkDocs would not see them
     * yet, but the user does, so the tree follows the editor rather than the file on disk.
     */
    fun `test prefers the unsaved content of an open document`() {
        val file = createFile("draft.md", "# On disk\n")
        assertEquals("On disk", service().titleOf(file))

        val document = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(file) }
        assertNotNull(document)
        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("# In the editor\n") }

        assertEquals("In the editor", service().titleOf(file))
    }

    /**
     * Use case: asking twice for the same unchanged page. The second answer comes out of the cache, which
     * shows itself in the answer staying the same after the cache is dropped and the file re-read.
     */
    fun `test answers repeatedly and consistently`() {
        val file = createFile("stable.md", "# Stable\n")

        assertEquals("Stable", service().titleOf(file))
        assertEquals("Stable", service().titleOf(file))

        service().clear()

        assertEquals("Stable", service().titleOf(file))
    }

    /**
     * Use case: a generated page of a megabyte or more. Reading it would cost more than the title is worth,
     * so the file name is used without opening the file.
     */
    fun `test does not read a file beyond the size limit`() {
        val padding = "x".repeat(MkDocsPageTitleService.MAX_FILE_LENGTH.toInt())
        val file = createFile("generated.md", "# Would be the title\n$padding")

        assertEquals("generated", service().titleOf(file))
    }

    /**
     * Use case: a directory reaching the lookup by accident, for instance through a `nav` entry pointing at
     * one. A directory has no heading, so its name is the answer.
     */
    fun `test falls back for a directory`() {
        val directory = myFixture.tempDirFixture.findOrCreateDir("section")

        assertEquals("section", service().titleOf(directory))
    }

    /**
     * Returns the service under test.
     */
    private fun service(): MkDocsPageTitleService = project.service<MkDocsPageTitleService>()

    /**
     * Creates a file in the temporary directory of the fixture and returns it.
     *
     * The file is created through the temporary directory rather than through the fixture's project helper,
     * because the latter builds PSI — and with it a document, which would hide the path reading straight
     * from the virtual file system.
     *
     * @param path the path of the file, relative to the temporary directory
     * @param text the content of the file
     */
    private fun createFile(path: String, text: String): VirtualFile =
        myFixture.tempDirFixture.createFile(path, text)
}
