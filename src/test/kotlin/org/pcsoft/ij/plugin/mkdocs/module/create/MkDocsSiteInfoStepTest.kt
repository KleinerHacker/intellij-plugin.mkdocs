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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError
import java.nio.file.Path

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the second wizard step: what the site calls itself, and which of it is required.
 */
class MkDocsSiteInfoStepTest : BasePlatformTestCase() {

    /**
     * Use case: the user moves on from the first step without having typed a site name yet. The site name is
     * the one key MkDocs cannot do without, so the step has to refuse.
     */
    fun `test refuses a missing site name`() {
        val step = MkDocsSiteInfoStep(project, null)

        assertEquals(MkDocsSiteTemplateError.BLANK_SITE_NAME, step.validate())

        step.setSiteNameForTest("Handbook")
        assertNull(step.validate())
    }

    /**
     * Use case: the site address was filled in. It ends up in `site_url` and in every canonical link, so an
     * address that is none has to be caught before the site is created — while an empty field stays fine.
     */
    fun `test refuses a site address that is no web address`() {
        val step = MkDocsSiteInfoStep(project, null)
        step.setSiteNameForTest("Handbook")

        step.setSiteUrlForTest("example.org")
        assertEquals(MkDocsSiteTemplateError.INVALID_SITE_URL, step.validate())

        step.setSiteUrlForTest("https://example.org/docs")
        assertNull(step.validate())

        step.setSiteUrlForTest("")
        assertNull(step.validate())
    }

    /**
     * Use case: the user typed the technical name in the first step and moves on. Typing the same thing
     * again is what the prefill spares them — until they decide the site is called something else.
     */
    fun `test prefills the site name from the technical name`() {
        val step = MkDocsSiteInfoStep(project, null)

        step.suggestSiteName("handbook")
        assertEquals("handbook", applied(step).siteName)

        step.setSiteNameForTest("The Handbook")
        step.suggestSiteName("something-else")
        assertEquals("The Handbook", applied(step).siteName)
    }

    /**
     * Use case: everything on this page reaches the template, trimmed — a value padded with spaces from
     * pasting has no business in the configuration file.
     */
    fun `test applies every entered value to the template`() {
        val step = MkDocsSiteInfoStep(project, null)
        step.setSiteNameForTest("  Handbook  ")
        step.setSiteAuthorForTest("Ada Lovelace")
        step.setSiteDescriptionForTest("Everything about the machine.")
        step.setSiteUrlForTest("https://example.org/docs")

        val template = applied(step)
        assertEquals("Handbook", template.siteName)
        assertEquals("Ada Lovelace", template.siteAuthor)
        assertEquals("Everything about the machine.", template.siteDescription)
        assertEquals("https://example.org/docs", template.siteUrl)
    }

    /**
     * Use case: the wizard has to grey out *Next* while the site name is missing, which it learns about
     * through this callback.
     */
    fun `test notifies about every input change`() {
        val step = MkDocsSiteInfoStep(project, null)
        var notifications = 0
        step.onInputChanged = { notifications++ }

        step.setSiteNameForTest("Handbook")
        assertTrue("the site name must notify", notifications > 0)

        val afterName = notifications
        step.setSiteUrlForTest("https://example.org")
        assertTrue("the site address must notify", notifications > afterName)
    }

    /** Applies the step to an otherwise empty template. */
    private fun applied(step: MkDocsSiteInfoStep): MkDocsSiteTemplate =
        step.applyTo(MkDocsSiteTemplate(rootPath = Path.of("/tmp/site").toAbsolutePath(), siteName = ""))
}
