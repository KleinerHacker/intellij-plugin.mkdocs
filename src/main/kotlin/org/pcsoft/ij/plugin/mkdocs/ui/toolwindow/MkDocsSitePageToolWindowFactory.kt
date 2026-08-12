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

package org.pcsoft.ij.plugin.mkdocs.ui.toolwindow

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Builds the *Site Page* tool window: one tab per MkDocs site of the project.
 *
 * The tool window is available as soon as the project holds a site and disappears again with the last one,
 * which the availability listener keeps up to date. Reading the navigation needs no index, so the tool window
 * works while the IDE is still indexing.
 */
class MkDocsSitePageToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun shouldBeAvailable(project: Project): Boolean =
        MkDocsModuleService.getInstance(project).getMkDocsModules().isNotEmpty()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        syncContents(project, toolWindow)
    }

    companion object {

        /** Identity of the tool window; the stripe title comes from `toolwindow.stripe.MkDocs_Site_Page`. */
        const val ID: String = "MkDocs Site Page"

        /** The module a tab belongs to, so a tab can be found again without going by its title. */
        private val MODULE_KEY: Key<Module> = Key.create("mkdocs.site.page.module")

        /**
         * Brings the tabs of [toolWindow] in line with the MkDocs modules of [project].
         *
         * Tabs of modules that are still there are kept and only refreshed: replacing them would throw away
         * the scroll position and the selection of a tree the user is working with. Must be called on the
         * event dispatch thread.
         *
         * @param project the project the sites belong to
         * @param toolWindow the tool window to bring up to date
         */
        @JvmStatic
        fun syncContents(project: Project, toolWindow: ToolWindow) {
            if (project.isDisposed) return
            val manager = toolWindow.contentManager
            val modules = MkDocsModuleService.getInstance(project).getMkDocsModules()
                .filterNot { it.isDisposed }
            val titles = modules.groupingBy { siteNameOf(it) }.eachCount()

            for (content in manager.contents) {
                val module = content.getUserData(MODULE_KEY)
                if (module == null || module !in modules) {
                    manager.removeContent(content, true)
                }
            }

            for (module in modules) {
                val title = displayNameOf(module, titles)
                val existing = manager.contents.firstOrNull { it.getUserData(MODULE_KEY) == module }
                if (existing != null) {
                    existing.displayName = title
                    (existing.component as? MkDocsSitePagePanel)?.refresh()
                    continue
                }

                val panel = MkDocsSitePagePanel(project, module)
                val content = ContentFactory.getInstance().createContent(panel, title, false)
                content.isCloseable = false
                content.icon = MkDocsIcons.MkDocs
                content.description = MkDocsBundle.message("toolwindow.sitePage.tab.tooltip", module.name)
                content.putUserData(MODULE_KEY, module)
                content.setDisposer(panel)
                manager.addContent(content)
            }
        }

        /**
         * Returns the name of the site [module] carries.
         *
         * @param module a module carrying the MkDocs facet
         */
        private fun siteNameOf(module: Module): String =
            MkDocsFacet.getInstance(module)?.configuration?.siteName?.takeIf { it.isNotBlank() } ?: module.name

        /**
         * Returns the title of the tab of [module].
         *
         * Two sites may well be called the same — a monorepo with one handbook per component is the obvious
         * case. The module name then tells the tabs apart.
         *
         * @param module a module carrying the MkDocs facet
         * @param titles how often each site name occurs in the project
         */
        private fun displayNameOf(module: Module, titles: Map<String, Int>): String {
            val siteName = siteNameOf(module)
            if ((titles[siteName] ?: 0) <= 1) return siteName
            return MkDocsBundle.message("toolwindow.sitePage.tab.ambiguous", siteName, module.name)
        }
    }
}
