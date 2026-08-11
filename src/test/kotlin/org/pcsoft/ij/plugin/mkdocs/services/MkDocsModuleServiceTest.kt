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

package org.pcsoft.ij.plugin.mkdocs.services

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the case in which the directory above `mkdocs.yml` already belongs to an IntelliJ module: no module
 * is created, the existing one is marked with the MkDocs facet.
 */
class MkDocsModuleServiceTest : BasePlatformTestCase() {

    private val service: MkDocsModuleService
        get() = MkDocsModuleService.getInstance(project)

    /**
     * Use case: a project containing an `mkdocs.yml`. The directory above it must be recognised as an MkDocs
     * module — here the existing module — and carry the site name from the configuration.
     */
    fun `test marks the surrounding module as an MkDocs module`() {
        myFixture.addFileToProject("mkdocs.yml", "site_name: My Documentation\n")

        service.sync()

        val facet = MkDocsFacet.getInstance(myFixture.module)
        assertNotNull("expected an MkDocs facet on the module", facet)
        assertEquals("My Documentation", facet!!.configuration.siteName)
        assertEquals("mkdocs.yml", facet.configuration.configFilePath)
        assertFalse("the module already existed, it must not be owned", facet.configuration.ownsModule)
        assertEquals(listOf(myFixture.module), service.getMkDocsModules())
    }

    /**
     * Use case: `mkdocs.yaml` is used instead of `mkdocs.yml`. Both spellings are valid for MkDocs and must
     * be detected alike.
     */
    fun `test detects the yaml spelling as well`() {
        myFixture.addFileToProject("mkdocs.yaml", "site_name: Handbook\n")

        service.sync()

        val facet = MkDocsFacet.getInstance(myFixture.module)
        assertNotNull(facet)
        assertEquals("mkdocs.yaml", facet!!.configuration.configFilePath)
    }

    /**
     * Use case: the user renames the site inside `mkdocs.yml`. A re-run of the detection has to pick the new
     * name up instead of keeping the stale one — the configuration file is the source of truth.
     */
    fun `test updates the site name after the configuration changed`() {
        val file = myFixture.addFileToProject("mkdocs.yml", "site_name: Old Name\n").virtualFile
        service.sync()
        assertEquals("Old Name", MkDocsFacet.getInstance(myFixture.module)!!.configuration.siteName)

        setText(file, "site_name: New Name\n")
        service.sync()

        assertEquals("New Name", MkDocsFacet.getInstance(myFixture.module)!!.configuration.siteName)
    }

    /**
     * Use case: the configuration file is deleted. The module is no longer an MkDocs module, so the facet has
     * to disappear again — a stale facet would keep offering MkDocs features for nothing.
     */
    fun `test removes the facet when the configuration is deleted`() {
        val file = myFixture.addFileToProject("mkdocs.yml", "site_name: Doomed\n").virtualFile
        service.sync()
        assertNotNull(MkDocsFacet.getInstance(myFixture.module))

        WriteCommandAction.runWriteCommandAction(project) { file.delete(this) }
        service.sync()

        assertNull(MkDocsFacet.getInstance(myFixture.module))
        assertTrue(service.getMkDocsModules().isEmpty())
    }

    /**
     * Use case: running detection twice without any change in between must not produce a second facet or
     * otherwise churn the module model.
     */
    fun `test is idempotent`() {
        myFixture.addFileToProject("mkdocs.yml", "site_name: Stable\n")

        service.sync()
        val first = MkDocsFacet.getInstance(myFixture.module)
        service.sync()
        val second = MkDocsFacet.getInstance(myFixture.module)

        assertNotNull(first)
        assertSame(first, second)
        assertEquals(1, service.getMkDocsModules().size)
    }

    /**
     * Use case: a build output directory contains a copy of the configuration file. Such a copy is an
     * artefact and must never turn into a module.
     */
    fun `test ignores configuration files in build outputs`() {
        myFixture.addFileToProject("build/mkdocs.yml", "site_name: Artefact\n")
        myFixture.addFileToProject("node_modules/pkg/mkdocs.yml", "site_name: Dependency\n")

        assertTrue(findSites().isEmpty())
    }

    /**
     * Use case: several sites live in the same module (`docs/` and `handbook/`). Detection reports both — the
     * module model then gives the second one a module of its own.
     */
    fun `test finds every site in the project`() {
        myFixture.addFileToProject("docs/mkdocs.yml", "site_name: Docs\n")
        myFixture.addFileToProject("handbook/mkdocs.yml", "site_name: Handbook\n")

        assertEquals(listOf("Docs", "Handbook"), findSites().map { it.siteName }.sorted())
    }

    /**
     * Use case: a directory holds both `mkdocs.yml` and `mkdocs.yaml`. That is a single site, and it must
     * always be the same one of the two files that is picked — MkDocs loads `mkdocs.yml` first, so the facet
     * has to name that file instead of whichever the file system happened to hand out first.
     */
    fun `test prefers the yml spelling when both exist`() {
        myFixture.addFileToProject("mkdocs.yaml", "site_name: Yaml\n")
        myFixture.addFileToProject("mkdocs.yml", "site_name: Yml\n")

        val sites = findSites()

        assertEquals(1, sites.size)
        assertEquals("mkdocs.yml", sites.single().configFile.name)
        assertEquals("Yml", sites.single().siteName)
    }

    /**
     * Use case: the tool window has to learn when to re-read the navigation of a site. Every scan therefore
     * reports itself on the message bus — also when the set of modules came out unchanged, because the
     * content of a configuration file may well have changed without adding or removing a site.
     */
    fun `test reports every scan on the message bus`() {
        myFixture.addFileToProject("mkdocs.yml", "site_name: My Documentation\n")
        var reported = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            MkDocsSitesListener.TOPIC,
            object : MkDocsSitesListener {
                override fun sitesChanged() {
                    reported++
                }
            },
        )

        service.sync()
        service.sync()

        assertEquals(2, reported)
    }

    private fun findSites(): List<MkDocsSite> = runReadActionBlocking { service.findSites() }

    /**
     * Replaces the content of [file] through its document, so the open PSI is reparsed immediately — writing
     * the bytes into the virtual file directly would leave the PSI on the old text.
     */
    private fun setText(file: VirtualFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            document.setText(text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
