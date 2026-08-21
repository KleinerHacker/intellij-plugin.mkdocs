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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the reading of the flat icon names as the tree they describe: which entries lie on a level, which of
 * them are groups, and what the sets of the installation are. This is what decides the completion popup, so
 * every case a name of the theme can take is pinned here.
 */
class MkDocsMaterialIconTreeTest {

    /**
     * Use case: the top level, the first thing a user picks. It holds the sets and nothing of what lies below
     * them, however deep the names go.
     */
    @Test
    fun `test offers the sets on the top level`() {
        val level = MkDocsMaterialIconTree.childrenOf(NAMES, "")

        assertEquals(listOf("fontawesome", "material", "octicons"), level.map { it.segment })
        assertTrue(level.all { it.group })
    }

    /**
     * Use case: a set that splits further, which `fontawesome` does. Its level is the split, not the icons —
     * those lie one level deeper and would be several hundred entries here.
     */
    @Test
    fun `test offers the nesting of a set as its own level`() {
        val level = MkDocsMaterialIconTree.childrenOf(NAMES, "fontawesome")

        assertEquals(listOf("brands", "regular"), level.map { it.segment })
        assertTrue(level.all { it.group })
        assertEquals("fontawesome/brands", level.first().path)
    }

    /**
     * Use case: the bottom of a walk. The entries are icons, they carry their whole path, and the segment is
     * what the popup writes.
     */
    @Test
    fun `test offers the icons of the deepest level`() {
        val level = MkDocsMaterialIconTree.childrenOf(NAMES, "fontawesome/brands")

        assertEquals(listOf("github", "gitlab"), level.map { it.segment })
        assertFalse(level.any { it.group })
        assertEquals("fontawesome/brands/github", level.first().path)
    }

    /**
     * Use case: a level holding both. Groups come first — what is picked more often stands higher — and each
     * kind is sorted by itself.
     */
    @Test
    fun `test sorts the groups in front of the icons`() {
        val names = listOf("set/alpha", "set/nested/one", "set/beta")

        val level = MkDocsMaterialIconTree.childrenOf(names, "set")

        assertEquals(listOf("nested", "alpha", "beta"), level.map { it.segment })
        assertEquals(listOf(true, false, false), level.map { it.group })
    }

    /**
     * Use case: a group asked about. Everything something lies below is one, the empty path included — that
     * is the level of the sets, and a caller must not have to special case the start of the walk.
     */
    @Test
    fun `test tells a group from an icon`() {
        assertTrue(MkDocsMaterialIconTree.isGroup(NAMES, ""))
        assertTrue(MkDocsMaterialIconTree.isGroup(NAMES, "fontawesome"))
        assertTrue(MkDocsMaterialIconTree.isGroup(NAMES, "fontawesome/brands"))
        assertFalse(MkDocsMaterialIconTree.isGroup(NAMES, "material/check"))
    }

    /**
     * Use case: a set that was misspelt. It is no group, which is what tells the completion to offer nothing
     * rather than to fall back to the level above.
     */
    @Test
    fun `test refuses a group that is none`() {
        assertFalse(MkDocsMaterialIconTree.isGroup(NAMES, "materail"))
    }

    /**
     * Use case: the sets of an installation, which the shorthands of a page are matched against. The nested
     * ones are in it as well — `fontawesome` and `fontawesome/brands` alike — because a written shorthand can
     * only be split at the longer one.
     */
    @Test
    fun `test names every group, the nested ones included`() {
        val groups = MkDocsMaterialIconTree.groups(NAMES)

        assertEquals(
            listOf("fontawesome", "fontawesome/brands", "fontawesome/regular", "material", "octicons"),
            groups,
        )
    }

    /**
     * Use case: a checkout whose theme is not installed. Every question is answered with nothing, and none of
     * them is an error — an empty popup is what a site without an installation offers.
     */
    @Test
    fun `test answers an empty installation with nothing`() {
        assertTrue(MkDocsMaterialIconTree.childrenOf(emptyList(), "").isEmpty())
        assertTrue(MkDocsMaterialIconTree.groups(emptyList()).isEmpty())
        assertFalse(MkDocsMaterialIconTree.isGroup(emptyList(), "material"))
    }

    private companion object {

        /** The names of an installation holding a flat set and a nested one. */
        val NAMES = listOf(
            "fontawesome/brands/github",
            "fontawesome/brands/gitlab",
            "fontawesome/regular/star",
            "material/alert",
            "material/check",
            "octicons/repo-16",
        )
    }
}
