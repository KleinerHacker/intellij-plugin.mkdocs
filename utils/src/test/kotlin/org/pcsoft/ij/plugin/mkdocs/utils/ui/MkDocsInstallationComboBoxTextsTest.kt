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

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the installation field says: which directory the automatic entry names, and which one the line
 * below the field reports as the one being read. Both are statements a user acts on — an empty completion
 * popup is read against them — so the rule behind them is pinned here rather than left to the UI.
 */
class MkDocsInstallationComboBoxTextsTest {

    private val texts = MkDocsInstallationComboBox.Texts(
        automatic = "Detected by pip ({0})",
        automaticNone = "Detected by pip (nothing found)",
        custom = "Directory of my own",
        inUse = "The icons are read from: {0}",
        inUseNone = "No installation is being read.",
    )

    /**
     * Use case: pip reports an installation. The automatic entry names it rather than calling itself a
     * default, which is what tells a user the plugin found the environment they build the site with.
     */
    @Test
    fun `automatic entry names what was found`() {
        assertEquals("Detected by pip (/opt/material)", texts.automaticFor("/opt/material"))
    }

    /**
     * Use case: nothing is installed. The entry has to say so, because an entry naming nothing would read as
     * an installation whose name is missing.
     */
    @Test
    fun `automatic entry says when nothing was found`() {
        assertEquals("Detected by pip (nothing found)", texts.automaticFor(null))
    }

    /**
     * Use case: the field is left on the automatic entry. What is read is what was found, and the line says
     * so — naming an installation is not the same statement as using it.
     */
    @Test
    fun `the line reports the found installation while nothing is configured`() {
        assertEquals("The icons are read from: /opt/material", texts.inUseFor(null, "/opt/material"))
    }

    /**
     * Use case: a directory of one's own is configured and exists. It wins over what pip reported, which is
     * the rule the locator follows, and the line has to report the same winner.
     */
    @Test
    fun `a configured directory wins over the found one`() {
        assertEquals("The icons are read from: /own/icons", texts.inUseFor("/own/icons", "/opt/material"))
    }

    /**
     * Use case: neither a configured directory nor an installation — the state of a fresh checkout. The line
     * says that nothing is read, which is the answer to a completion popup offering no icons.
     */
    @Test
    fun `the line says when nothing is read at all`() {
        assertEquals("No installation is being read.", texts.inUseFor(null, null))
    }
}
