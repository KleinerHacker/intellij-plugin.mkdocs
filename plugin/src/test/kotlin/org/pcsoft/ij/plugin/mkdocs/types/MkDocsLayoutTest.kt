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
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import java.nio.file.Files
import java.nio.file.Path
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers [MkDocsLayout], the shared answer to "where does this file sit inside a site" used by the project
 * view decorator, the file icon provider and the *New Directory* suggestions.
 */
class MkDocsLayoutTest : BasePlatformTestCase() {

    /**
     * Use case: a page deep inside the documentation directory has to find the site it belongs to, so the
     * plugin can read that site's configuration.
     */
    fun `test finds the site root above a nested file`() {
        val root = addFile("handbook/mkdocs.yml").parent
        val page = addFile("handbook/docs/guide/intro.md")

        assertEquals(root, runReadActionBlocking { MkDocsLayout.findSiteRoot(page) })
    }

    /**
     * Use case: a file that belongs to no site at all. There is nothing above it to find, and the caller has
     * to cope with that rather than getting a wrong site.
     */
    fun `test finds no site root outside a site`() {
        val loose = addFile("plain/notes.md")

        assertNull(runReadActionBlocking { MkDocsLayout.findSiteRoot(loose) })
    }

    /**
     * Use case: a site nested inside another one. The innermost configuration file is the one governing a
     * file, exactly as MkDocs would see it.
     */
    fun `test prefers the innermost site root`() {
        addFile("outer/mkdocs.yml")
        val innerRoot = addFile("outer/docs/inner/mkdocs.yml").parent
        val page = addFile("outer/docs/inner/docs/index.md")

        assertEquals(innerRoot, runReadActionBlocking { MkDocsLayout.findSiteRoot(page) })
    }

    /**
     * Use case: the documentation directory of a site with a default configuration, which is the common case.
     */
    fun `test resolves the default documentation directory`() {
        val root = addFile("defaults/mkdocs.yml").parent
        val docs = addFile("defaults/docs/index.md").parent

        assertEquals(docs, runReadActionBlocking { MkDocsLayout.docsDirOf(project, root) })
        assertTrue(runReadActionBlocking { MkDocsLayout.isDocsDirectory(project, docs) })
    }

    /**
     * Use case: a site pointing `docs_dir` at a nested path. Resolving the value against the site root is
     * what MkDocs does, so a bare name comparison would miss it.
     */
    fun `test resolves a nested documentation directory`() {
        val root = addFile("nested/mkdocs.yml", "site_name: Handbook\ndocs_dir: src/pages\n").parent
        val docs = addFile("nested/src/pages/index.md").parent

        assertEquals(docs, runReadActionBlocking { MkDocsLayout.docsDirOf(project, root) })
        assertTrue(runReadActionBlocking { MkDocsLayout.isDocsDirectory(project, docs) })
    }

    /**
     * Use case: deciding whether a file is a published page. Everything below the documentation directory is,
     * the directory itself is not, and neither is anything beside it.
     */
    fun `test recognises files inside the documentation directory`() {
        addFile("inside/mkdocs.yml")
        val page = addFile("inside/docs/guide/deep/page.md")
        val docs = addFile("inside/docs/index.md").parent
        val outside = addFile("inside/README.md")

        assertTrue(runReadActionBlocking { MkDocsLayout.isInsideDocsDir(project, page) })
        assertFalse(
            "the documentation directory is no page itself",
            runReadActionBlocking { MkDocsLayout.isInsideDocsDir(project, docs) },
        )
        assertFalse(runReadActionBlocking { MkDocsLayout.isInsideDocsDir(project, outside) })
    }

    /**
     * Use case: a site whose `docs_dir` names a directory that was never created. There is no documentation
     * directory to be inside of, and nothing must be reported as a page.
     */
    fun `test copes with a missing documentation directory`() {
        val root = addFile("missing/mkdocs.yml", "site_name: Handbook\ndocs_dir: absent\n").parent
        val stray = addFile("missing/docs/index.md")

        assertNull(runReadActionBlocking { MkDocsLayout.docsDirOf(project, root) })
        assertFalse(runReadActionBlocking { MkDocsLayout.isInsideDocsDir(project, stray) })
    }

    /**
     * Use case: the assets directory name of a module without a facet. MkDocs has no key for it, so the
     * convention is all that is left.
     */
    fun `test falls back to the conventional assets directory`() {
        assertEquals(MkDocsSiteTemplate.DEFAULT_ASSETS_DIR, MkDocsLayout.assetsDirNameOf(null))
    }

    /**
     * Use case: the stylesheets directory name of a module without a facet. Same situation as with the
     * assets directory — MkDocs names the individual files, never the directory.
     */
    fun `test falls back to the conventional stylesheets directory`() {
        assertEquals(MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR, MkDocsLayout.stylesheetsDirNameOf(null))
    }

    /**
     * Use case: a style sheet the configuration names in `extra_css`. That is the one thing making MkDocs
     * load it, so it has to be recognised wherever below the documentation directory it lies.
     */
    fun `test recognises a referenced style sheet`() {
        addFile("styled/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        addFile("styled/docs/index.md")
        val stylesheet = addFile("styled/docs/stylesheets/extra.css", "body { color: red; }\n")

        assertTrue(runReadActionBlocking { MkDocsLayout.isReferencedStylesheet(project, stylesheet) })
    }

    /**
     * Use case: a style sheet sitting in the stylesheets directory that `extra_css` does not name. The built
     * site never loads it, so its convenient location must not be mistaken for a reference.
     */
    fun `test rejects an unreferenced style sheet`() {
        addFile("unstyled/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        addFile("unstyled/docs/index.md")
        val other = addFile("unstyled/docs/stylesheets/unused.css", "body { color: red; }\n")

        assertFalse(runReadActionBlocking { MkDocsLayout.isReferencedStylesheet(project, other) })
    }

    /**
     * Use case: a site naming a style sheet that lies outside the stylesheets directory. `extra_css` resolves
     * against the documentation directory, not against a conventional folder, so this one counts too.
     */
    fun `test recognises a referenced style sheet outside the stylesheets directory`() {
        addFile("elsewhere-css/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - theme/print.css\n")
        addFile("elsewhere-css/docs/index.md")
        val stylesheet = addFile("elsewhere-css/docs/theme/print.css", "body { color: red; }\n")

        assertTrue(runReadActionBlocking { MkDocsLayout.isReferencedStylesheet(project, stylesheet) })
    }

    /**
     * Use case: a CSS file of the surrounding project, outside any MkDocs site. Nothing about MkDocs applies
     * to it.
     */
    fun `test rejects a style sheet outside a site`() {
        val loose = addFile("plain-project/style.css", "body { color: red; }\n")

        assertFalse(runReadActionBlocking { MkDocsLayout.isReferencedStylesheet(project, loose) })
    }

    /**
     * Use case: the wizard suggests an output directory for a site created inside a Gradle module. The
     * location does not exist yet, so the search has to start at the innermost existing directory.
     */
    fun `test detects the output directory of a surrounding build system`() {
        val moduleDir = Files.createTempDirectory("mkdocs-layout")
        try {
            Files.createFile(moduleDir.resolve("build.gradle.kts"))
            val notCreatedYet = moduleDir.resolve("handbook").resolve("nested")

            assertEquals(MkDocsProject.GRADLE_SITE_DIR, MkDocsLayout.detectSiteDir(notCreatedYet))
        } finally {
            moduleDir.toFile().deleteRecursively()
        }
    }

    /**
     * Use case: a location outside any build system. Nothing can be derived, so MkDocs' own default stands.
     */
    fun `test falls back to the default output directory`() {
        val plainDir: Path = Files.createTempDirectory("mkdocs-plain")
        try {
            assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, MkDocsLayout.detectSiteDir(plainDir))
        } finally {
            plainDir.toFile().deleteRecursively()
        }
    }

    /**
     * Use case: a site points `docs_dir` at a directory of its own naming. What the file says is what has to
     * be resolved — the project view marks exactly that directory as the documentation directory.
     */
    fun `test resolves the documentation directory a site configures`() {
        val file = addFile("configured/mkdocs.yml", "site_name: Handbook\ndocs_dir: sources\n")

        assertEquals("sources", resolveDocsDir(file))
    }

    /**
     * Use case: the ordinary site without `docs_dir`. MkDocs falls back to `docs`, so the plugin has to do
     * the same instead of marking nothing.
     */
    fun `test resolves the documentation directory to the MkDocs default`() {
        val file = addFile("plain-docs/mkdocs.yml", "site_name: Handbook\n")

        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(file))
    }

    /**
     * Use case: a half-written file makes the parser see a sequence behind `docs_dir`, or leaves the value
     * empty. Neither is a usable directory name, so the default has to apply rather than a guess.
     */
    fun `test resolves an unusable documentation directory to the default`() {
        val sequence = addFile("seq-docs/mkdocs.yml", "site_name: Handbook\ndocs_dir:\n  - one\n  - two\n")
        val blank = addFile("blank-docs/mkdocs.yml", "site_name: Handbook\ndocs_dir: \"   \"\n")

        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(sequence))
        assertEquals(MkDocsSiteTemplate.DEFAULT_DOCS_DIR, resolveDocsDir(blank))
    }

    /**
     * Use case: a site builds into a directory of the surrounding build system. The configured value has to
     * come back as written, so the plugin knows where the rendered site lands.
     */
    fun `test resolves the output directory a site configures`() {
        val file = addFile("configured-out/mkdocs.yml", "site_name: Handbook\nsite_dir: target/docs\n")

        assertEquals("target/docs", resolveSiteDir(file))
    }

    /**
     * Use case: a site without `site_dir`, which is the common case. MkDocs then builds into `site`, and so
     * must the plugin assume.
     */
    fun `test resolves the output directory to the MkDocs default`() {
        val file = addFile("plain-out/mkdocs.yml", "site_name: Handbook\n")

        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(file))
    }

    /**
     * Use case: the same two broken shapes behind `site_dir` — a sequence and an empty value. Both have to
     * fall back to the default MkDocs builds into.
     */
    fun `test resolves an unusable output directory to the default`() {
        val sequence = addFile("seq-out/mkdocs.yml", "site_name: Handbook\nsite_dir:\n  - one\n  - two\n")
        val blank = addFile("blank-out/mkdocs.yml", "site_name: Handbook\nsite_dir: \"   \"\n")

        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(sequence))
        assertEquals(MkDocsSiteTemplate.DEFAULT_SITE_DIR, resolveSiteDir(blank))
    }

    private fun resolveDocsDir(file: VirtualFile): String =
        runReadActionBlocking { MkDocsLayout.resolveDocsDir(project, file) }

    private fun resolveSiteDir(file: VirtualFile): String =
        runReadActionBlocking { MkDocsLayout.resolveSiteDir(project, file) }

    private fun addFile(relativePath: String, text: String = ""): VirtualFile =
        myFixture.addFileToProject(relativePath, text).virtualFile
}
