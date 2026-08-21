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

/**
 * One of the three programs an MkDocs site is built with: the interpreter, its package manager, MkDocs itself.
 *
 * A feature of a site arrives as a Python distribution and is looked for with [MkDocsInstallationLocator];
 * these three are not features but the ground everything else stands on, and what has to be known about them
 * is not a directory but whether they are there at all and in which version. That is what [MkDocsToolService]
 * answers, and this enum is what a caller names its question with.
 *
 * Each entry carries two things: the key its configured path is kept under in [MkDocsInstallationSettings],
 * and how the version is read out of what `--version` writes. The three programs word that line differently -
 * `Python 3.13.1`, `pip 25.0.1 from ... (python 3.13)`, `mkdocs, version 1.6.1 from ... (Python 3.13)` - so
 * the pattern belongs to the entry rather than to the one place running the command.
 *
 * @property key the name the configured path of this program is kept under
 */
enum class MkDocsTool(val key: String) {

    /** The interpreter, which the other two are run through. */
    PYTHON("python") {
        override val versionPattern = Regex("""Python\s+(\S+)""")
    },

    /** The package manager, which installs MkDocs and every feature of a site. */
    PIP("pip") {
        override val versionPattern = Regex("""\bpip\s+(\d\S*)""")
    },

    /** MkDocs itself, which builds and serves a site. */
    MKDOCS("mkdocs") {
        override val versionPattern = Regex("""mkdocs,\s*version\s+([^\s,]+)""")
    },

    ;

    /** How the version of this program is read out of what `--version` writes. */
    protected abstract val versionPattern: Regex

    /**
     * Returns the version [output] reports, or `null` if it reports none.
     *
     * A command answering without a version it is known by is not this program: an entry point of another
     * name may lie on the `PATH` under `mkdocs`, and a report that cannot be read is how it shows itself.
     *
     * @param output everything the command wrote, standard output and error alike
     */
    fun parseVersion(output: String): String? =
        versionPattern.find(output)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    companion object {

        /** How the interpreter a program belongs to is named in its report, if it names one at all. */
        private val PYTHON_OF = Regex("""\(python\s+([^)]+)\)""", RegexOption.IGNORE_CASE)

        /**
         * Returns the interpreter version [output] names, or `null` if it names none.
         *
         * Both `pip` and `mkdocs` end their report with the interpreter they were run through. That is what
         * the settings page compares against the configured interpreter: a `pip` lying on the `PATH` may well
         * belong to another Python than the one a site is built with, and nothing but this line says so.
         *
         * @param output everything the command wrote, standard output and error alike
         */
        fun parsePythonVersion(output: String): String? =
            PYTHON_OF.find(output)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
