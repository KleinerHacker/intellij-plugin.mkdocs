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

import com.intellij.facet.FacetConfiguration
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializerUtil
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.ui.MkDocsMaterialSettingsPages
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetEditorTab
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Persistent state of the Angular Material facet.
 *
 * The state is written into the module's `.iml` file, so a detected Material site survives an IDE restart even
 * before the detection had a chance to run again. As with the MkDocs facet, the configuration file stays the
 * source of truth — the detection overwrites this state whenever the two disagree.
 */
class MkDocsMaterialFacetConfiguration :
    FacetConfiguration,
    PersistentStateComponent<MkDocsMaterialFacetConfiguration.State> {

    /**
     * Serialized form of the facet.
     *
     * @property themeName the theme name found in the configuration file, normally
     *                     [MkDocsConfig.THEME_MATERIAL]. Remembered as written so the tab can show what the
     *                     site actually declares
     */
    class State {
        @JvmField
        var themeName: String = MkDocsConfig.THEME_MATERIAL
    }

    private var state = State()

    /** The theme name the site declares in its configuration file. */
    var themeName: String
        get() = state.themeName
        set(value) {
            state.themeName = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * Builds the tabs of the facet: the overview of what the site declares, and the four settings pages.
     *
     * The four pages are the very ones the site creation wizard shows — one implementation, two hosts. They
     * are created as a set rather than one by one, because the extensions page has to see what the features
     * page has ticked.
     */
    override fun createEditorTabs(
        editorContext: FacetEditorContext,
        validatorsManager: FacetValidatorsManager,
    ): Array<FacetEditorTab> {
        val pages = MkDocsMaterialSettingsPages(
            project = editorContext.project,
            docsDir = { docsDirOf(editorContext) },
            siteRoot = { configFileOf(editorContext)?.parent },
        )
        val tabs = mutableListOf<FacetEditorTab>(MkDocsMaterialFacetEditorTab(this))
        pages.pages.mapTo(tabs) { MkDocsMaterialSettingsEditorTab(it, editorContext) }
        return tabs.toTypedArray()
    }

    /**
     * The documentation directory of the edited site, or `null` while there is none.
     *
     * @param editorContext the context of the edited facet
     */
    private fun docsDirOf(editorContext: FacetEditorContext): VirtualFile? {
        val configFile = configFileOf(editorContext) ?: return null
        val root = configFile.parent ?: return null
        val name = runReadActionBlocking { MkDocsConfig.readDocsDir(editorContext.project, configFile) }
            ?: MkDocsProject.DEFAULT_DOCS_DIR
        return VfsUtilCore.findRelativeFile(name, root)?.takeIf { it.isDirectory }
    }

    /**
     * The configuration file of the edited site, or `null` if the module holds no MkDocs site.
     *
     * @param editorContext the context of the edited facet
     */
    private fun configFileOf(editorContext: FacetEditorContext): VirtualFile? {
        val module: Module = editorContext.module.takeIf { !it.isDisposed } ?: return null
        val mkDocsFacet = runReadActionBlocking { MkDocsFacet.getInstance(module) } ?: return null
        return MkDocsFacetEditorTab.findConfigFile(module, mkDocsFacet.configuration.configFilePath)
    }
}
