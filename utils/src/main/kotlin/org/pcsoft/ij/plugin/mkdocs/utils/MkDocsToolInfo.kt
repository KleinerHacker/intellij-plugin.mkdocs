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
 * What was found of one of the three programs of [MkDocsTool].
 *
 * A path alone would not be an answer: a file lying where an interpreter is expected proves nothing, and what
 * makes the find one is that the program answered `--version` as itself. So the version is part of the
 * finding rather than something asked for afterwards, and the settings page states both.
 *
 * @property command the command that answered, the executable first
 * @property version the version the program reports of itself
 * @property pythonVersion the interpreter the program named in its report, or `null` if it named none
 */
data class MkDocsToolInfo(
    val command: List<String>,
    val version: String,
    val pythonVersion: String?,
) {

    /**
     * The executable of [command], which is what a settings page shows and what a configured path replaces.
     */
    val executable: String
        get() = command.first()

    companion object {

        /**
         * The finding standing for "asked, and there is nothing".
         *
         * The difference between a question that was put and one that was not, which the cache of
         * [MkDocsToolService] has to keep apart and a `null` cannot express inside a map.
         */
        internal val NONE = MkDocsToolInfo(listOf(""), "", null)
    }
}
