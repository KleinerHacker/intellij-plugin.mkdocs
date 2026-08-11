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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacetEditorTab
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsSitesListener
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNav
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * One tab of the *Site Page* tool window: the navigation of a single MkDocs site.
 *
 * The tree shows `nav` as it stands in the configuration file — nothing more. A site without `nav` therefore
 * shows no tree but says so, rather than inventing a navigation out of the documentation directory: what the
 * tool window promises is the navigation of the site, and a site without `nav` has none written down.
 *
 * Reading the configuration file and the headings of the pages happens on a pooled thread and only the
 * finished nodes reach the event dispatch thread. Bursts of change notifications are merged, so saving a file
 * in a loop cannot turn into a series of rebuilds.
 *
 * @param project the project the site belongs to
 * @param module the module carrying the MkDocs facet of the site
 */
class MkDocsSitePagePanel(
    private val project: Project,
    private val module: Module,
) : SimpleToolWindowPanel(true, true), Disposable {

    /**
     * What a rebuild produced.
     *
     * @property root the root of the navigation tree, or `null` if there is no navigation to show
     * @property emptyText what to show instead of the tree, empty if there is a tree
     */
    data class State(val root: DefaultMutableTreeNode?, val emptyText: String)

    /** The tree showing the navigation. */
    val tree: Tree = Tree(DefaultTreeModel(DefaultMutableTreeNode()))

    private val refreshQueue = MergingUpdateQueue(REFRESH_QUEUE_NAME, REFRESH_MERGE_MILLIS, true, this, this)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = MkDocsNavTreeRenderer()
        tree.emptyText.text = MkDocsBundle.message("toolwindow.sitePage.empty.noNav")
        TreeUIHelper.getInstance().installTreeSpeedSearch(tree)

        installNavigation()
        setToolbar(createToolbar().component)
        setContent(ScrollPaneFactory.createScrollPane(tree, true))

        project.messageBus.connect(this).subscribe(
            MkDocsSitesListener.TOPIC,
            object : MkDocsSitesListener {
                override fun sitesChanged() = refresh()
                override fun pagesChanged() = refresh()
            },
        )

        refresh()
    }

    /**
     * Rebuilds the tree, off the event dispatch thread and merged with any rebuild already pending.
     */
    fun refresh() {
        if (project.isDisposed) return
        refreshQueue.queue(
            Update.create(REFRESH_TASK_ID) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    if (project.isDisposed || module.isDisposed) return@executeOnPooledThread
                    val state = computeState()
                    ApplicationManager.getApplication().invokeLater(
                        { if (!project.isDisposed) applyState(state) },
                        ModalityState.any(),
                    )
                }
            },
        )
    }

    /**
     * Reads the navigation of the site and builds the nodes for it.
     *
     * Blocking, and never to be called on the event dispatch thread: it reads the configuration file through
     * the PSI and the pages of the site through the virtual file system.
     */
    fun computeState(): State {
        val configFile = configFile()
            ?: return State(null, MkDocsBundle.message("toolwindow.sitePage.empty.noConfig"))

        val nav = runReadActionBlocking {
            if (project.isDisposed) null else MkDocsNav.read(project, configFile)
        } ?: return State(null, MkDocsBundle.message("toolwindow.sitePage.empty.noNav"))

        if (nav.isEmpty()) return State(null, MkDocsBundle.message("toolwindow.sitePage.empty.emptyNav"))

        val docsDir = configFile.parent?.let { siteRoot ->
            runReadActionBlocking {
                if (project.isDisposed) null else MkDocsLayout.docsDirOf(project, siteRoot)
            }
        }
        return State(MkDocsNavTreeBuilder.build(project, docsDir, nav), "")
    }

    /**
     * Puts [state] into the tree. Must be called on the event dispatch thread.
     *
     * @param state the result of [computeState]
     */
    fun applyState(state: State) {
        tree.emptyText.text = state.emptyText
        tree.model = DefaultTreeModel(state.root ?: DefaultMutableTreeNode())
        if (state.root != null) expandAll()
    }

    /**
     * Rebuilds the tree and waits for the result.
     *
     * Exists for tests, which cannot wait for the merged, asynchronous [refresh] without making themselves
     * dependent on timing.
     */
    @TestOnly
    fun reloadNow() {
        applyState(computeState())
    }

    override fun dispose() {
        // The update queue and the message bus connection hang off this panel and go with it.
    }

    /**
     * Returns the MkDocs configuration file of the site, or `null` if the module carries none any more.
     */
    private fun configFile(): VirtualFile? {
        if (module.isDisposed) return null
        val facet = MkDocsFacet.getInstance(module) ?: return null
        return MkDocsFacetEditorTab.findConfigFile(module, facet.configuration.configFilePath)
    }

    /**
     * Makes a double click and the enter key open what the selected node stands for.
     */
    private fun installNavigation() {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                if (tree.getPathForLocation(event.x, event.y) == null) return false
                return openSelected()
            }
        }.installOn(tree)

        DumbAwareAction.create { openSelected() }.registerCustomShortcutSet(CommonShortcuts.ENTER, tree)
    }

    /**
     * Opens what the selected node stands for.
     *
     * @return `true` if something was opened, `false` for a section, an unresolved page, or no selection
     */
    private fun openSelected(): Boolean {
        val node = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? MkDocsNavTreeNode
            ?: return false

        val file = node.file
        if (file != null && file.isValid) {
            OpenFileDescriptor(project, file).navigate(true)
            return true
        }
        val url = node.url
        if (url != null) {
            BrowserUtil.browse(url)
            return true
        }
        return false
    }

    /**
     * Builds the toolbar of the tab: a refresh action and the usual expand and collapse actions.
     */
    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup()
        group.add(RefreshAction())
        group.addSeparator()

        val expander = DefaultTreeExpander(tree)
        val actions = CommonActionsManager.getInstance()
        group.add(actions.createExpandAllAction(expander, tree))
        group.add(actions.createCollapseAllAction(expander, tree))

        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, false)
        toolbar.targetComponent = tree
        return toolbar
    }

    /**
     * Expands every row of the tree.
     *
     * A navigation is written to be read as a whole, and it is small — a collapsed tree would hide most of
     * what the tab exists for. Expanding grows the row count as it goes, which is why the loop re-reads it.
     */
    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    /**
     * Re-reads the navigation on demand.
     *
     * Changes reach the tree by themselves, but only once they are on disk. An unsaved configuration file is
     * what this action is for.
     */
    private inner class RefreshAction : DumbAwareAction(
        MkDocsBundle.message("toolwindow.sitePage.action.refresh.text"),
        MkDocsBundle.message("toolwindow.sitePage.action.refresh.description"),
        AllIcons.Actions.Refresh,
    ) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) = refresh()
    }

    private companion object {

        /** Time window in which incoming rebuild requests are merged into a single one. */
        const val REFRESH_MERGE_MILLIS = 300

        /** Name of the update queue, shown in thread dumps. */
        const val REFRESH_QUEUE_NAME = "MkDocsSitePageRefresh"

        /** Identity of the merged rebuild task inside the update queue. */
        const val REFRESH_TASK_ID = "mkdocs.site.page.refresh"

        /** Place the toolbar of the tab reports itself under. */
        const val TOOLBAR_PLACE = "MkDocsSitePageToolbar"
    }
}
