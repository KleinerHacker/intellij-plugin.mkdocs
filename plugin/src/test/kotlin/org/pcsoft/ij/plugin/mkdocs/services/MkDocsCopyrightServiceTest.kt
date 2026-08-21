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

package org.pcsoft.ij.plugin.mkdocs.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the notice the wizard falls back to when the IDE has none configured. Pure text assembly, so no
 * IntelliJ Platform is needed.
 */
class MkDocsCopyrightServiceTest {

    /**
     * Use case: the author was filled in on the site page. The fallback notice names them next to the year.
     */
    @Test
    fun `names the year and the author`() {
        assertEquals("© 2026 Acme", MkDocsCopyrightService.fallbackNotice("Acme", 2026))
    }

    /**
     * Use case: no author was entered. A notice ending in a dangling space would be worse than one naming
     * only the year, so the empty part is dropped.
     */
    @Test
    fun `omits a missing author`() {
        assertEquals("© 2026", MkDocsCopyrightService.fallbackNotice("   ", 2026))
        assertEquals("© 2026", MkDocsCopyrightService.fallbackNotice("", 2026))
    }
}
