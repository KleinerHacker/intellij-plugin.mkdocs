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
 * Covers how the report of `--version` is read for each of the three programs. The call itself is not driven
 * here: which programs lie on the machine running the build is not something a test may depend on, while the
 * reading is where a wrong answer turns an installed program into a missing one.
 */
class MkDocsToolTest {

    /**
     * Use case: the interpreter answering. `python --version` writes one line and nothing else, and the
     * version out of it is what the settings page states next to the path.
     */
    @Test
    fun `reads the version of the interpreter`() {
        assertEquals("3.13.1", MkDocsTool.PYTHON.parseVersion("Python 3.13.1\n"))
    }

    /**
     * Use case: pip answering. Its report carries the path of the package and the interpreter it belongs to
     * behind the version, and neither of the two may be mistaken for the version.
     */
    @Test
    fun `reads the version of pip out of its long report`() {
        val output = "pip 25.0.1 from C:\\Python313\\Lib\\site-packages\\pip (python 3.13)\n"

        assertEquals("25.0.1", MkDocsTool.PIP.parseVersion(output))
    }

    /**
     * Use case: MkDocs answering. It words its line differently from the other two — `mkdocs, version 1.6.1` —
     * which is why the pattern belongs to the entry rather than to the one place running the command.
     */
    @Test
    fun `reads the version of mkdocs`() {
        val output = "mkdocs, version 1.6.1 from /usr/lib/python3.13/site-packages/mkdocs (Python 3.13)\n"

        assertEquals("1.6.1", MkDocsTool.MKDOCS.parseVersion(output))
    }

    /**
     * Use case: something answering that is not the program it was looked for as. An entry point of another
     * name may lie on the `PATH` under `mkdocs`, and a report that cannot be read is how it shows itself —
     * the candidate is then not a find but the next candidate's turn.
     */
    @Test
    fun `answers nothing for a report of another program`() {
        assertNull(MkDocsTool.MKDOCS.parseVersion("usage: mkdocs [-h]\n"))
        assertNull(MkDocsTool.PYTHON.parseVersion(""))
        assertNull(MkDocsTool.PIP.parseVersion("pip is not recognized\n"))
    }

    /**
     * Use case: reading which interpreter pip belongs to. A `pip` lying on the `PATH` may well belong to
     * another Python than the one a site is built with, and this line is the only place it says so.
     */
    @Test
    fun `reads the interpreter a program names`() {
        val pip = "pip 25.0.1 from C:\\Python313\\Lib\\site-packages\\pip (python 3.13)\n"
        val mkdocs = "mkdocs, version 1.6.1 from /usr/lib/mkdocs (Python 3.12)\n"

        assertEquals("3.13", MkDocsTool.parsePythonVersion(pip))
        assertEquals("3.12", MkDocsTool.parsePythonVersion(mkdocs))
    }

    /**
     * Use case: a report naming no interpreter at all, which the interpreter's own report never does. Nothing
     * may be invented there — the settings page states an interpreter only where one was named.
     */
    @Test
    fun `answers nothing where no interpreter is named`() {
        assertNull(MkDocsTool.parsePythonVersion("Python 3.13.1\n"))
    }

    /**
     * Use case: the key a configured path is kept under. It is written into `mkdocs.xml` and read back from
     * it, so renaming one silently drops the setting of every user who made it.
     */
    @Test
    fun `keeps the settings key of every program`() {
        assertEquals(listOf("python", "pip", "mkdocs"), MkDocsTool.entries.map { it.key })
    }
}
