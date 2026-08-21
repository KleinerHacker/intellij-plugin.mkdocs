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

package org.pcsoft.ij.plugin.mkdocs.utils.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsToolInfo

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the field naming a program says: which program the automatic entry found, and which one is
 * actually run. Both are statements a user acts on — a build doing nothing is read against them — so the rule
 * behind them is pinned here rather than left to the UI.
 */
class MkDocsExecutableComboBoxTextsTest {

    private val texts = MkDocsExecutableComboBox.Texts(
        automatic = "Found automatically: {0} ({1})",
        automaticNone = "Found automatically: nothing found",
        custom = "A program of my own",
        inUse = "In use: {0}",
        inUseNone = "No program is in use.",
    )

    /**
     * Use case: the search found a program. The entry names it together with the version it answered with —
     * what makes a find one is that the program answered as itself, and a path alone would not say so.
     */
    @Test
    fun `the automatic entry names the program and its version`() {
        val found = MkDocsToolInfo(listOf("/opt/venv/bin/python"), "3.12.4", null)

        assertEquals("Found automatically: /opt/venv/bin/python (3.12.4)", texts.automaticFor(found))
    }

    /**
     * Use case: nothing was found. The entry has to say so, because an entry naming nothing would read as a
     * program whose path is merely missing from the line.
     */
    @Test
    fun `the automatic entry says when nothing was found`() {
        assertEquals("Found automatically: nothing found", texts.automaticFor(null))
    }

    /**
     * Use case: the field is left on the automatic entry. What is run is what was found, and the line says so
     * — naming a program is not the same statement as using it.
     */
    @Test
    fun `the line reports the found program while nothing is configured`() {
        val found = MkDocsToolInfo(listOf("/usr/bin/mkdocs"), "1.6.1", "3.13")

        assertEquals("In use: /usr/bin/mkdocs", texts.inUseFor(null, found))
    }

    /**
     * Use case: a program of one's own is configured. It wins over what the search found, which is the rule
     * the service follows, and the line has to report the same winner.
     */
    @Test
    fun `a configured program wins over the found one`() {
        val found = MkDocsToolInfo(listOf("/usr/bin/mkdocs"), "1.6.1", "3.13")

        assertEquals("In use: /opt/own/mkdocs", texts.inUseFor("/opt/own/mkdocs", found))
    }

    /**
     * Use case: neither a configured program nor one that was found — the state of a machine without Python.
     * The line says that nothing is run, which is the answer to a build that does nothing.
     */
    @Test
    fun `the line says when nothing is run at all`() {
        assertEquals("No program is in use.", texts.inUseFor(null, null))
    }
}
