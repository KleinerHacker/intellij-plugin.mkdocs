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

package org.pcsoft.ij.plugin.mkdocs.module.facet.material

import com.intellij.facet.ui.FacetEditorTab
import com.intellij.ui.dsl.builder.panel
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import javax.swing.JComponent

/**
 * Project Structure tab of the Angular Material facet.
 *
 * The tab is informational: the theme is declared in `mkdocs.yml`, and the facet mirrors it. What the user can
 * do here is add or remove the facet itself, which is handled by the dialog rather than by this tab.
 *
 * @param configuration the facet configuration whose values are displayed
 */
class MkDocsMaterialFacetEditorTab(
    private val configuration: MkDocsMaterialFacetConfiguration,
) : FacetEditorTab() {

    override fun getDisplayName(): String = MkDocsBundle.message("facet.angularMaterial.tab.title")

    override fun createComponent(): JComponent = panel {
        row(MkDocsBundle.message("facet.angularMaterial.field.theme")) {
            label(configuration.themeName)
        }
        row {
            comment(MkDocsBundle.message("facet.angularMaterial.hint"))
        }
    }

    /** The tab never edits anything, so there is nothing that could become modified. */
    override fun isModified(): Boolean = false
}
