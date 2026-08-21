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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the settings page says about a program a user named by hand, as far as it can be said without
 * running it. This is the judgement made on every keystroke, and a wrong one either refuses a program that is
 * fine or accepts a path the build later fails on.
 */
class MkDocsToolInstallationTest {

    @get:Rule
    val folder = TemporaryFolder()

    /**
     * Use case: a path pointing at nothing — a typo, or an environment that has been deleted since. It is the
     * most common way to get this field wrong, and the page has to say so rather than accept it.
     */
    @Test
    fun `reports a path pointing at nothing`() {
        val missing = folder.root.resolve("python").absolutePath

        assertEquals(MkDocsToolInstallation.Problem.NOT_FOUND, MkDocsToolInstallation.problemOf(missing))
    }

    /**
     * Use case: the field is left empty while it stands on "a program of my own". That is not the way back to
     * the automatic search — leaving the entry is — but a question left unanswered, and applying on it would
     * store nothing while the page claims a program of one's own.
     */
    @Test
    fun `reports a blank path`() {
        assertEquals(MkDocsToolInstallation.Problem.NOT_FOUND, MkDocsToolInstallation.problemOf("   "))
    }

    /**
     * Use case: the directory of an environment chosen instead of the interpreter inside it. The chooser
     * offers files, but a path may be typed as well, and a directory is the mistake that reads most like a
     * correct answer.
     */
    @Test
    fun `reports a directory`() {
        val directory = folder.newFolder("venv").absolutePath

        assertEquals(MkDocsToolInstallation.Problem.NOT_A_FILE, MkDocsToolInstallation.problemOf(directory))
    }

    /**
     * Use case: a file that exists and may be run. Nothing more can be decided here — whether it really is
     * the program it was named as is decided by running it, which the page does when it is opened or applied.
     */
    @Test
    fun `accepts a file that may be run`() {
        val executable = folder.newFile("python")
        // A file created by the test carries no execute bit on POSIX, and it is being run that is judged.
        executable.setExecutable(true)

        assertNull(MkDocsToolInstallation.problemOf(executable.absolutePath))
    }

    /**
     * Use case: a file that may not be run — a text file picked by mistake on POSIX, where the execute bit is
     * what tells a program from a document. Windows knows no such bit and answers that everything may be run,
     * so the finding is only asserted where it can exist.
     */
    @Test
    fun `reports a file that may not be run`() {
        val document = folder.newFile("notes.txt")
        if (!document.setExecutable(false)) return

        assertEquals(
            MkDocsToolInstallation.Problem.NOT_EXECUTABLE,
            MkDocsToolInstallation.problemOf(document.absolutePath),
        )
    }
}
