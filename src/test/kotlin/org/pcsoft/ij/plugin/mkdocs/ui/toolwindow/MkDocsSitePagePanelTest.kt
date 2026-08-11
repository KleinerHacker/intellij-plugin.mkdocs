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

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what a tab of the tool window shows: the navigation of the site, or the reason there is none.
 */
class MkDocsSitePagePanelTest : BasePlatformTestCase() {

    /**
     * Use case: the common site. The tree shows the entries of `nav`, in the written order, and no message
     * takes the place of the tree.
     */
    fun `test shows the navigation of the site`() {
        myFixture.addFileToProject("docs/index.md", "# Home\n")
        myFixture.addFileToProject("docs/guide.md", "# Guide\n")
        val panel = panelFor("site_name: Handbook\nnav:\n  - index.md\n  - Guide: guide.md\n")

        assertEquals("", panel.tree.emptyText.text)
        val root = panel.tree.model.root as DefaultMutableTreeNode
        assertEquals(2, root.childCount)
        assertEquals("Home", labelAt(root, 0))
        assertEquals("Guide", labelAt(root, 1))
    }

    /**
     * Use case: a site without `nav`, which is the MkDocs default. The tab must say exactly that rather than
     * showing an empty tree without a reason, and must not invent a navigation from the file system.
     */
    fun `test reports a missing nav element`() {
        myFixture.addFileToProject("docs/index.md", "# Home\n")
        val panel = panelFor("site_name: Handbook\n")

        assertEquals(MkDocsBundle.message("toolwindow.sitePage.empty.noNav"), panel.tree.emptyText.text)
        assertEquals(0, (panel.tree.model.root as DefaultMutableTreeNode).childCount)
    }

    /**
     * Use case: `nav` is written but carries no entries yet. That is a different situation from a missing
     * `nav`, and the tab says so.
     */
    fun `test reports an empty nav element`() {
        myFixture.addFileToProject("docs/index.md", "# Home\n")
        val panel = panelFor("site_name: Handbook\nnav:\n")

        assertEquals(MkDocsBundle.message("toolwindow.sitePage.empty.emptyNav"), panel.tree.emptyText.text)
    }

    /**
     * Use case: `nav` written as something MkDocs would not accept, which happens while the file is being
     * edited. There is no navigation to show, and the tab reports it like a missing one.
     */
    fun `test reports an unusable nav element`() {
        myFixture.addFileToProject("docs/index.md", "# Home\n")
        val panel = panelFor("site_name: Handbook\nnav: index.md\n")

        assertEquals(MkDocsBundle.message("toolwindow.sitePage.empty.noNav"), panel.tree.emptyText.text)
    }

    /**
     * Use case: an entry of `nav` points nowhere. The tree keeps the entry and marks it, so the mistake is
     * visible instead of silently dropped.
     */
    fun `test keeps an entry pointing at a missing page`() {
        myFixture.addFileToProject("docs/index.md", "# Home\n")
        val panel = panelFor("site_name: Handbook\nnav:\n  - index.md\n  - gone.md\n")

        val root = panel.tree.model.root as DefaultMutableTreeNode
        assertEquals(2, root.childCount)
        assertFalse(nodeAt(root, 0).unresolved)
        assertTrue(nodeAt(root, 1).unresolved)
        assertEquals("gone", labelAt(root, 1))
    }

    /**
     * Creates a site from [configText], lets the detection pick it up, and returns a tab for it.
     *
     * @param configText the content of `mkdocs.yml`
     */
    private fun panelFor(configText: String): MkDocsSitePagePanel {
        myFixture.addFileToProject("mkdocs.yml", configText)
        MkDocsModuleService.getInstance(project).sync()

        val panel = MkDocsSitePagePanel(project, myFixture.module)
        Disposer.register(testRootDisposable, panel)
        panel.reloadNow()
        return panel
    }

    /**
     * Returns the label of the child of [parent] at [index].
     *
     * @param parent a node of the navigation tree
     * @param index position of the child
     */
    private fun labelAt(parent: DefaultMutableTreeNode, index: Int): String = nodeAt(parent, index).label

    /**
     * Returns the payload of the child of [parent] at [index].
     *
     * @param parent a node of the navigation tree
     * @param index position of the child
     */
    private fun nodeAt(parent: DefaultMutableTreeNode, index: Int): MkDocsNavTreeNode =
        (parent.getChildAt(index) as DefaultMutableTreeNode).userObject as MkDocsNavTreeNode
}
