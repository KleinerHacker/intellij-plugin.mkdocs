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

package org.pcsoft.ij.plugin.mkdocs.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the settings every feature shares: one entry per feature, and a blank path as the way back to the
 * automatic answer.
 */
class MkDocsInstallationSettingsTest {

    /**
     * Use case: a user configures the icon directory of the Material feature. It has to come back unchanged,
     * and it must not answer for another feature that keeps a path of its own.
     */
    @Test
    fun `keeps one path per feature`() {
        val settings = MkDocsInstallationSettings()

        settings.setPath("material", "/opt/material")
        settings.setPath("mike", "/opt/mike")

        assertEquals("/opt/material", settings.pathOf("material"))
        assertEquals("/opt/mike", settings.pathOf("mike"))
        assertEquals("", settings.pathOf("i18n"))
    }

    /**
     * Use case: a user clears the field to go back to what pip reports. The entry has to disappear rather
     * than become an empty path, which the locator would have to tell apart from a real one.
     */
    @Test
    fun `forgets a feature whose path is cleared`() {
        val settings = MkDocsInstallationSettings()
        settings.setPath("material", "/opt/material")

        settings.setPath("material", "   ")

        assertEquals("", settings.pathOf("material"))
        assertFalse(settings.paths.containsKey("material"))
    }

    /**
     * Use case: a path typed with blanks around it, which a text field hands on the way it was typed. What is
     * stored and what is read must be the path alone.
     */
    @Test
    fun `trims what is stored and what is read`() {
        val settings = MkDocsInstallationSettings()

        settings.setPath("material", "  /opt/material  ")

        assertEquals("/opt/material", settings.pathOf("material"))
    }

    /**
     * Use case: the platform reads the state of a project back into the service. Everything a project carried
     * has to arrive, because the state IS the object here.
     */
    @Test
    fun `takes over the state it is loaded with`() {
        val stored = MkDocsInstallationSettings()
        stored.setPath("material", "/opt/material")
        val settings = MkDocsInstallationSettings()

        settings.loadState(stored.state)

        assertEquals("/opt/material", settings.pathOf("material"))
    }
}
