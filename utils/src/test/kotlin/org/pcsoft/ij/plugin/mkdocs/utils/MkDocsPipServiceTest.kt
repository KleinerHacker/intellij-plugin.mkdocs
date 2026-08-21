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
 * Covers how the output of `pip show` is read. The call itself is not driven here: whether pip is on the
 * machine running the build is not something a test may depend on, while the parsing is where a wrong answer
 * would silently cost the feature its icons.
 */
class MkDocsPipServiceTest {

    /**
     * Use case: the answer of a Windows machine, which is what the report of `pip show mkdocs-material` looks
     * like on the developer machine this was written for. The `Location` line is the installation directory,
     * and it carries a drive letter and backslashes.
     */
    @Test
    fun `reads the location of a windows report`() {
        val output = """
            Name: mkdocs-material
            Version: 9.7.7
            Summary: Documentation that simply works
            Location: C:\Users\Chris\AppData\Roaming\Python\Python314\site-packages
            Requires: babel, backrefs, colorama
        """.trimIndent()

        assertEquals(
            """C:\Users\Chris\AppData\Roaming\Python\Python314\site-packages""",
            MkDocsPipService.parseLocation(output),
        )
    }

    /**
     * Use case: the same answer on Linux or macOS, where the path lies below the interpreter of a virtual
     * environment. Nothing about the parsing may depend on how a path is spelled.
     */
    @Test
    fun `reads the location of a posix report`() {
        val output = "Name: mkdocs-material\nLocation: /home/chris/.venv/lib/python3.12/site-packages\n"

        assertEquals("/home/chris/.venv/lib/python3.12/site-packages", MkDocsPipService.parseLocation(output))
    }

    /**
     * Use case: a distribution that is not installed. pip writes a warning and no report at all, and the
     * caller has to be told "not installed" rather than being handed a path that does not exist.
     */
    @Test
    fun `answers nothing without a location line`() {
        assertNull(MkDocsPipService.parseLocation("WARNING: Package(s) not found: mkdocs-material\n"))
        assertNull(MkDocsPipService.parseLocation(""))
    }

    /**
     * Use case: a report whose `Location` carries nothing but blanks, which is no answer either. Handing an
     * empty path on would make the locator ask the file system for the working directory.
     */
    @Test
    fun `answers nothing for a blank location`() {
        assertNull(MkDocsPipService.parseLocation("Name: mkdocs-material\nLocation:   \n"))
    }

    /**
     * Use case: a key that merely starts like the one that matters. `Location-Something` is not the
     * installation directory, and a report holding both must yield the real one.
     */
    @Test
    fun `reads the location and not a key that looks like it`() {
        val output = "Location: /opt/packages\nLocation-Editable: /elsewhere\n"

        assertEquals("/opt/packages", MkDocsPipService.parseLocation(output))
    }
}
