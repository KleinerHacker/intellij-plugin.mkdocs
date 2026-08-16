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
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers the whole point of renaming a technical directory through the facet page: every reference pointing
 * into it has to follow. That is what makes the rename safe, and it involves the reference contributor, the
 * YAML PSI and the platform rename refactoring at once.
 */
class MkDocsDirectoryRenameIT : BasePlatformTestCase() {

    /**
     * Use case: the stylesheets directory is renamed on a site that loads two style sheets through
     * `extra_css`. Both entries have to point into the new directory afterwards — an entry MkDocs cannot
     * resolve means the built site silently loses its styling.
     */
    fun `test rewrites the extra_css entries`() {
        val configFile = site(
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
              - stylesheets/print.css
            """.trimIndent() + "\n",
        )
        myFixture.addFileToProject("site/docs/stylesheets/extra.css", "body {}\n")
        myFixture.addFileToProject("site/docs/stylesheets/print.css", "body {}\n")

        applyLayout(configFile, current().copy(stylesheetsDirName = "css"))

        assertEquals(listOf("css/extra.css", "css/print.css"), readExtraCss(configFile))
        assertNotNull(configFile.parent.findChild("docs")?.findChild("css")?.findChild("extra.css"))
    }

    /**
     * Use case: the assets directory is renamed on a site whose theme takes its logo out of it. The `logo` key
     * is a path like any other, so it has to be rewritten as well.
     */
    fun `test rewrites the theme logo`() {
        val configFile = site("site_name: Handbook\ntheme:\n  name: material\n  logo: assets/logo.svg\n")
        myFixture.addFileToProject("site/docs/assets/logo.svg", "<svg/>")

        applyLayout(configFile, current().copy(assetsDirName = "media"))

        assertTrue("the logo must point into the renamed directory", text(configFile).contains("logo: media/logo.svg"))
    }

    /**
     * Use case: the documentation directory itself is renamed on a site listing its pages in `nav`. The `nav`
     * entries are resolved against `docs_dir` and therefore stay as they are, while `docs_dir` follows the
     * directory — the pages must still be found afterwards.
     */
    fun `test keeps the navigation working when the documentation directory moves`() {
        val configFile = site("site_name: Handbook\nnav:\n  - Home: index.md\n  - Guide: guide.md\n")
        myFixture.addFileToProject("site/docs/guide.md", "# Guide\n")

        applyLayout(configFile, current().copy(docsDirName = "documentation"))

        assertEquals("documentation", runReadActionBlocking { MkDocsConfig.readDocsDir(project, configFile) })
        val content = text(configFile)
        assertTrue("the navigation entries stay relative to docs_dir", content.contains("- Guide: guide.md"))
        assertNotNull(configFile.parent.findChild("documentation")?.findChild("guide.md"))
    }

    /**
     * Creates a site with a start page and the two convention directories.
     *
     * @param configText the content of the configuration file
     */
    private fun site(configText: String): VirtualFile {
        val configFile = myFixture.addFileToProject("site/mkdocs.yml", configText).virtualFile
        myFixture.addFileToProject("site/docs/index.md", "# Handbook\n")
        myFixture.addFileToProject("site/docs/assets/.gitkeep", "")
        myFixture.addFileToProject("site/docs/stylesheets/.gitkeep", "")
        return configFile
    }

    /** The layout such a site starts out with. */
    private fun current(): MkDocsDirectoryLayout = MkDocsDirectoryLayout(
        docsDirName = MkDocsSiteTemplate.DEFAULT_DOCS_DIR,
        siteDirName = MkDocsSiteTemplate.DEFAULT_SITE_DIR,
        assetsDirName = MkDocsSiteTemplate.DEFAULT_ASSETS_DIR,
        stylesheetsDirName = MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR,
    )

    private fun applyLayout(configFile: VirtualFile, target: MkDocsDirectoryLayout) {
        val service = MkDocsDirectoryService.getInstance(project)
        service.applyLayout(myFixture.module, configFile, service.currentLayout(myFixture.module, configFile), target)
    }

    private fun readExtraCss(file: VirtualFile): List<String> =
        runReadActionBlocking { MkDocsConfig.readExtraCss(project, file) }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }
}
