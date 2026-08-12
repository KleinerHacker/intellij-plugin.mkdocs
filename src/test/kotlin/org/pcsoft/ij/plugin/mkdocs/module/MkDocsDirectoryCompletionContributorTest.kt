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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what [MkDocsDirectoryCompletionContributor] offers in the *New Directory* dialog: the documentation
 * directory at the site root, and the assets and stylesheets directories inside it, each only while it is
 * still missing.
 */
class MkDocsDirectoryCompletionContributorTest : BasePlatformTestCase() {

    private val contributor = MkDocsDirectoryCompletionContributor()

    /**
     * Use case: a site whose documentation directory was never created — a checkout that lost the empty
     * folder, for instance. Creating a directory at the site root should offer exactly that name.
     */
    fun `test suggests the missing documentation directory`() {
        val root = addFile("handbook/mkdocs.yml", "site_name: Handbook\n").parent
        syncAndKeepFacet()

        val variants = contributor.getVariants(directoryOf(root))

        assertEquals(1, variants.size)
        assertEquals("docs", variants.first().path)
        assertSame(MkDocsIcons.DocsBadge, variants.first().icon)
    }

    /**
     * Use case: the same site with `docs_dir` pointing somewhere else. The suggestion has to be the
     * configured name, otherwise it would create a directory MkDocs never reads.
     */
    fun `test suggests the configured documentation directory`() {
        val root = addFile("renamed/mkdocs.yml", "site_name: Handbook\ndocs_dir: manual\n").parent
        syncAndKeepFacet()

        val variants = contributor.getVariants(directoryOf(root))

        assertEquals(listOf("manual"), variants.map { it.path })
    }

    /**
     * Use case: a complete site. The documentation directory already exists, so there is nothing left to
     * suggest at the root — an offer to create it would only be confusing.
     */
    fun `test suggests nothing when the documentation directory exists`() {
        val root = addFile("complete/mkdocs.yml", "site_name: Handbook\n").parent
        addFile("complete/docs/index.md")
        syncAndKeepFacet()

        assertEmpty(contributor.getVariants(directoryOf(root)))
    }

    /**
     * Use case: creating a directory inside the documentation directory of a site that has neither an assets
     * nor a stylesheets directory yet. That is the one place both of them belong.
     */
    fun `test suggests the missing assets and stylesheets directories`() {
        addFile("assets-missing/mkdocs.yml", "site_name: Handbook\n")
        val docs = addFile("assets-missing/docs/index.md").parent
        syncAndKeepFacet()
        configuration().assetsDirName = "assets"
        configuration().stylesheetsDirName = "stylesheets"

        val variants = contributor.getVariants(directoryOf(docs))

        assertEquals(listOf("assets", "stylesheets"), variants.map { it.path })
        assertSame(MkDocsIcons.AssetsBadge, variants.first().icon)
        assertSame(MkDocsIcons.StylesheetsBadge, variants.last().icon)
    }

    /**
     * Use case: a site that already carries its stylesheets directory but no assets directory. Creating one
     * of the two must not make the other vanish from the dialog, so exactly the missing one is offered.
     */
    fun `test suggests only the directory that is still missing`() {
        addFile("one-missing/mkdocs.yml", "site_name: Handbook\n")
        val docs = addFile("one-missing/docs/index.md").parent
        addFile("one-missing/docs/stylesheets/extra.css")
        syncAndKeepFacet()
        configuration().assetsDirName = "assets"
        configuration().stylesheetsDirName = "stylesheets"

        val variants = contributor.getVariants(directoryOf(docs))

        assertEquals(listOf("assets"), variants.map { it.path })
        assertSame(MkDocsIcons.AssetsBadge, variants.first().icon)
    }

    /**
     * Use case: a complete site. Both directories exist, so the dialog inside the documentation directory has
     * nothing left to offer.
     */
    fun `test suggests nothing when both directories exist`() {
        addFile("both-there/mkdocs.yml", "site_name: Handbook\n")
        val docs = addFile("both-there/docs/index.md").parent
        addFile("both-there/docs/assets/logo.png")
        addFile("both-there/docs/stylesheets/extra.css")
        syncAndKeepFacet()
        configuration().assetsDirName = "assets"
        configuration().stylesheetsDirName = "stylesheets"

        assertEmpty(contributor.getVariants(directoryOf(docs)))
    }

    /**
     * Use case: a site created with a differently named stylesheets directory. The facet remembers the name,
     * and the suggestion has to follow it rather than the convention.
     */
    fun `test suggests the remembered stylesheets directory`() {
        addFile("styles-renamed/mkdocs.yml", "site_name: Handbook\n")
        val docs = addFile("styles-renamed/docs/index.md").parent
        addFile("styles-renamed/docs/assets/logo.png")
        syncAndKeepFacet()
        configuration().assetsDirName = "assets"
        configuration().stylesheetsDirName = "css"

        assertEquals(listOf("css"), contributor.getVariants(directoryOf(docs)).map { it.path })
    }

    /**
     * Use case: a site created with a differently named assets directory. The facet remembers the name, and
     * the suggestion has to follow it rather than the convention.
     */
    fun `test suggests the remembered assets directory`() {
        addFile("assets-renamed/mkdocs.yml", "site_name: Handbook\n")
        val docs = addFile("assets-renamed/docs/index.md").parent
        addFile("assets-renamed/docs/stylesheets/extra.css")
        syncAndKeepFacet()
        configuration().assetsDirName = "media"
        configuration().stylesheetsDirName = "stylesheets"

        assertEquals(listOf("media"), contributor.getVariants(directoryOf(docs)).map { it.path })
    }

    /**
     * Use case: a directory somewhere inside a site that is neither the root nor the documentation
     * directory. Nothing about MkDocs applies there.
     */
    fun `test suggests nothing elsewhere in the site`() {
        addFile("elsewhere/mkdocs.yml", "site_name: Handbook\n")
        val guide = addFile("elsewhere/docs/guide/intro.md").parent
        syncAndKeepFacet()

        assertEmpty(contributor.getVariants(directoryOf(guide)))
    }

    /**
     * Use case: a plain directory of a module carrying no MkDocs facet. The dialog is opened all over the
     * project, and outside a site the plugin must stay silent.
     */
    fun `test suggests nothing without a facet`() {
        val plain = addFile("plain/notes.txt").parent
        removeFacet()

        assertEmpty(contributor.getVariants(directoryOf(plain)))
    }

    /**
     * Runs the detection and makes sure the fixture module ends up carrying the facet the contributor
     * requires — the light fixture project has a single module shared by every test.
     */
    private fun syncAndKeepFacet() {
        MkDocsModuleService.getInstance(project).sync()
        assertNotNull("the detection must have put an MkDocs facet on the fixture module", MkDocsFacet.getInstance(myFixture.module))
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

    /** The MkDocs configuration of the facet the fixture module carries. */
    private fun configuration() = MkDocsFacet.getInstance(myFixture.module)!!.configuration

    private fun directoryOf(file: VirtualFile): PsiDirectory =
        PsiManager.getInstance(project).findDirectory(file)!!

    private fun addFile(relativePath: String, text: String = ""): VirtualFile =
        myFixture.addFileToProject(relativePath, text).virtualFile
}
