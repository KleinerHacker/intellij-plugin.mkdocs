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

package org.pcsoft.ij.plugin.mkdocs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsProjectTest {

    @Test
    fun `recognises both accepted config file names`() {
        assertTrue(MkDocsProject.isConfigFile("mkdocs.yml"))
        assertTrue(MkDocsProject.isConfigFile("mkdocs.yaml"))
    }

    @Test
    fun `recognition is case-insensitive`() {
        assertTrue(MkDocsProject.isConfigFile("MkDocs.YML"))
    }

    @Test
    fun `rejects unrelated file names`() {
        assertFalse(MkDocsProject.isConfigFile("mkdocs.json"))
        assertFalse(MkDocsProject.isConfigFile("docs.yml"))
        assertFalse(MkDocsProject.isConfigFile(""))
        // A path is not a bare file name — callers must strip the directory part themselves.
        assertFalse(MkDocsProject.isConfigFile("docs/mkdocs.yml"))
    }

    /**
     * Use case: the icon provider asks whether a file is a page of a site. Only Markdown files are, and the
     * extension has to be recognised whatever way it is spelled.
     */
    @Test
    fun `recognises markdown page files`() {
        assertTrue(MkDocsProject.isPageFile("index.md"))
        assertTrue(MkDocsProject.isPageFile("Getting-Started.MD"))
        assertFalse(MkDocsProject.isPageFile("index.markdown"))
        assertFalse(MkDocsProject.isPageFile("mkdocs.yml"))
        // A file called just ".md" is an extension without a name, not a page.
        assertFalse(MkDocsProject.isPageFile(".md"))
        assertFalse(MkDocsProject.isPageFile(""))
    }

    /**
     * Use case: the icon provider asks whether a file is the requirements file of a site. Only the exact name
     * counts, whatever way it is spelled — the location is decided elsewhere.
     */
    @Test
    fun `recognises the requirements file name`() {
        assertTrue(MkDocsProject.isRequirementsFile("requirements.txt"))
        assertTrue(MkDocsProject.isRequirementsFile("Requirements.TXT"))
        assertTrue(MkDocsProject.isRequirementsFile("REQUIREMENTS.txt"))
    }

    /**
     * Use case: files merely looking like the requirements file, such as a variant for a second environment
     * or a path instead of a bare name. None of them is the requirements file of a site.
     */
    @Test
    fun `rejects file names that are no requirements file`() {
        assertFalse(MkDocsProject.isRequirementsFile("requirements-dev.txt"))
        assertFalse(MkDocsProject.isRequirementsFile("requirements"))
        assertFalse(MkDocsProject.isRequirementsFile("requirements.txt.bak"))
        assertFalse(MkDocsProject.isRequirementsFile(""))
        // A path is not a bare file name — callers must strip the directory part themselves.
        assertFalse(MkDocsProject.isRequirementsFile("docs/requirements.txt"))
    }

    /**
     * Use case: the wizard suggests an output directory. A Maven module keeps generated output under
     * `target`, so the site must not drop its HTML next to the sources.
     */
    @Test
    fun `suggests the maven output directory`() {
        assertEquals(MkDocsProject.MAVEN_SITE_DIR, MkDocsProject.siteDirFor(listOf("pom.xml", "src")))
    }

    /**
     * Use case: the same for a Gradle module, in both the Groovy and the Kotlin spelling of the build script.
     */
    @Test
    fun `suggests the gradle output directory`() {
        assertEquals(MkDocsProject.GRADLE_SITE_DIR, MkDocsProject.siteDirFor(listOf("build.gradle")))
        assertEquals(MkDocsProject.GRADLE_SITE_DIR, MkDocsProject.siteDirFor(listOf("build.gradle.kts")))
        assertEquals(MkDocsProject.GRADLE_SITE_DIR, MkDocsProject.siteDirFor(listOf("settings.gradle.kts")))
    }

    /**
     * Use case: a plain IntelliJ IDEA module, recognised by its `.idea` directory or its module file.
     */
    @Test
    fun `suggests the idea output directory`() {
        assertEquals(MkDocsProject.IDEA_SITE_DIR, MkDocsProject.siteDirFor(listOf(".idea")))
        assertEquals(MkDocsProject.IDEA_SITE_DIR, MkDocsProject.siteDirFor(listOf("handbook.iml")))
    }

    /**
     * Use case: a directory carrying markers of several build systems. Maven wins over Gradle and Gradle over
     * IDEA, so a Maven project imported into the IDE does not get the IDEA directory.
     */
    @Test
    fun `prefers the outermost build system`() {
        assertEquals(
            MkDocsProject.MAVEN_SITE_DIR,
            MkDocsProject.siteDirFor(listOf(".idea", "build.gradle", "pom.xml")),
        )
        assertEquals(MkDocsProject.GRADLE_SITE_DIR, MkDocsProject.siteDirFor(listOf(".idea", "build.gradle")))
    }

    /**
     * Use case: a directory belonging to no build system at all. There is nothing to derive, so the caller
     * has to fall back to the MkDocs default itself.
     */
    @Test
    fun `announces no build system for an unrelated directory`() {
        assertNull(MkDocsProject.siteDirFor(listOf("docs", "README.md")))
        assertNull(MkDocsProject.siteDirFor(emptyList()))
    }

    /**
     * Use case: the user types an output directory. Several levels are fine — the build systems nest their
     * output — but the path must stay below the site root.
     */
    @Test
    fun `accepts nested output directories`() {
        assertTrue(MkDocsProject.isValidSiteDirName("site"))
        assertTrue(MkDocsProject.isValidSiteDirName("target/docs"))
        assertTrue(MkDocsProject.isValidSiteDirName("build\\docs"))
    }

    /**
     * Use case: an output directory that would escape the site root or address a place of its own. MkDocs
     * would happily write there, which is exactly what must not happen by accident.
     */
    @Test
    fun `rejects output directories leaving the site root`() {
        assertFalse(MkDocsProject.isValidSiteDirName(""))
        assertFalse(MkDocsProject.isValidSiteDirName("   "))
        assertFalse(MkDocsProject.isValidSiteDirName("/var/www"))
        assertFalse(MkDocsProject.isValidSiteDirName("C:/temp"))
        assertFalse(MkDocsProject.isValidSiteDirName("../site"))
        assertFalse(MkDocsProject.isValidSiteDirName("site/"))
        assertFalse(MkDocsProject.isValidSiteDirName("build//docs"))
    }
}
