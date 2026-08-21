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

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the tool window against a real project on disk: several sites side by side, a site appearing and
 * one disappearing again, and the navigation being read end to end from `mkdocs.yml` down to the heading of
 * each page.
 */
class MkDocsSitePageToolWindowIT : HeavyPlatformTestCase() {

    private val service: MkDocsModuleService
        get() = project.service<MkDocsModuleService>()

    /**
     * Use case: a project without any MkDocs site. The tool window has nothing to show and must stay out of
     * the way rather than sitting on the stripe empty.
     */
    fun `test is unavailable without a site`() {
        assertFalse(MkDocsSitePageToolWindowFactory().shouldBeAvailable(project))
    }

    /**
     * Use case: the first site of a project arrives — through a checkout or the creation wizard. The tool
     * window becomes available as soon as the detection has seen it.
     */
    fun `test becomes available with the first site`() {
        createFile("handbook/mkdocs.yml", "site_name: Handbook\n")

        service.sync()

        assertTrue(MkDocsSitePageToolWindowFactory().shouldBeAvailable(project))
    }

    /**
     * Use case: a monorepo with two documentation sites. Each site is its own module and therefore gets a tab
     * of its own, each showing only its own navigation.
     */
    fun `test gives every site its own navigation`() {
        createFile("guide/mkdocs.yml", "site_name: Guide\nnav:\n  - index.md\n")
        createFile("guide/docs/index.md", "# Guide home\n")
        createFile("reference/mkdocs.yml", "site_name: Reference\nnav:\n  - api.md\n")
        createFile("reference/docs/api.md", "# API reference\n")

        service.sync()

        assertEquals("Guide home", singleLabelOf(moduleNamed("Guide")))
        assertEquals("API reference", singleLabelOf(moduleNamed("Reference")))
    }

    /**
     * Use case: the whole chain from the configuration file to the label of a node — a title from `nav`, a
     * heading of a page and a bare file name all in the same site, plus a section grouping them.
     */
    fun `test reads the navigation end to end`() {
        createFile(
            "handbook/mkdocs.yml",
            """
            site_name: Handbook
            nav:
              - Start: index.md
              - Guide:
                  - guide/install.md
                  - guide/plain.md
            """.trimIndent(),
        )
        createFile("handbook/docs/index.md", "# Ignored, nav wins\n")
        createFile("handbook/docs/guide/install.md", "# Installation\n")
        createFile("handbook/docs/guide/plain.md", "no heading here\n")

        service.sync()
        val root = rootOf(moduleNamed("Handbook"))

        assertEquals(2, root.childCount)
        assertEquals("Start", labelAt(root, 0))

        val section = root.getChildAt(1) as DefaultMutableTreeNode
        assertEquals("Guide", labelAt(root, 1))
        assertEquals(2, section.childCount)
        assertEquals("Installation", labelAt(section, 0))
        assertEquals("plain", labelAt(section, 1))
    }

    /**
     * Use case: a site whose `mkdocs.yml` carries no `nav`. The tab says so instead of showing a navigation
     * built out of the documentation directory, which the site never asked for.
     */
    fun `test reports a site without a navigation`() {
        createFile("handbook/mkdocs.yml", "site_name: Handbook\n")
        createFile("handbook/docs/index.md", "# Home\n")

        service.sync()
        val panel = panelFor(moduleNamed("Handbook"))

        assertEquals(MkDocsBundle.message("toolwindow.sitePage.empty.noNav"), panel.tree.emptyText.text)
    }

    /**
     * Use case: the documentation folder is removed from the working copy. Its module goes with it, so there
     * is no tab left to show and the tool window disappears again.
     */
    fun `test becomes unavailable when the last site disappears`() {
        val configFile = createFile("handbook/mkdocs.yml", "site_name: Handbook\n")
        service.sync()
        assertTrue(MkDocsSitePageToolWindowFactory().shouldBeAvailable(project))

        VfsTestUtil.deleteFile(configFile)
        service.sync()

        assertFalse(MkDocsSitePageToolWindowFactory().shouldBeAvailable(project))
    }

    /**
     * Returns the MkDocs module named [name].
     *
     * @param name the module name, which the detection takes from `site_name`
     */
    private fun moduleNamed(name: String): Module {
        val module = service.getMkDocsModules().firstOrNull { it.name == name }
        assertNotNull("expected an MkDocs module named $name", module)
        return module!!
    }

    /**
     * Builds the tab of [module] and returns it.
     *
     * @param module a module carrying the MkDocs facet
     */
    private fun panelFor(module: Module): MkDocsSitePagePanel {
        val panel = MkDocsSitePagePanel(project, module)
        Disposer.register(testRootDisposable, panel)
        panel.reloadNow()
        return panel
    }

    /**
     * Returns the root of the navigation tree of [module].
     *
     * @param module a module carrying the MkDocs facet
     */
    private fun rootOf(module: Module): DefaultMutableTreeNode =
        panelFor(module).tree.model.root as DefaultMutableTreeNode

    /**
     * Returns the label of the only entry of the navigation of [module].
     *
     * @param module a module carrying the MkDocs facet
     */
    private fun singleLabelOf(module: Module): String {
        val root = rootOf(module)
        assertEquals(1, root.childCount)
        return labelAt(root, 0)
    }

    /**
     * Returns the label of the child of [parent] at [index].
     *
     * @param parent a node of the navigation tree
     * @param index position of the child
     */
    private fun labelAt(parent: DefaultMutableTreeNode, index: Int): String =
        ((parent.getChildAt(index) as DefaultMutableTreeNode).userObject as MkDocsNavTreeNode).label

    private fun createFile(relativePath: String, text: String): VirtualFile =
        VfsTestUtil.createFile(getOrCreateProjectBaseDir(), relativePath, text)
}
