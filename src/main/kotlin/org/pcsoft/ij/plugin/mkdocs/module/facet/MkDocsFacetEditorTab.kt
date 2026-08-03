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

package org.pcsoft.ij.plugin.mkdocs.module.facet

import com.intellij.facet.ui.FacetEditorTab
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import javax.swing.JComponent

/**
 * Project Structure tab of the MkDocs facet.
 *
 * Everything shown here is derived from the MkDocs configuration file, so the tab is purely informational:
 * the values are edited by editing `mkdocs.yml`, not here.
 *
 * @param configuration the facet configuration whose values are displayed
 */
class MkDocsFacetEditorTab(private val configuration: MkDocsFacetConfiguration) : FacetEditorTab() {

    override fun getDisplayName(): String = MkDocsBundle.message("facet.mkdocs.tab.title")

    override fun createComponent(): JComponent = panel {
        row(MkDocsBundle.message("facet.mkdocs.field.siteName")) {
            label(configuration.siteName)
        }
        row(MkDocsBundle.message("facet.mkdocs.field.configFile")) {
            label(configuration.configFilePath)
        }
        row {
            comment(MkDocsBundle.message("facet.mkdocs.hint"))
        }
    }

    /** The tab never edits anything, so there is nothing that could become modified. */
    override fun isModified(): Boolean = false
}
