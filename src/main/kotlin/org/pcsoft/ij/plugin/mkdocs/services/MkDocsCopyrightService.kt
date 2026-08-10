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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProfile
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProvider
import java.time.Year

/**
 * Collects the copyright notices the creation wizard can offer.
 *
 * Asks every registered [MkDocsCopyrightProvider]. With no provider registered — the Copyright plugin is
 * missing or switched off — the list stays empty and the wizard uses [fallbackNotice].
 *
 * @param project the project the wizard runs in
 */
@Service(Service.Level.PROJECT)
class MkDocsCopyrightService(private val project: Project) {

    companion object {

        /**
         * Returns the service instance for [project].
         *
         * @param project the project whose service is requested
         */
        @JvmStatic
        fun getInstance(project: Project): MkDocsCopyrightService = project.service()

        /**
         * Returns the notice used when the IDE has none configured.
         *
         * @param author the author entered in the wizard, which may be empty
         * @param year the year to name in the notice
         */
        @JvmStatic
        fun fallbackNotice(author: String, year: Int = Year.now().value): String =
            listOf("©", year.toString(), author.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Every configured notice, without duplicates by name, in the order the providers report them. */
    fun profiles(): List<MkDocsCopyrightProfile> =
        MkDocsCopyrightProvider.EP_NAME.extensionList
            .flatMap { provider -> runCatching { provider.profiles(project) }.getOrDefault(emptyList()) }
            .filter { it.notice.isNotBlank() }
            .distinctBy { it.name }

    /**
     * The notice marked as the default one, or `null` if the user marked none.
     *
     * A project without a default is exactly the case the wizard has to let the user decide.
     */
    fun defaultProfile(): MkDocsCopyrightProfile? =
        MkDocsCopyrightProvider.EP_NAME.extensionList
            .firstNotNullOfOrNull { provider -> runCatching { provider.defaultProfile(project) }.getOrNull() }
            ?.takeIf { it.notice.isNotBlank() }
}
