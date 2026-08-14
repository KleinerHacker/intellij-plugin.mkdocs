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

import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPage
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetEditorTab
import javax.swing.JComponent

/**
 * One Material settings page shown as a tab of the Angular Material facet.
 *
 * The configuration file is the single source of truth, not the persisted facet state: [reset] reads
 * `mkdocs.yml` and [isModified] compares against exactly that snapshot. A facet remembers only that the site
 * declares the Material theme, and a tab filling itself from remembered state would show a palette the file
 * stopped carrying two commits ago.
 *
 * [apply] writes the difference between the snapshot and what the page says, inside an undoable command of its
 * own. Nothing here touches the facet model, so [MkDocsMaterialFacetListener] — which only reacts to a facet
 * appearing or disappearing — has nothing to bounce back into the file.
 *
 * @param page the page this tab shows
 * @param editorContext the context of the edited facet, used to locate the configuration file
 */
class MkDocsMaterialSettingsEditorTab(
    private val page: MkDocsMaterialSettingsPage,
    private val editorContext: FacetEditorContext,
) : FacetEditorTab() {

    /** What the configuration file said when the tab was last filled; what an edit is compared against. */
    private var shown: MkDocsMaterialSettings = MkDocsMaterialSettings.EMPTY

    override fun getDisplayName(): String = page.title

    override fun createComponent(): JComponent {
        reset()
        return page.component()
    }

    override fun isModified(): Boolean = page.isModified(shown)

    override fun reset() {
        val configFile = configFile()
        shown = configFile
            ?.let { runReadActionBlocking { MkDocsMaterialConfig.read(editorContext.project, it) } }
            ?: MkDocsMaterialSettings.EMPTY
        page.reset(shown)
    }

    /**
     * Writes what the user changed on this page into the configuration file.
     *
     * Only the keys that actually differ are touched — see [MkDocsMaterialConfig.write] — so the comments of
     * the author, the keys of other plugins and the options below a Markdown extension survive an *Apply*
     * that changed a single colour.
     */
    override fun apply() {
        val configFile = configFile() ?: return
        val target = page.applyTo(shown)
        if (target == shown) return

        val project = editorContext.project
        val from = shown
        WriteCommandAction.runWriteCommandAction(
            project,
            MkDocsBundle.message("facet.angularMaterial.command.settings"),
            null,
            { MkDocsMaterialConfig.write(project, configFile, from, target) },
        )
        shown = target
    }

    /** The module the edited facet belongs to, or `null` while it is already gone. */
    private fun module(): Module? = editorContext.module.takeIf { !it.isDisposed }

    /** The configuration file of the site, or `null` if the module holds no MkDocs site. */
    private fun configFile(): VirtualFile? {
        val module = module() ?: return null
        val mkDocsFacet = runReadActionBlocking { MkDocsFacet.getInstance(module) } ?: return null
        return MkDocsFacetEditorTab.findConfigFile(module, mkDocsFacet.configuration.configFilePath)
    }
}
