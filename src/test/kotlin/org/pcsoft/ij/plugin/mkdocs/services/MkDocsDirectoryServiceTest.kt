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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the directory renames the MkDocs facet page performs: the directories themselves and the two keys
 * MkDocs holds for them. Needs the platform because renaming goes through the rename refactoring.
 */
class MkDocsDirectoryServiceTest : BasePlatformTestCase() {

    /**
     * Use case: the user gives the assets directory a name of its own. The directory has to move with its
     * content, and the facet page — not MkDocs — is the only place the new name is recorded, so the
     * configuration file must stay as it was.
     */
    fun `test renames the assets directory`() {
        val configFile = site()
        myFixture.addFileToProject("site/docs/assets/logo.svg", "<svg/>")

        applyLayout(configFile, current().copy(assetsDirName = "media"))

        val docs = configFile.parent.findChild("docs")!!
        assertNull("the old directory must be gone", docs.findChild("assets"))
        assertNotNull("the renamed directory must hold its file", docs.findChild("media")?.findChild("logo.svg"))
        assertNull("MkDocs has no key for the assets directory", readDocsDir(configFile))
    }

    /**
     * Use case: the user renames the documentation directory of a site that relied on the MkDocs default and
     * therefore never wrote `docs_dir`. The directory moves, and the key has to appear so MkDocs still finds
     * the pages.
     */
    fun `test renames the documentation directory and writes the key`() {
        val configFile = site()

        applyLayout(configFile, current().copy(docsDirName = "documentation"))

        val root = configFile.parent
        assertNull("the old directory must be gone", root.findChild("docs"))
        assertNotNull(
            "the renamed directory must hold the start page",
            root.findChild("documentation")?.findChild("index.md")
        )
        assertEquals("documentation", readDocsDir(configFile))
    }

    /**
     * Use case: the user sets the documentation directory back to the MkDocs default. The key would then
     * repeat what MkDocs assumes anyway, so it is taken out of the file again.
     */
    fun `test removes the key when the default is restored`() {
        val configFile = site("site_name: Handbook\ndocs_dir: documentation\n", docsDirName = "documentation")

        applyLayout(
            configFile,
            current(docsDirName = "documentation").copy(docsDirName = MkDocsSiteTemplate.DEFAULT_DOCS_DIR),
        )

        assertNotNull(configFile.parent.findChild("docs"))
        assertNull("the key must be gone with the default restored", readDocsDir(configFile))
    }

    /**
     * Use case: the user points the build output somewhere else. Build output is written again by the next
     * build, so nothing is moved — only `site_dir` changes.
     */
    fun `test writes the output directory without moving anything`() {
        val configFile = site()

        applyLayout(configFile, current().copy(siteDirName = "build/docs"))

        assertEquals("build/docs", readSiteDir(configFile))
        assertNotNull("the documentation directory must be untouched", configFile.parent.findChild("docs"))
    }

    /**
     * Use case: the page is applied without anything being changed. Nothing may move and nothing may be
     * written, so a site that never named its directories does not suddenly carry the defaults in writing.
     */
    fun `test leaves an unchanged layout alone`() {
        val configFile = site()

        applyLayout(configFile, current())

        assertEquals("site_name: Handbook\n", text(configFile))
        assertNotNull(configFile.parent.findChild("docs"))
    }

    /**
     * Use case: the user renames the site on its facet page. `site_name` is what the module is called, so the
     * new name has to end up in the configuration file — the facet is not a place it could live on its own.
     */
    fun `test renames the site`() {
        val configFile = site()

        MkDocsDirectoryService.getInstance(project).renameSite(configFile, "Field Manual")

        assertEquals("Field Manual", runReadActionBlocking { MkDocsConfig.readSiteName(project, configFile) })
    }

    /**
     * Use case: a site whose configuration file never named it — MkDocs then falls back to the directory name,
     * and so does the plugin. Renaming it has to add the key rather than leave the site nameless.
     */
    fun `test writes the site name into a file without one`() {
        val configFile = site("# just a comment\n")

        MkDocsDirectoryService.getInstance(project).renameSite(configFile, "Field Manual")

        assertEquals("Field Manual", runReadActionBlocking { MkDocsConfig.readSiteName(project, configFile) })
        assertTrue("the comment must survive", text(configFile).contains("# just a comment"))
    }

    /**
     * Use case: the site name field is emptied. An empty `site_name` renders an empty header, so the file is
     * left as it is and the site keeps the name it had.
     */
    fun `test ignores an empty site name`() {
        val configFile = site()

        MkDocsDirectoryService.getInstance(project).renameSite(configFile, "   ")

        assertEquals("Handbook", runReadActionBlocking { MkDocsConfig.readSiteName(project, configFile) })
    }

    /**
     * Creates a site with a start page and the two convention directories.
     *
     * @param configText the content of the configuration file
     * @param docsDirName the documentation directory the site uses
     */
    private fun site(
        configText: String = "site_name: Handbook\n",
        docsDirName: String = MkDocsSiteTemplate.DEFAULT_DOCS_DIR,
    ): VirtualFile {
        val configFile = myFixture.addFileToProject("site/mkdocs.yml", configText).virtualFile
        myFixture.addFileToProject("site/$docsDirName/index.md", "# Handbook\n")
        myFixture.addFileToProject("site/$docsDirName/assets/.gitkeep", "")
        myFixture.addFileToProject("site/$docsDirName/stylesheets/.gitkeep", "")
        return configFile
    }

    /** The layout such a site starts out with. */
    private fun current(docsDirName: String = MkDocsSiteTemplate.DEFAULT_DOCS_DIR): MkDocsDirectoryLayout =
        MkDocsDirectoryLayout(
            docsDirName = docsDirName,
            siteDirName = MkDocsSiteTemplate.DEFAULT_SITE_DIR,
            assetsDirName = MkDocsSiteTemplate.DEFAULT_ASSETS_DIR,
            stylesheetsDirName = MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR,
        )

    private fun applyLayout(configFile: VirtualFile, target: MkDocsDirectoryLayout) {
        val service = MkDocsDirectoryService.getInstance(project)
        service.applyLayout(myFixture.module, configFile, service.currentLayout(myFixture.module, configFile), target)
    }

    private fun readDocsDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readDocsDir(project, file) }

    private fun readSiteDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readSiteDir(project, file) }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }
}
