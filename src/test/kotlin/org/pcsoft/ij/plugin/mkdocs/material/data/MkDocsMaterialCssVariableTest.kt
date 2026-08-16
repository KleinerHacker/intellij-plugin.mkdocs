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
 */
class MkDocsMaterialCssVariableTest {

    /**
     * Use case: the list comes from a bundled resource. A resource that is missing from the jar or does not
     * parse would silently switch the whole completion off.
     */
    @Test
    fun `the bundled resource parses`() {
        assertTrue(MkDocsMaterialCssVariables.all.isNotEmpty())
    }

    /**
     * Use case: the custom properties of the theme all carry the `--md-` prefix; anything else in the list
     * would be offered for a variable the theme does not define.
     */
    @Test
    fun `every variable carries the theme prefix`() {
        MkDocsMaterialCssVariables.all.forEach { variable ->
            assertTrue(variable.name, variable.name.startsWith(MkDocsMaterialCssVariables.PREFIX))
        }
    }

    /**
     * Use case: a duplicate entry would be offered twice in completion and would make the lookup by name
     * ambiguous.
     */
    @Test
    fun `names are unique`() {
        val names = MkDocsMaterialCssVariables.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    /**
     * Use case: every entry is documented in QuickDoc, so none may be shipped without a description key.
     */
    @Test
    fun `every variable carries a description key`() {
        MkDocsMaterialCssVariables.all.forEach { variable ->
            assertTrue(variable.name, variable.descriptionKey.isNotBlank())
        }
    }

    /**
     * Use case: the colour swatch is only offered for variables holding a colour. Shadows and fonts hold
     * something else and must not be marked as colours.
     */
    @Test
    fun `only colour variables are marked as colours`() {
        assertTrue(MkDocsMaterialCssVariables.byName("--md-primary-fg-color")?.isColor == true)
        assertTrue(MkDocsMaterialCssVariables.byName("--md-shadow-z1")?.isColor == false)
        assertTrue(MkDocsMaterialCssVariables.byName("--md-text-font")?.isColor == false)
    }

    /**
     * Use case: the settings page and QuickDoc list the variables per section, and every declared group has
     * to hold at least one variable.
     */
    @Test
    fun `every group holds at least one variable`() {
        MkDocsMaterialCssVariableGroup.entries.forEach { group ->
            assertTrue(group.name, MkDocsMaterialCssVariables.byGroup(group).isNotEmpty())
        }
    }

    /**
     * Use case: a name typed in a style sheet is resolved against the list; a property of the site itself
     * stays unresolved instead of being documented as one of the theme.
     */
    @Test
    fun `byName rejects unknown properties`() {
        assertNull(MkDocsMaterialCssVariables.byName("--md-nonsense-color"))
        assertNull(MkDocsMaterialCssVariables.byName("--my-own-color"))
    }
}
