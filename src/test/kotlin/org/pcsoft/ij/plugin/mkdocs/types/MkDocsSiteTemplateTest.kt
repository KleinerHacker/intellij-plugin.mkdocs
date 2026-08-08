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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the rules deciding whether the site creation wizard may proceed. They work on plain paths, so no
 * IntelliJ Platform is needed — a temporary directory is enough.
 */
class MkDocsSiteTemplateTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /**
     * Use case: the ordinary input — a name and the two default directory names, pointing at an existing
     * empty directory. Nothing stands in the way, so no error is reported.
     */
    @Test
    fun `accepts a complete template`() {
        assertNull(template(siteName = "My Documentation").validate())
    }

    /**
     * Use case: the wizard appended the site name to the selected directory, so the target does not exist
     * yet. That is the normal state while typing and must be accepted — the directory is created with the
     * site.
     */
    @Test
    fun `accepts a directory that does not exist yet`() {
        val template = MkDocsSiteTemplate(rootPath = root().resolve("brand-new"), siteName = "Brand New")

        assertNull(template.validate())
    }

    /**
     * Use case: the user typed a location several levels below anything that exists. Every missing level is
     * created along with the site, so this is accepted just like a single missing directory.
     */
    @Test
    fun `accepts a path whose parents are missing too`() {
        val template = MkDocsSiteTemplate(
            rootPath = root().resolve("missing").resolve("deeper").resolve("site"),
            siteName = "Deep",
        )

        assertNull(template.validate())
    }

    /**
     * Use case: a location that is not an absolute path — a half-typed value, or one pasted without its
     * drive or leading separator. Creating a directory relative to wherever the IDE happens to run is never
     * what the user meant.
     */
    @Test
    fun `refuses a location that is not absolute`() {
        val template = MkDocsSiteTemplate(rootPath = Path.of("relative", "site"), siteName = "Docs")

        assertEquals(MkDocsSiteTemplateError.INVALID_ROOT, template.validate())
    }

    /**
     * Use case: the location field is empty. There is no directory to speak of, so the wizard cannot
     * proceed.
     */
    @Test
    fun `refuses an empty location`() {
        val template = MkDocsSiteTemplate(rootPath = Path.of(""), siteName = "Docs")

        assertEquals(MkDocsSiteTemplateError.INVALID_ROOT, template.validate())
    }

    /**
     * Use case: the location points at a file rather than a directory. A site cannot live inside a file.
     */
    @Test
    fun `refuses a path pointing at a file`() {
        val file = folder.newFile("readme.md")

        val template = MkDocsSiteTemplate(rootPath = file.toPath(), siteName = "Docs")

        assertEquals(MkDocsSiteTemplateError.INVALID_ROOT, template.validate())
    }

    /**
     * Use case: the user has not typed a name yet. `site_name` would end up empty, so the wizard must not
     * proceed.
     */
    @Test
    fun `refuses a blank site name`() {
        assertEquals(MkDocsSiteTemplateError.BLANK_SITE_NAME, template(siteName = "   ").validate())
    }

    /**
     * Use case: a directory name carrying a path of its own (`../docs`, `a/b`). Accepting it would let the
     * structure escape the site root, so both directory fields refuse it.
     */
    @Test
    fun `refuses directory names carrying a path`() {
        assertEquals(
            MkDocsSiteTemplateError.INVALID_DOCS_DIR,
            template(siteName = "Docs", docsDir = "../outside").validate(),
        )
        assertEquals(
            MkDocsSiteTemplateError.INVALID_ASSETS_DIR,
            template(siteName = "Docs", assetsDir = "nested/assets").validate(),
        )
    }

    /**
     * Use case: an empty directory name. MkDocs needs a directory, so the field cannot simply be cleared.
     */
    @Test
    fun `refuses blank directory names`() {
        assertEquals(
            MkDocsSiteTemplateError.INVALID_DOCS_DIR,
            template(siteName = "Docs", docsDir = " ").validate(),
        )
        assertEquals(
            MkDocsSiteTemplateError.INVALID_ASSETS_DIR,
            template(siteName = "Docs", assetsDir = "").validate(),
        )
    }

    /**
     * Use case: the chosen directory already is a site root. A second configuration file next to the first
     * one would be ignored by MkDocs and confuse the detection, so creation is refused.
     */
    @Test
    fun `refuses a directory that already holds a site`() {
        folder.newFile("mkdocs.yml")

        assertEquals(MkDocsSiteTemplateError.SITE_EXISTS, template(siteName = "Second").validate())
    }

    /**
     * Use case: the `.yaml` spelling is already there. It is just as much an MkDocs configuration file, so it
     * blocks creation the same way.
     */
    @Test
    fun `refuses a directory holding the yaml spelling`() {
        folder.newFile("mkdocs.yaml")

        assertEquals(MkDocsSiteTemplateError.SITE_EXISTS, template(siteName = "Second").validate())
    }

    /**
     * Use case: the target directory does not exist yet — the normal case after the wizard appended the site
     * name. Nothing is there to be overwritten, so there is nothing to warn about.
     */
    @Test
    fun `does not warn about a directory that does not exist`() {
        val template = MkDocsSiteTemplate(rootPath = root().resolve("brand-new"), siteName = "Brand New")

        assertNull(template.warn())
    }

    /**
     * Use case: the user pointed the location at an existing empty directory. The site fills it, nothing is
     * touched, so there is nothing to warn about either.
     */
    @Test
    fun `does not warn about an empty directory`() {
        assertNull(template(siteName = "Docs").warn())
    }

    /**
     * Use case: the location points at a directory that already holds other content. Creating the site there
     * mixes it with that content — allowed, but the user should know before pressing Finish.
     */
    @Test
    fun `warns about a directory that is not empty`() {
        folder.newFile("readme.md")

        assertEquals(MkDocsSiteTemplateWarning.NOT_EMPTY, template(siteName = "Docs").warn())
    }

    /**
     * Use case: a directory holding nothing but subdirectories. Content is content — the warning does not
     * depend on what kind it is.
     */
    @Test
    fun `warns about a directory holding only subdirectories`() {
        folder.newFolder("src")

        assertEquals(MkDocsSiteTemplateWarning.NOT_EMPTY, template(siteName = "Docs").warn())
    }

    private fun root(): Path = folder.root.toPath()

    /**
     * Use case: the wizard suggested the output directory of the surrounding build system. Those are nested
     * paths, so the output directory has to accept more than a single name.
     */
    @Test
    fun `accepts a nested output directory`() {
        assertNull(template(siteName = "Handbook", siteDir = "target/docs").validate())
        assertNull(template(siteName = "Handbook", siteDir = "build/docs").validate())
    }

    /**
     * Use case: an output directory the user pointed outside the site — absolute, or climbing up with `..`.
     * MkDocs deletes the output directory before writing it, so this must never be accepted by accident.
     */
    @Test
    fun `rejects an output directory outside the site`() {
        assertEquals(
            MkDocsSiteTemplateError.INVALID_SITE_DIR,
            template(siteName = "Handbook", siteDir = "../elsewhere").validate(),
        )
        assertEquals(
            MkDocsSiteTemplateError.INVALID_SITE_DIR,
            template(siteName = "Handbook", siteDir = "/var/www").validate(),
        )
    }

    /**
     * Use case: the output directory field was cleared. MkDocs needs somewhere to build into, so an empty
     * value is refused rather than silently defaulted.
     */
    @Test
    fun `rejects a blank output directory`() {
        assertEquals(
            MkDocsSiteTemplateError.INVALID_SITE_DIR,
            template(siteName = "Handbook", siteDir = "  ").validate(),
        )
    }

    private fun template(
        siteName: String,
        docsDir: String = "docs",
        assetsDir: String = "assets",
        siteDir: String = "site",
    ): MkDocsSiteTemplate = MkDocsSiteTemplate(
        rootPath = root(),
        siteName = siteName,
        docsDirName = docsDir,
        assetsDirName = assetsDir,
        siteDirName = siteDir,
    )
}
