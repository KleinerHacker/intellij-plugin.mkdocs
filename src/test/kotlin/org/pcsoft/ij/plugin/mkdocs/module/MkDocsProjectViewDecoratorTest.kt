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

package org.pcsoft.ij.plugin.mkdocs.module

import com.intellij.facet.FacetManager
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.LayeredIcon
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the decision logic of [MkDocsProjectViewDecorator]: which nodes are decorated at all, and what the
 * decoration consists of.
 */
class MkDocsProjectViewDecoratorTest : BasePlatformTestCase() {

    private val decorator = MkDocsProjectViewDecorator()

    /**
     * Use case: the project view shows the directory holding `mkdocs.yml`. It must read like a Maven project
     * directory — the site name in brackets behind the folder name — and carry the MkDocs badge.
     */
    fun `test decorates the site root with site name and badge`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        MkDocsModuleService.getInstance(project).sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEquals("My Documentation", data.locationString)
        assertTrue("expected the folder icon to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: any ordinary directory of the project. Without an MkDocs configuration file next to it there
     * is nothing to mark, and the node must be left exactly as the platform rendered it.
     */
    fun `test leaves a directory without configuration untouched`() {
        val file = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        val other = myFixture.addFileToProject("assets/logo.txt", "")
        MkDocsModuleService.getInstance(project).sync()
        assertNotNull(file)

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(other.virtualFile.parent), data)

        assertNull(data.locationString)
        assertSame(AllIcons.Nodes.Folder, data.getIcon(false))
    }

    /**
     * Use case: the configuration file itself is shown in the project view. The decorator addresses
     * directories only — a file node must never be touched, not even the `mkdocs.yml` of a detected site.
     */
    fun `test leaves file nodes untouched`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        MkDocsModuleService.getInstance(project).sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf<PsiFile>(configFile), data)

        assertNull(data.locationString)
    }

    /**
     * Use case: an `mkdocs.yml` exists on disk but the module carries no MkDocs facet — the detection has not
     * run yet, or it deliberately skipped this site. The project view must not invent a site name; it only
     * ever mirrors the detected module model.
     */
    fun `test does not decorate a module without the facet`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        removeFacet()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertNull(data.locationString)
    }

    /**
     * Use case: a facet whose site name is empty — a state the persisted `.iml` could carry. An empty pair of
     * brackets behind the directory name would be noise, so the node stays undecorated.
     */
    fun `test does not decorate for a blank site name`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        MkDocsModuleService.getInstance(project).sync()
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.siteName = "  "

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertNull(data.locationString)
    }

    /**
     * Use case: the platform did not provide an icon for the node. Adding the badge is then impossible, but
     * the site name must still be shown instead of the decoration failing altogether.
     */
    fun `test adds the site name even without a node icon`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        MkDocsModuleService.getInstance(project).sync()

        val data = PresentationData()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEquals("My Documentation", data.locationString)
        assertNull(data.getIcon(false))
    }

    /**
     * Drops the MkDocs facet from the fixture module, so a test starts from a module that is provably not an
     * MkDocs module — the light fixture project is shared between tests and may still carry one.
     */
    private fun removeFacet() {
        val facet = MkDocsFacet.getInstance(myFixture.module) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val model = FacetManager.getInstance(myFixture.module).createModifiableModel()
            model.removeFacet(facet)
            model.commit()
        }
    }

    private fun presentationWithFolderIcon(): PresentationData =
        PresentationData().apply { setIcon(AllIcons.Nodes.Folder) }

    private fun nodeOf(directory: VirtualFile): ProjectViewNode<*> =
        nodeOf(PsiManager.getInstance(project).findDirectory(directory)!!)

    private fun <T : Any> nodeOf(value: T): ProjectViewNode<*> =
        object : ProjectViewNode<T>(project, value, ViewSettings.DEFAULT) {
            override fun contains(file: VirtualFile): Boolean = false
            override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
            override fun update(presentation: PresentationData) = Unit
        }
}
