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

package org.pcsoft.ij.plugin.mkdocs.material.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers how a page names an icon: the shorthand of a name, the name behind a shorthand, and which of the
 * shorthands of a page are wrong. The last one is what the annotator marks, and the line between a misspelt
 * icon and an emoji of `pymdownx.emoji` is drawn here rather than in the annotator, because that line is the
 * whole decision.
 */
class MkDocsMaterialShorthandsTest {

    /**
     * Use case: an icon of a nested set. Every separator of the name becomes a dash, and the whole thing is
     * wrapped in the colons the theme reads it between.
     */
    @Test
    fun `test writes the shorthand of a name`() {
        assertEquals(":material-check:", MkDocsMaterialShorthands.shorthandOf("material/check"))
        assertEquals(
            ":fontawesome-brands-github:",
            MkDocsMaterialShorthands.shorthandOf("fontawesome/brands/github"),
        )
    }

    /**
     * Use case: the way back, which the drawing in front of a shorthand needs. It is resolved against the
     * installed names rather than guessed: `material-weather-sunny` could be split in a dozen places.
     */
    @Test
    fun `test reads the name behind a shorthand`() {
        assertEquals("material/check", MkDocsMaterialShorthands.nameOf(":material-check:", NAMES))
        assertNull(MkDocsMaterialShorthands.nameOf(":material-gone:", NAMES))
    }

    /**
     * Use case: a page with a typo in an icon of a known set. That is what the theme renders nothing for, and
     * the range of it is what gets marked.
     */
    @Test
    fun `test finds a misspelt icon of a known set`() {
        val text = "Neither :material-gone: nor anything else."

        val found = MkDocsMaterialShorthands.unknownIn(text, NAMES)

        assertEquals(1, found.size)
        assertEquals(":material-gone:", text.substring(found.first()))
    }

    /**
     * Use case: the emoji shorthands of `pymdownx.emoji`, written in exactly the same syntax. None of them
     * begins with a set of the theme, and marking them would put red under every emoji of the page.
     */
    @Test
    fun `test leaves an emoji shorthand alone`() {
        val text = "A page with :smile: and :thumbsup: in it."

        assertTrue(MkDocsMaterialShorthands.unknownIn(text, NAMES).isEmpty())
    }

    /**
     * Use case: a misspelt set. In a page it cannot be told from an emoji — both are a word between colons
     * that names no set of the theme — so it is left alone rather than guessed at.
     */
    @Test
    fun `test leaves a misspelt set alone`() {
        assertTrue(MkDocsMaterialShorthands.unknownIn("Written :materail-check: here.", NAMES).isEmpty())
    }

    /**
     * Use case: the icons the page names correctly, the nested ones included. Nothing is wrong with them.
     */
    @Test
    fun `test finds nothing wrong with the installed icons`() {
        val text = "Both :material-check: and :fontawesome-brands-github: are there."

        assertTrue(MkDocsMaterialShorthands.unknownIn(text, NAMES).isEmpty())
    }

    /**
     * Use case: a checkout without an installation. Nothing is known to be wrong then, because nothing is
     * known at all — reporting every shorthand of the page would be reporting the missing installation once
     * per line.
     */
    @Test
    fun `test finds nothing without an installation`() {
        assertTrue(MkDocsMaterialShorthands.unknownIn("Written :material-gone: here.", emptyList()).isEmpty())
    }

    private companion object {

        /** The names of an installation holding a flat set and a nested one. */
        val NAMES = listOf("material/check", "material/alert", "fontawesome/brands/github")
    }
}
