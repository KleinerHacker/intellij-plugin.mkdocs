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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsMaterialColorTest {

    /**
     * Use case: the identifiers become a JSON schema enumeration; a duplicate would make one entry
     * unreachable in completion.
     */
    @Test
    fun `identifiers are unique`() {
        val ids = MkDocsMaterialColor.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Use case: Material writes a colour of two words with a hyphen, as in `deep-purple`. Writing it with a
     * space would produce a palette the theme cannot resolve.
     */
    @Test
    fun `identifiers are lower case and hyphenated`() {
        val pattern = Regex("^[a-z]+(-[a-z]+)*$")
        MkDocsMaterialColor.entries.forEach { color ->
            assertTrue(color.id, pattern.matches(color.id))
            assertFalse(color.id, color.id.contains(' '))
        }
    }

    /**
     * Use case: a colour that is neither a primary nor an accent could never be offered anywhere — it would
     * be dead data.
     */
    @Test
    fun `every colour is usable in at least one role`() {
        MkDocsMaterialColor.entries.forEach { color ->
            assertTrue(color.id, color.primary || color.accent)
        }
    }

    /**
     * Use case: the swatch painted next to a colour is built from the RGB value, so it has to stay inside
     * the 24 bit range — a value outside it would paint a wrong or transparent swatch.
     */
    @Test
    fun `hex values are plain RGB`() {
        MkDocsMaterialColor.entries.forEach { color ->
            assertTrue(color.id, color.hex in 0x000000..0xFFFFFF)
        }
    }

    /**
     * Use case: reading `theme.palette.primary` resolves the value back to a colour, and an unknown value
     * stays unresolved so the annotator can report it.
     */
    @Test
    fun `byId round-trips and rejects unknown identifiers`() {
        MkDocsMaterialColor.entries.forEach { color ->
            assertEquals(color, MkDocsMaterialColor.byId(color.id))
        }
        assertNull(MkDocsMaterialColor.byId("deep purple"))
        assertNull(MkDocsMaterialColor.byId("turquoise"))
    }

    /**
     * Use case: the settings page fills two different drop downs. The accent list is the shorter one —
     * Material accepts neither the neutral colours nor black and white as an accent.
     */
    @Test
    fun `primaries and accents differ where Material says they do`() {
        assertTrue(MkDocsMaterialColor.primaries().contains(MkDocsMaterialColor.BLUE_GREY))
        assertFalse(MkDocsMaterialColor.accents().contains(MkDocsMaterialColor.BLUE_GREY))
        assertFalse(MkDocsMaterialColor.accents().contains(MkDocsMaterialColor.BLACK))
        assertFalse(MkDocsMaterialColor.accents().contains(MkDocsMaterialColor.WHITE))
        assertTrue(MkDocsMaterialColor.accents().contains(MkDocsMaterialColor.CUSTOM))
        assertTrue(MkDocsMaterialColor.accents().size < MkDocsMaterialColor.primaries().size)
    }
}
