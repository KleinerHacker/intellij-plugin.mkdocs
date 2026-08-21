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

package org.pcsoft.ij.plugin.mkdocs.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import javax.swing.JComponent

/**
 * The *MkDocs* page below *Tools*, which the settings of the features hang under.
 *
 * The page itself edits nothing: everything configurable belongs to one feature of a site, and a feature owns
 * its own settings. What it does is give those pages a place — a node named after the plugin, with one child
 * per feature, instead of a flat list of pages below *Tools* whose relation to each other is guesswork.
 *
 * It knows none of its children: a feature registers its page against the id of this one, which is the only
 * thing the plugin and a feature share here.
 */
class MkDocsSettingsConfigurable : Configurable {

    override fun getDisplayName(): String = MkDocsBundle.message("settings.title")

    override fun createComponent(): JComponent = panel {
        row { label(MkDocsBundle.message("settings.description")) }
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit
}
