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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the intention that creates the target of a path value: which values it offers itself for, where it
 * puts what it creates, and whether it creates a directory or a file.
 */
class MkDocsCreatePathIntentionTest : BasePlatformTestCase() {

    private val intention = MkDocsCreatePathIntention()

    /**
     * Use case: a navigation entry pointing at a page that has not been written yet — the normal way an
     * outline of a manual is put down before its content. The page is created below `docs_dir`, which is
     * where MkDocs looks for it, not next to the configuration file.
     */
    fun `test creates a missing page below the documentation directory`() {
        val root = siteWith("site_name: Handbook\nnav:\n  - Intro: intro.md\n")

        invokeAt("intro.md")

        assertNotNull("the page belongs below docs_dir", root.findFileByRelativePath("docs/intro.md"))
        assertNull("nothing belongs next to mkdocs.yml", root.findChild("intro.md"))
    }

    /**
     * Use case: a page in a section of its own. The directories along the way do not exist either, and
     * creating only the last one would leave the value pointing nowhere just as before.
     */
    fun `test creates the directories along the way`() {
        val root = siteWith("site_name: Handbook\nnav:\n  - Guide: guide/setup.md\n")

        invokeAt("guide/setup.md")

        val page = root.findFileByRelativePath("docs/guide/setup.md")
        assertNotNull(page)
        assertFalse("a page is a file", page!!.isDirectory)
    }

    /**
     * Use case: `docs_dir` naming a directory that is not there. What is created has to be a directory —
     * MkDocs reads the sources of the site from it, and an empty file of that name would break the build in
     * a way that is harder to understand than the missing directory was.
     */
    fun `test creates a directory for a directory key`() {
        val root = siteWith("site_name: Handbook\ndocs_dir: manual\n")

        invokeAt("manual")

        val directory = root.findChild("manual")
        assertNotNull(directory)
        assertTrue("docs_dir names a directory", directory!!.isDirectory)
    }

    /**
     * Use case: the override directory of the Material theme. It is resolved against the configuration file
     * rather than against `docs_dir`, so it must not end up inside the documentation sources.
     */
    fun `test creates the override directory next to the configuration file`() {
        val root = siteWith("site_name: Handbook\ntheme:\n  name: material\n  custom_dir: overrides\n")

        invokeAt("overrides")

        assertNotNull(root.findChild("overrides"))
        assertNull(root.findFileByRelativePath("docs/overrides"))
    }

    /**
     * Use case: `site_dir` before the first build. The directory is build output MkDocs writes itself, so
     * the intention must stay away — creating it by hand only produces an empty directory.
     */
    fun `test is not offered for the build output directory`() {
        siteWith("site_name: Handbook\nsite_dir: build/site\n")

        assertFalse(isAvailableAt("build/site"))
    }

    /**
     * Use case: a value that already points at something. There is nothing to create, and offering it anyway
     * would put an action into the menu that does nothing.
     */
    fun `test is not offered for a target that exists`() {
        siteWith("site_name: Handbook\nnav:\n  - Intro: index.md\n")
        myFixture.addFileToProject("site/docs/index.md", "")

        assertFalse(isAvailableAt("index.md"))
    }

    /**
     * Use case: the caret sits on a value that is not a path at all. Every check builds on
     * [MkDocsPathKind], so a site name must not be offered a directory to be created for.
     */
    fun `test is not offered outside a path value`() {
        siteWith("site_name: Handbook\n")

        assertFalse(isAvailableAt("Handbook"))
    }

    /**
     * Writes a site of [text] with an existing documentation directory and opens its configuration file.
     *
     * @param text the content of the configuration file
     * @return the root directory of the site
     */
    private fun siteWith(text: String): VirtualFile {
        myFixture.addFileToProject("site/docs/.gitkeep", "")
        val configFile = myFixture.addFileToProject("site/mkdocs.yml", text)
        myFixture.configureFromExistingVirtualFile(configFile.virtualFile)
        return configFile.virtualFile.parent
    }

    /**
     * Puts the caret into [value] and runs the intention.
     *
     * @param value the path value to invoke it on
     */
    private fun invokeAt(value: String) {
        moveTo(value)
        WriteAction.runAndWait<RuntimeException> {
            intention.invoke(project, myFixture.editor, myFixture.file)
        }
    }

    /**
     * Puts the caret into [value] and asks whether the intention offers itself there.
     *
     * @param value the path value to ask about
     */
    private fun isAvailableAt(value: String): Boolean {
        moveTo(value)
        return intention.isAvailable(project, myFixture.editor, myFixture.file)
    }

    /**
     * Puts the caret into the first occurrence of [value] in the open file.
     *
     * @param value the text to move to
     */
    private fun moveTo(value: String) {
        val offset = myFixture.file.text.indexOf(value)
        assertTrue("'$value' is not in the file", offset >= 0)
        myFixture.editor.caretModel.moveToOffset(offset + 1)
    }
}
