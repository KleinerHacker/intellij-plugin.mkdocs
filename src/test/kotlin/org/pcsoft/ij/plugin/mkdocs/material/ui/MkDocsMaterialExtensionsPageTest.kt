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

package org.pcsoft.ij.plugin.mkdocs.material.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureFlag

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the extensions page, and above all the one thing that makes it more than a list: *required* is a
 * statement about the current feature selection, not about the theme, so the page has to follow the features
 * page while both are open.
 */
class MkDocsMaterialExtensionsPageTest : BasePlatformTestCase() {

    /**
     * Use case: a site listing two extensions is opened and applied untouched. Both have to come back.
     */
    fun `test gives back unchanged what it was filled with`() {
        val page = MkDocsMaterialExtensionsPage()
        val settings = MkDocsMaterialSettings(extensions = setOf("admonition", "pymdownx.superfences"))

        page.reset(settings)

        assertEquals(settings, page.applyTo(settings))
        assertFalse(page.isModified(settings))
    }

    /**
     * Use case: the user enables an extension in its row. It has to appear in the snapshot.
     */
    fun `test carries an enabled extension into the snapshot`() {
        val page = MkDocsMaterialExtensionsPage()
        page.reset(MkDocsMaterialSettings.EMPTY)

        page.setEnabled(MkDocsMarkdownExtension.ADMONITION, true)

        assertTrue(page.isModified(MkDocsMaterialSettings.EMPTY))
        assertEquals(setOf("admonition"), page.applyTo(MkDocsMaterialSettings.EMPTY).extensions)
    }

    /**
     * Use case: the site lists an extension this plugin does not know about. It is not shown in the table,
     * and applying the page must not drop it.
     */
    fun `test keeps an extension it does not know`() {
        val page = MkDocsMaterialExtensionsPage()
        val settings = MkDocsMaterialSettings(extensions = setOf("mdx_truly_sane_lists"))
        page.reset(settings)

        page.setEnabled(MkDocsMarkdownExtension.ADMONITION, true)

        assertEquals(setOf("mdx_truly_sane_lists", "admonition"), page.applyTo(settings).extensions)
    }

    /**
     * Use case: the user ticks a feature on the features page while the extensions page is open. What the
     * feature needs has to turn from a recommendation into a requirement on the spot — the two pages are
     * shown next to each other, in the wizard as much as in the Project Structure dialog.
     */
    fun `test follows the selection of the features page`() {
        val pages = MkDocsMaterialSettingsPages()
        pages.reset(MkDocsMaterialSettings.EMPTY)

        val forced = MkDocsMaterialFeatureFlag.CONTENT_CODE_ANNOTATE.requiredExtensions
            .mapNotNull { MkDocsMarkdownExtension.byId(it) }
        assertTrue("the flag has to force something, or this test says nothing", forced.isNotEmpty())
        forced.forEach {
            assertNotSame(
                MkDocsMaterialExtensionsPage.Status.REQUIRED,
                pages.extensions.statusOf(it),
            )
        }

        pages.features.setSelectedForTest(MkDocsMaterialFeatureFlag.CONTENT_CODE_ANNOTATE, true)

        forced.forEach {
            assertEquals(MkDocsMaterialExtensionsPage.Status.REQUIRED, pages.extensions.statusOf(it))
            assertTrue(it in pages.extensions.requiredExtensions())
        }
    }
}
