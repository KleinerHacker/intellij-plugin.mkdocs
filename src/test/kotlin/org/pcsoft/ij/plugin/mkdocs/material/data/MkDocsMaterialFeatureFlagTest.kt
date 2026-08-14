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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsMaterialFeatureFlagTest {

    /**
     * Use case: the identifiers end up in a JSON schema enumeration and in completion. A duplicate would make
     * one of the two entries unreachable, so every flag has to carry an identifier of its own.
     */
    @Test
    fun `identifiers are unique`() {
        val ids = MkDocsMaterialFeatureFlag.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * Use case: a flag is written into `theme.features` verbatim. Material spells every flag in lower case
     * with dots as separators, and a typo in the table would produce a configuration the theme ignores.
     */
    @Test
    fun `identifiers are lower case dotted names`() {
        val pattern = Regex("^[a-z]+(\\.[a-z]+)*$")
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            assertTrue(flag.id, pattern.matches(flag.id))
        }
    }

    /**
     * Use case: the settings page disables a check box when a conflicting flag is on. The declaration names
     * one side of the pair only, so the computed relation has to hold in both directions.
     */
    @Test
    fun `conflicts are symmetric`() {
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            flag.conflicts().forEach { other ->
                assertTrue(
                    "${other.id} does not conflict back with ${flag.id}",
                    other.conflicts().contains(flag)
                )
            }
        }
    }

    /**
     * Use case: a flag conflicting with itself could never be enabled at all — it would disable its own
     * check box. Such a declaration is always a copy and paste mistake.
     */
    @Test
    fun `no flag conflicts with itself`() {
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            assertTrue(flag.id, !flag.conflicts().contains(flag))
        }
    }

    /**
     * Use case: both declared conflicts are the ones Material documents, and they are visible from either
     * side even though only one side declares them.
     */
    @Test
    fun `documented conflicts are known from both sides`() {
        assertTrue(
            MkDocsMaterialFeatureFlag.NAVIGATION_EXPAND.conflicts()
                .contains(MkDocsMaterialFeatureFlag.NAVIGATION_PRUNE)
        )
        assertTrue(
            MkDocsMaterialFeatureFlag.NAVIGATION_PRUNE.conflicts()
                .contains(MkDocsMaterialFeatureFlag.NAVIGATION_EXPAND)
        )
        assertTrue(
            MkDocsMaterialFeatureFlag.TOC_FOLLOW.conflicts().contains(MkDocsMaterialFeatureFlag.TOC_INTEGRATE)
        )
    }

    /**
     * Use case: a dependency is resolved by identifier when the settings page decides whether a flag can be
     * switched on. An identifier that resolves to nothing would silently drop the dependency.
     */
    @Test
    fun `every requirement names a known flag`() {
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            flag.requires.forEach { id ->
                assertNotNull("${flag.id} requires unknown $id", MkDocsMaterialFeatureFlag.byId(id))
            }
            flag.conflictsWith.forEach { id ->
                assertNotNull("${flag.id} conflicts with unknown $id", MkDocsMaterialFeatureFlag.byId(id))
            }
        }
    }

    /**
     * Use case: the annotator turns the forced extensions of a flag into a quick fix. An identifier that is
     * not a known extension could never be added by that fix.
     */
    @Test
    fun `every forced extension names a known extension`() {
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            flag.requiredExtensions.forEach { id ->
                assertNotNull("${flag.id} requires unknown extension $id", MkDocsMarkdownExtension.byId(id))
            }
        }
    }

    /**
     * Use case: reading `theme.features` resolves each entry back to a flag, and an entry the theme does not
     * know has to stay unresolved so the annotator can report it.
     */
    @Test
    fun `byId round-trips and rejects unknown identifiers`() {
        MkDocsMaterialFeatureFlag.entries.forEach { flag ->
            assertEquals(flag, MkDocsMaterialFeatureFlag.byId(flag.id))
        }
        assertNull(MkDocsMaterialFeatureFlag.byId("navigation.nonsense"))
        assertNull(MkDocsMaterialFeatureFlag.byId(""))
    }

    /**
     * Use case: completion offers the identifiers, and it offers each one exactly once, in declaration order.
     */
    @Test
    fun `allIds lists every flag once`() {
        val allIds = MkDocsMaterialFeatureFlag.allIds()
        assertEquals(MkDocsMaterialFeatureFlag.entries.size, allIds.size)
        assertEquals(allIds.size, allIds.toSet().size)
        assertEquals(MkDocsMaterialFeatureFlag.entries.first().id, allIds.first())
    }
}
