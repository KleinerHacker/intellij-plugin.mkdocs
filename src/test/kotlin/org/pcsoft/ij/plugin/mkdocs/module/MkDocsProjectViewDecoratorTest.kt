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
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.LayeredIcon
import org.pcsoft.ij.plugin.mkdocs.MkDocsTextAttributes
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
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEquals("[My Documentation]", siteFragment(data))
        assertTrue("expected the folder icon to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: the directory name must survive the decoration. As soon as a coloured fragment exists the
     * platform stops rendering the plain presentable text, so the name has to be the first fragment.
     */
    fun `test keeps the directory name in front of the site name`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon().apply { presentableText = "docs" }
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEquals("docs [My Documentation]", data.coloredText.joinToString("") { it.text })
    }

    /**
     * Use case: any ordinary directory of the project. Without an MkDocs configuration file next to it there
     * is nothing to mark, and the node must be left exactly as the platform rendered it.
     */
    fun `test leaves a directory without configuration untouched`() {
        val file = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        val other = myFixture.addFileToProject("assets/logo.txt", "")
        project.service<MkDocsModuleService>().sync()
        assertNotNull(file)

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(other.virtualFile.parent), data)

        assertEmpty(data.coloredText)
        assertSame(AllIcons.Nodes.Folder, data.getIcon(false))
    }

    /**
     * Use case: the configuration file itself is shown in the project view. The decorator addresses
     * directories only — a file node must never be touched, not even the `mkdocs.yml` of a detected site.
     */
    fun `test leaves file nodes untouched`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf<PsiFile>(configFile), data)

        assertEmpty(data.coloredText)
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

        assertEmpty(data.coloredText)
    }

    /**
     * Use case: a facet whose site name is empty — a state the persisted `.iml` could carry. An empty pair of
     * brackets behind the directory name would be noise, so the node stays undecorated.
     */
    fun `test does not decorate for a blank site name`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        project.service<MkDocsModuleService>().sync()
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.siteName = "  "

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEmpty(data.coloredText)
    }

    /**
     * Use case: the platform did not provide an icon for the node. Adding the badge is then impossible, but
     * the site name must still be shown instead of the decoration failing altogether.
     */
    fun `test adds the site name even without a node icon`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        project.service<MkDocsModuleService>().sync()

        val data = PresentationData()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        assertEquals("[My Documentation]", siteFragment(data))
        assertNull(data.getIcon(false))
    }

    /**
     * Use case: the site name fragment is rendered with the plugin's own text attribute, which is defined in
     * the colour scheme files and therefore adapts to a light or dark theme.
     */
    fun `test renders the site name with the plugin text attribute`() {
        val configFile = myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.virtualFile.parent), data)

        val expected = MkDocsTextAttributes.asSimpleTextAttributes(MkDocsTextAttributes.SiteName)
        val actual = data.coloredText.last().attributes
        assertEquals(expected.style, actual.style)
        assertEquals(expected.fgColor, actual.fgColor)
    }

    /**
     * Use case: the documentation directory of a site. It gets its own badge — a different one from the site
     * root — but no name in brackets, because only the site root carries the site name.
     */
    fun `test badges the documentation directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val page = myFixture.addFileToProject("handbook/docs/index.md", "# Home\n")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(page.virtualFile.parent), data)

        assertEmpty(data.coloredText)
        assertTrue("expected the docs folder to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: a site pointing `docs_dir` at a directory of its own naming. The badge has to follow the
     * configuration, not the MkDocs default.
     */
    fun `test badges a renamed documentation directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: Handbook\ndocs_dir: sources\n")
        val page = myFixture.addFileToProject("handbook/sources/index.md", "# Home\n")
        val unrelated = myFixture.addFileToProject("handbook/docs/index.md", "# Home\n")
        project.service<MkDocsModuleService>().sync()

        val badged = presentationWithFolderIcon()
        decorator.decorate(nodeOf(page.virtualFile.parent), badged)
        assertTrue("expected the configured docs folder to be badged", badged.getIcon(false) is LayeredIcon)

        val plain = presentationWithFolderIcon()
        decorator.decorate(nodeOf(unrelated.virtualFile.parent), plain)
        assertSame("a directory named docs is not the docs dir here", AllIcons.Nodes.Folder, plain.getIcon(false))
    }

    /**
     * Use case: the assets directory inside the documentation directory. MkDocs has no key for it, so the
     * name comes from the facet — here the default.
     */
    fun `test badges the assets directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val asset = myFixture.addFileToProject("handbook/docs/assets/logo.txt", "")
        project.service<MkDocsModuleService>().sync()
        // The light fixture module is shared between tests and keeps whatever a previous test wrote into the
        // facet, so the default this test is about has to be stated explicitly.
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.assetsDirName = "assets"

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(asset.virtualFile.parent), data)

        assertTrue("expected the assets folder to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: the site was created with a differently named assets directory. The facet remembers the name,
     * and the badge has to follow it rather than the convention.
     */
    fun `test badges a renamed assets directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val asset = myFixture.addFileToProject("handbook/docs/media/logo.txt", "")
        val conventional = myFixture.addFileToProject("handbook/docs/assets/logo.txt", "")
        project.service<MkDocsModuleService>().sync()
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.assetsDirName = "media"

        val badged = presentationWithFolderIcon()
        decorator.decorate(nodeOf(asset.virtualFile.parent), badged)
        assertTrue("expected the configured assets folder to be badged", badged.getIcon(false) is LayeredIcon)

        val plain = presentationWithFolderIcon()
        decorator.decorate(nodeOf(conventional.virtualFile.parent), plain)
        assertSame("only the configured name counts", AllIcons.Nodes.Folder, plain.getIcon(false))
    }

    /**
     * Use case: the stylesheets directory inside the documentation directory. Like the assets directory it
     * has no MkDocs key, so the name comes from the facet — here the default.
     */
    fun `test badges the stylesheets directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val stylesheet = myFixture.addFileToProject("handbook/docs/stylesheets/extra.css", "")
        project.service<MkDocsModuleService>().sync()
        // The light fixture module is shared between tests and keeps whatever a previous test wrote into the
        // facet, so the default this test is about has to be stated explicitly.
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.stylesheetsDirName = "stylesheets"

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(stylesheet.virtualFile.parent), data)

        assertTrue("expected the stylesheets folder to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: the site was created with a differently named stylesheets directory. The facet remembers the
     * name, and the badge has to follow it rather than the convention.
     */
    fun `test badges a renamed stylesheets directory`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val styled = myFixture.addFileToProject("handbook/docs/css/extra.css", "")
        val conventional = myFixture.addFileToProject("handbook/docs/stylesheets/extra.css", "")
        project.service<MkDocsModuleService>().sync()
        MkDocsFacet.getInstance(myFixture.module)!!.configuration.stylesheetsDirName = "css"

        val badged = presentationWithFolderIcon()
        decorator.decorate(nodeOf(styled.virtualFile.parent), badged)
        assertTrue("expected the configured stylesheets folder to be badged", badged.getIcon(false) is LayeredIcon)

        val plain = presentationWithFolderIcon()
        decorator.decorate(nodeOf(conventional.virtualFile.parent), plain)
        assertSame("only the configured name counts", AllIcons.Nodes.Folder, plain.getIcon(false))
    }

    /**
     * Use case: a directory named like the stylesheets directory but sitting somewhere else in the site. Only
     * the one directly inside the documentation directory is the stylesheets directory.
     */
    fun `test leaves a directory named stylesheets elsewhere untouched`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val stray = myFixture.addFileToProject("handbook/docs/guide/stylesheets/extra.css", "")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(stray.virtualFile.parent), data)

        assertSame(AllIcons.Nodes.Folder, data.getIcon(false))
    }

    /**
     * Use case: a directory named like the assets directory but sitting somewhere else in the site. Only the
     * one directly inside the documentation directory is the assets directory.
     */
    fun `test leaves a directory named assets elsewhere untouched`() {
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: My Documentation\n")
        val stray = myFixture.addFileToProject("handbook/docs/guide/assets/logo.txt", "")
        project.service<MkDocsModuleService>().sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(stray.virtualFile.parent), data)

        assertSame(AllIcons.Nodes.Folder, data.getIcon(false))
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

    /** Returns the trimmed text of the last coloured fragment, i.e. the fragment carrying the site name. */
    private fun siteFragment(data: PresentationData): String? =
        data.coloredText.lastOrNull()?.text?.trim()

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
