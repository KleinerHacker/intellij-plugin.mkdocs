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

import com.intellij.copyright.CopyrightManager
import com.intellij.openapi.project.Project
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.pattern.EntityUtil
import com.maddyhome.idea.copyright.pattern.VelocityHelper
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProfile
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProvider

/**
 * Reads the notices configured in the IDE's Copyright settings.
 *
 * Registered only through the optional descriptor that is loaded when the Copyright plugin is present.
 */
class MkDocsIdeCopyrightProvider : MkDocsCopyrightProvider {

    override fun profiles(project: Project): List<MkDocsCopyrightProfile> =
        CopyrightManager.getInstance(project).getCopyrights().mapNotNull { convert(project, it) }

    override fun defaultProfile(project: Project): MkDocsCopyrightProfile? =
        CopyrightManager.getInstance(project).defaultCopyright?.let { convert(project, it) }

    /**
     * Turns [profile] into what the wizard shows, or `null` if it carries no usable notice.
     *
     * The notice is stored encoded and written as a Velocity template — `$today.year` and friends only
     * become text once the template is evaluated. Evaluating it here is what keeps template syntax out of
     * the generated configuration file.
     *
     * @param project the project the notice is configured in, needed to resolve project variables
     * @param profile the notice as the Copyright plugin stores it
     */
    private fun convert(project: Project, profile: CopyrightProfile): MkDocsCopyrightProfile? {
        val raw = profile.notice?.takeIf { it.isNotBlank() } ?: return null
        val decoded = runCatching { EntityUtil.decode(raw) }.getOrDefault(raw)
        val evaluated = runCatching { VelocityHelper.evaluate(null, project, null, decoded) }.getOrDefault(decoded)
        val notice = MkDocsCopyrightProfile.singleLine(evaluated)
        if (notice.isEmpty()) return null
        return MkDocsCopyrightProfile(name = profile.name, notice = notice)
    }
}
