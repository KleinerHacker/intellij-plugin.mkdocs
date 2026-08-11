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

package org.pcsoft.ij.plugin.mkdocs.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the banner shown above a configuration file missing its metadata: which files get one, what it
 * says, and what its fix writes.
 */
class MkDocsMissingMetadataAnnotatorTest : BasePlatformTestCase() {

    /**
     * Use case: a freshly written configuration file carrying nothing but the site name — the state the
     * wizard leaves behind when the optional pages are skipped. Author and description are missing, and each
     * gets a banner of its own so either can be added without the other.
     */
    fun `test shows a banner per missing key`() {
        val banners = bannersOf("mkdocs.yml", "site_name: Handbook\n")

        assertEquals(2, banners.size)
        assertTrue(banners.any { it.description.contains("site_author") })
        assertTrue(banners.any { it.description.contains("site_description") })
    }

    /**
     * Use case: the banner has to cover the file as a whole rather than underline something in the text.
     * What it reports is precisely what is *not* in the file, so there is nothing to point at.
     *
     * The file level flag itself is only readable through internal API, which this project does not use, so
     * what is asserted here is the range a file level annotation produces: the whole file. Together with the
     * severity that is everything the public API exposes about the message.
     */
    fun `test the banner covers the whole file`() {
        val file = myFixture.configureByText("mkdocs.yml", "site_name: Handbook\n")
        val banners = myFixture.doHighlighting().filter { it.description?.contains("is missing") == true }

        assertTrue(banners.isNotEmpty())
        assertTrue(banners.all { it.startOffset == 0 && it.endOffset == file.textLength })
        assertTrue(banners.all { it.severity == HighlightSeverity.WARNING })
    }

    /**
     * Use case: a complete configuration file. Nothing is missing, so no banner may appear — a reminder that
     * cannot be acted on is noise sitting permanently above the editor.
     */
    fun `test shows no banner for a complete file`() {
        val banners = bannersOf(
            "mkdocs.yml",
            "site_name: Handbook\nsite_author: Ada\nsite_description: About the machine\n",
        )

        assertEmpty(banners)
    }

    /**
     * Use case: the `.yaml` spelling of the configuration file, which MkDocs accepts just as well. It has to
     * be treated exactly like the `.yml` one.
     */
    fun `test covers the yaml spelling as well`() {
        assertEquals(2, bannersOf("mkdocs.yaml", "site_name: Handbook\n").size)
    }

    /**
     * Use case: any other YAML file in the project — a CI workflow, a Docker Compose file. None of them is
     * an MkDocs configuration, so none may be decorated with a banner.
     */
    fun `test ignores yaml files that are no mkdocs configuration`() {
        assertEmpty(bannersOf("docker-compose.yml", "services:\n  web:\n    image: nginx\n"))
    }

    /**
     * Use case: an empty configuration file. Every key is missing, and the banner must say so rather than
     * falling silent because there is no mapping to look at.
     */
    fun `test shows a banner for every key of an empty file`() {
        assertEquals(3, bannersOf("mkdocs.yml", "").size)
    }

    /**
     * Use case: the user follows the banner's fix for the author. The key is added with an empty value — the
     * fix cannot know the author — and it lands between the site name and the description, which is where
     * MkDocs documents it.
     */
    fun `test the fix adds the key in the documented order`() {
        val file = myFixture.configureByText("mkdocs.yml", "site_name: Handbook\nsite_description: About it\n")
        myFixture.doHighlighting()

        myFixture.launchAction(myFixture.findSingleIntention("Add 'site_author'"))

        assertEquals("site_name: Handbook\nsite_author:\nsite_description: About it\n", file.text)
    }

    /**
     * Use case: the fix on a file whose remaining keys come after none of the metadata keys. The new key
     * then belongs at the end, and the rest of the file must stay exactly as its author arranged it.
     */
    fun `test the fix appends when nothing follows`() {
        val file = myFixture.configureByText("mkdocs.yml", "site_name: Handbook\ntheme: readthedocs\n")
        myFixture.doHighlighting()

        myFixture.launchAction(myFixture.findSingleIntention("Add 'site_description'"))

        assertTrue(file.text.startsWith("site_name: Handbook\ntheme: readthedocs\n"))
        assertTrue(file.text.contains("site_description:"))
    }

    /** Highlights a file with [name] and [text] and returns the banners the annotator produced. */
    private fun bannersOf(name: String, text: String): List<HighlightInfo> {
        myFixture.configureByText(name, text)
        return myFixture.doHighlighting()
            .filter { it.description?.contains("is missing") == true }
    }
}
