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

    /**
     * Use case: a site points `docs_dir` at a directory of its own naming. The value has to be read as
     * written — the project view marks exactly that directory as the documentation directory.
     */
    fun `test reads a configured documentation directory`() {
        val file = configFile("docs/mkdocs.yml", "site_name: Handbook\ndocs_dir: sources\n")

        assertEquals("sources", readDocsDir(file))
        assertEquals("sources", resolveDocsDir(file))
    }

    /**
     * Use case: the ordinary site without `docs_dir`. MkDocs falls back to `docs`, so the plugin has to do
     * the same instead of marking nothing.
     */
    fun `test falls back to the default documentation directory`() {
        val file = configFile("docs/mkdocs.yml", "site_name: Handbook\n")

        assertNull(readDocsDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(file))
    }

    /**
     * Use case: a half-written file makes the parser see a sequence behind `docs_dir`. The text of such a
     * node is no usable directory name, so it must be refused rather than passed on.
     */
    fun `test refuses a non scalar documentation directory`() {
        val file = configFile("docs/mkdocs.yml", "site_name: Handbook\ndocs_dir:\n  - one\n  - two\n")

        assertNull(readDocsDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(file))
    }

    /**
     * Use case: `docs_dir` is present but empty. An empty directory name is unusable, so the default applies.
     */
    fun `test falls back when the documentation directory is blank`() {
        val file = configFile("docs/mkdocs.yml", "site_name: Handbook\ndocs_dir: \"   \"\n")

        assertNull(readDocsDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(file))
    }

    /**
     * Use case: a site builds into a directory of the surrounding build system. The configured value has to
     * be reported as written, so the plugin knows where the rendered site lands.
     */
    fun `test reads the configured output directory`() {
        val file = configFile("out/mkdocs.yml", "site_name: Handbook\nsite_dir: target/docs\n")

        assertEquals("target/docs", readSiteDir(file))
        assertEquals("target/docs", resolveSiteDir(file))
    }

    /**
     * Use case: a site without `site_dir`, which is the common case. MkDocs then builds into `site`, and so
     * must the plugin assume.
     */
    fun `test falls back to the default output directory`() {
        val file = configFile("plain/mkdocs.yml", "site_name: Handbook\n")

        assertNull(readSiteDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(file))
    }

    /**
     * Use case: a half-written file makes the parser see a sequence behind `site_dir`. The text of such a
     * node is no usable directory, so the default applies.
     */
    fun `test falls back when the output directory is no scalar`() {
        val file = configFile("seq/mkdocs.yml", "site_name: Handbook\nsite_dir:\n  - one\n  - two\n")

        assertNull(readSiteDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(file))
    }

    /**
     * Use case: `site_dir` is present but empty. An empty directory name is unusable, so the default applies.
     */
    fun `test falls back when the output directory is blank`() {
        val file = configFile("blank/mkdocs.yml", "site_name: Handbook\nsite_dir: \"   \"\n")

        assertNull(readSiteDir(file))
        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(file))
    }

    /**
     * Use case: a site pulling in style sheets. Every entry of `extra_css` has to come back, in the order it
     * is written, because those paths are what tells the plugin which files the built site actually loads.
     */
    fun `test reads the referenced style sheets`() {
        val file = configFile(
            "extra/mkdocs.yml",
            "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n  - css/print.css\n",
        )

        assertEquals(listOf("stylesheets/extra.css", "css/print.css"), readExtraCss(file))
    }

    /**
     * Use case: a site without the key. MkDocs then loads no style sheet at all, so the list has to be empty
     * rather than a guess at a conventional directory.
     */
    fun `test reports no style sheets without the key`() {
        val file = configFile("no-extra/mkdocs.yml", "site_name: Handbook\n")

        assertEmpty(readExtraCss(file))
    }

    /**
     * Use case: a half-written file where the key carries a plain scalar instead of a sequence. That is no
     * usable list of paths and must not be turned into one.
     */
    fun `test ignores a scalar value behind the key`() {
        val file = configFile("scalar/mkdocs.yml", "site_name: Handbook\nextra_css: stylesheets/extra.css\n")

        assertEmpty(readExtraCss(file))
    }

    /**
     * Use case: a sequence still being typed, carrying an empty entry between two usable ones. The blank has
     * no path in it and has to be dropped instead of matching some file by accident.
     */
    fun `test drops blank entries of the sequence`() {
        val file = configFile(
            "blank/mkdocs.yml",
            "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n  -\n  - '  '\n",
        )

        assertEquals(listOf("stylesheets/extra.css"), readExtraCss(file))
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun readSiteName(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readSiteName(project, file) }

    private fun resolveSiteName(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.resolveSiteName(project, file) }

    private fun readDocsDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readDocsDir(project, file) }

    private fun resolveDocsDir(file: VirtualFile): String =
        runReadActionBlocking { MkDocsLayout.resolveDocsDir(project, file) }

    private fun readSiteDir(file: VirtualFile): String? =
        runReadActionBlocking { MkDocsConfig.readSiteDir(project, file) }

    private fun resolveSiteDir(file: VirtualFile): String =
        runReadActionBlocking { MkDocsLayout.resolveSiteDir(project, file) }

    private fun readExtraCss(file: VirtualFile): List<String> =
        runReadActionBlocking { MkDocsConfig.readExtraCss(project, file) }
}
