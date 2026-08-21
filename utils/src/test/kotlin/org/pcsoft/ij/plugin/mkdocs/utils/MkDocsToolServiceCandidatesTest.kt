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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers which commands are tried for a program and in which order. The order is the whole answer: a machine
 * carrying several interpreters answers differently depending on which candidate is asked first, and a site
 * built with one interpreter while its features are read out of another is exactly the bug this order is
 * meant to make impossible.
 */
class MkDocsToolServiceCandidatesTest {

    /**
     * Use case: an activated virtual environment. It is what a user builds their site with, so its
     * interpreter is asked before anything on the `PATH` — and where it lies below the environment is
     * prescribed rather than searched for.
     */
    @Test
    fun `the interpreter of an activated environment comes first`() {
        val environment = mapOf("VIRTUAL_ENV" to "/home/chris/site/.venv")

        val candidates = MkDocsToolService.candidates(MkDocsTool.PYTHON, "", null, environment, false)

        assertEquals(listOf("/home/chris/site/.venv/bin/python"), candidates.first())
    }

    /**
     * Use case: the same environment on Windows, where the interpreter lies below `Scripts` and carries an
     * extension. Nothing about the answer may depend on the machine beyond that.
     */
    @Test
    fun `the environment is read with the layout of windows`() {
        val environment = mapOf("VIRTUAL_ENV" to """C:\site\.venv\""")

        val candidates = MkDocsToolService.candidates(MkDocsTool.PYTHON, "", null, environment, true)

        assertEquals(listOf("""C:\site\.venv\Scripts\python.exe"""), candidates.first())
    }

    /**
     * Use case: no environment activated. Then the `PATH` is all there is, and on Windows the launcher is
     * tried last — it answers for an installation none of the other names reaches.
     */
    @Test
    fun `falls back to the path and to the launcher on windows`() {
        val posix = MkDocsToolService.candidates(MkDocsTool.PYTHON, "", null, emptyMap(), false)
        val windows = MkDocsToolService.candidates(MkDocsTool.PYTHON, "", null, emptyMap(), true)

        assertEquals(listOf(listOf("python3"), listOf("python")), posix)
        assertEquals(listOf(listOf("python3"), listOf("python"), listOf("py", "-3")), windows)
    }

    /**
     * Use case: nothing is activated. `VIRTUAL_ENV` is then absent or empty, and an empty value must not turn
     * into a path built out of nothing — which would name the root of the file system.
     */
    @Test
    fun `no environment is read from a blank value`() {
        assertNull(MkDocsToolService.virtualEnv(emptyMap(), false))
        assertNull(MkDocsToolService.virtualEnv(mapOf("VIRTUAL_ENV" to "   "), false))
    }

    /**
     * Use case: pip and MkDocs once the interpreter is known. Both are run through it, so that all three
     * answers are about one and the same environment; the entry point on the `PATH` is only the fallback.
     */
    @Test
    fun `pip and mkdocs are run through the interpreter that was found`() {
        val python = listOf("/opt/venv/bin/python")

        val pip = MkDocsToolService.candidates(MkDocsTool.PIP, "", python, emptyMap(), false)
        val mkdocs = MkDocsToolService.candidates(MkDocsTool.MKDOCS, "", python, emptyMap(), false)

        assertEquals(listOf("/opt/venv/bin/python", "-m", "pip"), pip.first())
        assertEquals(listOf(listOf("pip"), listOf("pip3")), pip.drop(1))
        assertEquals(listOf("/opt/venv/bin/python", "-m", "mkdocs"), mkdocs.first())
        assertEquals(listOf(listOf("mkdocs")), mkdocs.drop(1))
    }

    /**
     * Use case: no interpreter was found. pip and MkDocs are then looked for on their own rather than not at
     * all — a machine with a system wide MkDocs and no interpreter this plugin recognises still works.
     */
    @Test
    fun `pip and mkdocs are looked for alone without an interpreter`() {
        assertEquals(
            listOf(listOf("pip"), listOf("pip3")),
            MkDocsToolService.candidates(MkDocsTool.PIP, "", null, emptyMap(), false),
        )
        assertEquals(
            listOf(listOf("mkdocs")),
            MkDocsToolService.candidates(MkDocsTool.MKDOCS, "", null, emptyMap(), false),
        )
    }

    /**
     * Use case: a program named by hand. It is the whole list, for every one of the three: a user who named a
     * program did not ask for a search, and falling back to one would build a site with a program they did
     * not name — silently, and with a different result than the page states.
     */
    @Test
    fun `a configured program is the only candidate`() {
        val environment = mapOf("VIRTUAL_ENV" to "/home/chris/site/.venv")

        val candidates =
            MkDocsToolService.candidates(MkDocsTool.PYTHON, "/usr/bin/python3.12", null, environment, false)

        assertEquals(listOf(listOf("/usr/bin/python3.12")), candidates)
    }

    /**
     * Use case: telling the answers apart in the cache. The automatic finding and the one of a configured
     * program are two answers about the same program, and the settings page states both at once — so they
     * must not overwrite each other.
     */
    @Test
    fun `the automatic answer and a configured one are cached apart`() {
        val automatic = MkDocsToolService.cacheKey(MkDocsTool.PYTHON, "")
        val configured = MkDocsToolService.cacheKey(MkDocsTool.PYTHON, "/usr/bin/python3.12")

        assertEquals(automatic, MkDocsToolService.cacheKey(MkDocsTool.PYTHON, ""))
        assert(automatic != configured)
        assert(MkDocsToolService.cacheKey(MkDocsTool.PIP, "") != automatic)
    }
}
