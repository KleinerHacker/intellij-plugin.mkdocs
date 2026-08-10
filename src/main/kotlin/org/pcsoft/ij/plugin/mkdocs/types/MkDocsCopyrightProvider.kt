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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * A copyright notice the user has already configured somewhere in the IDE.
 *
 * @property name the name the notice is configured under, shown when there is more than one
 * @property notice the notice itself, ready to be written into the configuration file
 */
data class MkDocsCopyrightProfile(val name: String, val notice: String) {

    companion object {

        /** Collapses every run of whitespace, including line breaks, into a single space. */
        private val WHITESPACE = Regex("\\s+")

        /**
         * Returns [notice] reduced to the single line MkDocs shows in the footer.
         *
         * A configured notice is usually a comment block of several lines, while `copyright` is one string —
         * so the lines are joined rather than kept.
         *
         * @param notice the notice as the IDE stores it
         */
        @JvmStatic
        fun singleLine(notice: String): String = notice.replace(WHITESPACE, " ").trim()
    }
}

/**
 * Reads the copyright notices configured in the IDE.
 *
 * The notices live in the Copyright plugin, which is bundled with every JetBrains IDE but can be switched
 * off. The extension point keeps the implementation in a module that is only loaded when the plugin is
 * present; without it the wizard falls back to a notice of its own.
 */
interface MkDocsCopyrightProvider {

    companion object {

        /** Where implementations register themselves. */
        @JvmField
        val EP_NAME: ExtensionPointName<MkDocsCopyrightProvider> =
            ExtensionPointName.create("org.pcsoft.ij.plugin.mkdocs.copyrightProvider")
    }

    /**
     * Returns every configured notice, in the order the IDE lists them.
     *
     * @param project the project whose configuration is read
     */
    fun profiles(project: Project): List<MkDocsCopyrightProfile>

    /**
     * Returns the notice marked as the default one.
     *
     * @param project the project whose configuration is read
     * @return the default notice, or `null` if none is marked
     */
    fun defaultProfile(project: Project): MkDocsCopyrightProfile?
}
