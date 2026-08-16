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

package org.pcsoft.ij.plugin.mkdocs.material.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The service is constructed directly rather than looked up with `service<…>()`: it only reads bundled
 * classpath resources, so a running application is not needed to exercise what these tests are about.
 */
class MkDocsMaterialFontTest {

    private val fonts = MkDocsMaterialDataService().fonts

    /**
     * Use case: the resource is bundled with the plugin, so a missing or unreadable file would leave both
     * font drop downs of the settings page empty.
     */
    @Test
    fun `the bundled resource is read`() {
        assertTrue(fonts.all.isNotEmpty())
        assertNotNull(fonts.byId("Roboto"))
    }

    /**
     * Use case: the family name is written into `theme.font` verbatim and requested from Google Fonts under
     * exactly that name, so a duplicate would offer the same family twice in one drop down.
     */
    @Test
    fun `family names are unique`() {
        val ids = fonts.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Use case: a family offered for neither role could never appear in either drop down — it would be dead
     * data.
     */
    @Test
    fun `every family is offered for at least one role`() {
        fonts.all.forEach { font ->
            assertTrue(font.id, font.text || font.code)
        }
    }

    /**
     * Use case: the placeholder standing in for a hand typed family is the one entry with an empty name, and
     * it has to be offered for both roles — a site may name an unlisted family for text as well as for code.
     */
    @Test
    fun `exactly one placeholder exists and serves both roles`() {
        assertEquals(1, fonts.all.count { it.custom })
        val custom = requireNotNull(fonts.custom)
        assertEquals("", custom.id)
        assertTrue(custom.text)
        assertTrue(custom.code)
    }

    /**
     * Use case: the placeholder is offered last in both drop downs, so the curated families stay at the top
     * where a user looks for them.
     */
    @Test
    fun `the placeholder is offered last`() {
        assertEquals(fonts.custom, fonts.textFonts().last())
        assertEquals(fonts.custom, fonts.codeFonts().last())
    }

    /**
     * Use case: reading `theme.font.text` resolves the value back to a curated family. The placeholder must
     * never be the answer — its empty name would otherwise match a site that names no family at all.
     */
    @Test
    fun `byId resolves curated families only`() {
        fonts.all.filterNot { it.custom }.forEach { font ->
            assertEquals(font, fonts.byId(font.id))
        }
        assertNull(fonts.byId(""))
        assertNull(fonts.byId("Comic Sans MS"))
    }

    /**
     * Use case: the two drop downs are filled from disjoint lists — a proportional family offered for code
     * would render the code blocks of the site unreadable.
     */
    @Test
    fun `text and code families do not overlap`() {
        fonts.all.filterNot { it.custom }.forEach { font ->
            assertFalse(font.id, font.text && font.code)
        }
        assertTrue(fonts.textFonts().isNotEmpty())
        assertTrue(fonts.codeFonts().isNotEmpty())
    }
}
