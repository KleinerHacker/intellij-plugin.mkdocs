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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import com.intellij.ui.LayeredIcon
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the whole chain on a real project on disk: detection creates the module and its facet, and the
 * project view decoration follows that model — including the case of several sites side by side and of a
 * site that disappears again.
 */
class MkDocsProjectViewDecoratorIT : HeavyPlatformTestCase() {

    private val decorator = MkDocsProjectViewDecorator()

    /**
     * Use case: a documentation folder belonging to no module is checked out. Detection creates a module for
     * it, and from then on the project view shows the site name in brackets plus the badge — the same way a
     * Maven project directory is rendered.
     */
    fun `test shows site name and badge after detection created the module`() {
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")
        MkDocsModuleService.getInstance(project).sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(configFile.parent), data)

        assertEquals("[Handbook]", siteFragment(data))
        assertTrue("expected the folder icon to be badged", data.getIcon(false) is LayeredIcon)
    }

    /**
     * Use case: a monorepo with two documentation sites. Every site root is decorated with its own name, so
     * the two directories stay distinguishable in the project view.
     */
    fun `test decorates every site with its own name`() {
        val guide = createConfig("guide/mkdocs.yml", "site_name: Guide\n")
        val reference = createConfig("reference/mkdocs.yaml", "site_name: Reference\n")
        MkDocsModuleService.getInstance(project).sync()

        val guideData = presentationWithFolderIcon()
        decorator.decorate(nodeOf(guide.parent), guideData)
        val referenceData = presentationWithFolderIcon()
        decorator.decorate(nodeOf(reference.parent), referenceData)

        assertEquals("[Guide]", siteFragment(guideData))
        assertEquals("[Reference]", siteFragment(referenceData))
    }

    /**
     * Use case: the configuration file is deleted from the working copy. The directory is an ordinary folder
     * again and must lose its marking — a stale bracket would claim a site that no longer exists.
     */
    fun `test drops the decoration when the site disappears`() {
        val configFile = createConfig("handbook/mkdocs.yml", "site_name: Handbook\n")
        val siteRoot = configFile.parent
        MkDocsModuleService.getInstance(project).sync()

        VfsTestUtil.deleteFile(configFile)
        MkDocsModuleService.getInstance(project).sync()

        val data = presentationWithFolderIcon()
        decorator.decorate(nodeOf(siteRoot), data)

        assertEmpty(data.coloredText)
        assertSame(AllIcons.Nodes.Folder, data.getIcon(false))
    }

    /** Returns the trimmed text of the last coloured fragment, i.e. the fragment carrying the site name. */
    private fun siteFragment(data: PresentationData): String? =
        data.coloredText.lastOrNull()?.text?.trim()

    private fun createConfig(relativePath: String, text: String): VirtualFile =
        VfsTestUtil.createFile(getOrCreateProjectBaseDir(), relativePath, text)

    private fun presentationWithFolderIcon(): PresentationData =
        PresentationData().apply { setIcon(AllIcons.Nodes.Folder) }

    private fun nodeOf(directory: VirtualFile): ProjectViewNode<*> {
        val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)!!
        return object : ProjectViewNode<com.intellij.psi.PsiDirectory>(project, psiDirectory, ViewSettings.DEFAULT) {
            override fun contains(file: VirtualFile): Boolean = false
            override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
            override fun update(presentation: PresentationData) = Unit
        }
    }
}
