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

import java.io.File

/**
 * What is wrong with a program a user named by hand, as far as it can be said without running it.
 *
 * The settings page judges every keystroke, and starting a process for each of them is not something a field
 * may do. So the two questions are apart: this one answers what the file system alone already decides, and
 * whether the file is the program it is meant to be is decided by [MkDocsToolService] running it - which
 * happens when the page is opened, applied, or the search is asked to run again.
 */
object MkDocsToolInstallation {

    /**
     * What is wrong with a path naming a program.
     */
    enum class Problem {

        /** Nothing lies at that path. */
        NOT_FOUND,

        /** Something lies there, but it is a directory rather than a program. */
        NOT_A_FILE,

        /** A file lies there, but it may not be run. */
        NOT_EXECUTABLE,
    }

    /**
     * Returns what is wrong with [path], or `null` if nothing is.
     *
     * A blank path counts as [Problem.NOT_FOUND]: it only ever reaches this method from a field that stands
     * on "a program of my own", and an empty answer to that is not a way back to the automatic search but a
     * question left unanswered.
     *
     * @param path the path the user typed or chose
     */
    fun problemOf(path: String): Problem? {
        val file = File(path.trim().ifEmpty { return Problem.NOT_FOUND })
        return when {
            !file.exists() -> Problem.NOT_FOUND
            !file.isFile -> Problem.NOT_A_FILE
            !file.canExecute() -> Problem.NOT_EXECUTABLE
            else -> null
        }
    }
}
