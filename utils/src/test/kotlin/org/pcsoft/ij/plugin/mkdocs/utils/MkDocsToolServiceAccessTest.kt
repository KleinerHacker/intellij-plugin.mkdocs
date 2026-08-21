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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers where the tool service may start a process and where it may not, and which of two answers about the
 * same program wins. No program is actually run here: which of them lie on the machine running a build is not
 * something a test may depend on, so every finding is handed in.
 */
class MkDocsToolServiceAccessTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            service<MkDocsToolService>().invalidate()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a caller asking under a read action while nothing is known yet. No process may be started
     * there, so the answer is nothing rather than an error in the log — and nothing is remembered either,
     * because the question was never actually put.
     */
    fun `test asks for nothing under a read action`() {
        val found = runReadActionBlocking { service<MkDocsToolService>().detect(MkDocsTool.MKDOCS, project) }

        assertNull(found)
        assertFalse(service<MkDocsToolService>().isKnown(MkDocsTool.MKDOCS, project))
    }

    /**
     * Use case: the same question once the answer is known. A cached answer costs nothing, so it is handed
     * out under a read action as well — that is what makes a warmed up answer usable everywhere.
     */
    fun `test hands out a known answer under a read action`() {
        val info = MkDocsToolInfo(listOf("/opt/venv/bin/mkdocs"), "1.6.1", "3.13")
        service<MkDocsToolService>().overrideInfo(MkDocsTool.MKDOCS, "", info)

        assertTrue(service<MkDocsToolService>().isKnown(MkDocsTool.MKDOCS, project))
        assertEquals(
            info,
            runReadActionBlocking { service<MkDocsToolService>().detect(MkDocsTool.MKDOCS, project) },
        )
    }

    /**
     * Use case: a program that was asked about and is not there. That is an answer, not a missing one, and it
     * must not make the next caller run the search again — which is what would cost a process per keystroke.
     */
    fun `test remembers that a program is not there`() {
        service<MkDocsToolService>().overrideInfo(MkDocsTool.PIP, "", null)

        assertTrue(service<MkDocsToolService>().isKnown(MkDocsTool.PIP, project))
        assertNull(service<MkDocsToolService>().detect(MkDocsTool.PIP, project))
    }

    /**
     * Use case: the automatic finding and a program of one's own, both known. The settings page states both
     * at once — the entry naming what was found stays true next to a configured program — so the two answers
     * must not overwrite each other.
     */
    fun `test keeps the automatic finding next to a configured one`() {
        val automatic = MkDocsToolInfo(listOf("/usr/bin/python3"), "3.13.1", null)
        val own = MkDocsToolInfo(listOf("/opt/venv/bin/python"), "3.12.4", null)
        service<MkDocsToolService>().overrideInfo(MkDocsTool.PYTHON, "", automatic)
        service<MkDocsToolService>().overrideInfo(MkDocsTool.PYTHON, "/opt/venv/bin/python", own)

        assertEquals(automatic, service<MkDocsToolService>().detectAutomatic(MkDocsTool.PYTHON, project))
        assertEquals(own.command, service<MkDocsToolService>().cachedCommand(MkDocsTool.PYTHON))
    }

    /**
     * Use case: what [MkDocsPipService] builds its command line from. It carries no project to ask with, so
     * it takes what is cached — and a cold cache has to answer nothing rather than a guess, which is what
     * makes it fall back to the `PATH`.
     */
    fun `test names no command while nothing is known`() {
        assertNull(service<MkDocsToolService>().cachedCommand(MkDocsTool.PYTHON))
    }

    /**
     * Use case: an installation that changed — a new environment, a program installed since. Every answer
     * goes at once, including the ones about pip and MkDocs, because both are derived from the interpreter
     * and a changed interpreter makes them stale.
     */
    fun `test throws every answer away at once`() {
        service<MkDocsToolService>().overrideInfo(MkDocsTool.PYTHON, "", MkDocsToolInfo(listOf("py"), "3", null))
        service<MkDocsToolService>().overrideInfo(MkDocsTool.PIP, "", MkDocsToolInfo(listOf("pip"), "25", "3"))

        service<MkDocsToolService>().invalidate()

        assertFalse(service<MkDocsToolService>().isKnown(MkDocsTool.PYTHON, project))
        assertFalse(service<MkDocsToolService>().isKnown(MkDocsTool.PIP, project))
        assertNull(service<MkDocsToolService>().cachedCommand(MkDocsTool.PYTHON))
    }
}
