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

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the assets page: the paths and the two reading keys go in and come back out, and a field left empty
 * means the key is absent rather than empty.
 */
class MkDocsMaterialAssetsPageTest : BasePlatformTestCase() {

    /**
     * Use case: a site naming a logo, a favicon, an override directory and a language is opened and applied
     * untouched. Nothing may change.
     */
    fun `test gives back unchanged what it was filled with`() {
        val page = MkDocsMaterialAssetsPage(project)
        val settings = MkDocsMaterialSettings(
            logo = "assets/logo.svg",
            favicon = "assets/favicon.png",
            customDir = "overrides",
            language = "de",
            direction = MkDocsMaterialAssetsPage.DIRECTION_RTL,
        )

        page.reset(settings)

        assertEquals(settings, page.applyTo(settings))
        assertFalse(page.isModified(settings))
    }

    /**
     * Use case: the user names a logo, a language and a writing direction on an empty site. All three have to
     * reach the snapshot.
     */
    fun `test carries typed values into the snapshot`() {
        val page = MkDocsMaterialAssetsPage(project)
        page.reset(MkDocsMaterialSettings.EMPTY)

        page.setLogoForTest("assets/logo.svg")
        page.setLanguageForTest("de")
        page.setDirectionForTest(MkDocsMaterialAssetsPage.DIRECTION_RTL)

        val applied = page.applyTo(MkDocsMaterialSettings.EMPTY)
        assertTrue(page.isModified(MkDocsMaterialSettings.EMPTY))
        assertEquals("assets/logo.svg", applied.logo)
        assertEquals("de", applied.language)
        assertEquals(MkDocsMaterialAssetsPage.DIRECTION_RTL, applied.direction)
    }

    /**
     * Use case: the user clears the language field. The key has to disappear rather than be written empty —
     * `theme.language: ''` says something different from a site not stating a language at all.
     */
    fun `test turns a cleared field into an absent key`() {
        val page = MkDocsMaterialAssetsPage(project)
        val settings = MkDocsMaterialSettings(language = "de")
        page.reset(settings)

        page.setLanguageForTest("   ")

        assertNull(page.applyTo(settings).language)
    }
}
