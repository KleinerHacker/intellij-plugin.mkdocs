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

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSite
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteFeature
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Exercises the whole path behind *New → MkDocs Site* on a real project: writing the structure, the module
 * detection picking it up, and the facet ending up on a module. Needs a real file system, which is why this
 * is not a light fixture test — the site directory is usually created along with the site.
 */
class MkDocsSiteCreationIT : HeavyPlatformTestCase() {

    private val service: MkDocsSiteCreationService
        get() = MkDocsSiteCreationService.getInstance(project)

    /**
     * Use case: the user creates a site in a directory that belongs to no module — the ordinary case in a
     * project without a build system. Afterwards the site must be a full MkDocs module, with the structure
     * MkDocs expects, without any further action from the user.
     */
    fun `test creating a site produces an MkDocs module`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "handbook")

        val site = service.create(MkDocsSiteTemplate(rootPath = directory.toNioPath(), siteName = "Handbook"))

        assertEquals(directory, site.root)
        assertEquals("mkdocs.yml", site.configFile.name)
        assertEquals("site_name: Handbook\n", VfsUtilCore.loadText(site.configFile))
        assertEquals("# Handbook\n", VfsUtilCore.loadText(directory.findFileByRelativePath("docs/index.md")!!))
        assertNotNull(directory.findFileByRelativePath("docs/assets"))
        assertNotNull(
            "the empty assets directory needs a keep file to survive a commit",
            directory.findFileByRelativePath("docs/assets/${MkDocsSiteCreationService.VCS_KEEP_FILE}"),
        )

        val module = ModuleManager.getInstance(project).findModuleByName("Handbook")
        assertNotNull("the detection must have created a module for the new site", module)
        val facet = MkDocsFacet.getInstance(module!!)
        assertNotNull(facet)
        assertEquals("Handbook", facet!!.configuration.siteName)
        assertEquals("mkdocs.yml", facet.configuration.configFilePath)
        assertTrue(facet.configuration.ownsModule)
    }

    /**
     * Use case: the wizard appended the site name to the selected directory, so the target does not exist
     * yet — the normal case. The directory has to be created along with the site.
     */
    fun `test creating a site creates the target directory`() {
        val parent = getOrCreateProjectBaseDir()

        val site = service.create(
            MkDocsSiteTemplate(rootPath = parent.toNioPath().resolve("brand-new"), siteName = "Brand New"),
        )

        assertEquals("brand-new", site.root.name)
        assertNotNull(parent.findFileByRelativePath("brand-new/mkdocs.yml"))
        assertNotNull(parent.findFileByRelativePath("brand-new/docs/index.md"))
    }

    /**
     * Use case: the ordinary case in a real project — the target directory lies inside a module that already
     * exists, because the whole project directory is a content root. The site must then be marked on that
     * module; no new module is needed.
     */
    fun `test creating a site inside an existing module marks that module`() {
        val contentRoot = getOrCreateProjectBaseDir()
        PsiTestUtil.addContentRoot(module, contentRoot)

        val site = service.create(
            MkDocsSiteTemplate(rootPath = contentRoot.toNioPath().resolve("demo"), siteName = "demo"),
        )

        assertNotNull(site.configFile)
        val facet = MkDocsFacet.getInstance(module)
        assertNotNull("the surrounding module must carry the MkDocs facet", facet)
        assertEquals("demo", facet!!.configuration.siteName)
        assertEquals("mkdocs.yml", facet.configuration.configFilePath)
        assertFalse("the module already existed, it must not be owned", facet.configuration.ownsModule)
        assertEquals(listOf(module), MkDocsModuleService.getInstance(project).getMkDocsModules())
    }

    /**
     * Use case: a site created with renamed directories. `docs_dir` has to be written because MkDocs would
     * otherwise not find the sources; the assets directory has no MkDocs key and therefore lives in the
     * facet.
     */
    fun `test creating a site keeps renamed directories`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "manual")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Manual",
                docsDirName = "sources",
                assetsDirName = "media",
            ),
        )

        assertEquals("site_name: Manual\ndocs_dir: sources\n", VfsUtilCore.loadText(site.configFile))
        assertNotNull(directory.findFileByRelativePath("sources/index.md"))
        assertNotNull(directory.findFileByRelativePath("sources/media"))

        val module = ModuleManager.getInstance(project).findModuleByName("Manual")
        assertNotNull(module)
        assertEquals("media", MkDocsFacet.getInstance(module!!)!!.configuration.assetsDirName)
    }

    /**
     * Use case: a selected feature has to be applied to the finished site, so it can add its own entries to
     * `mkdocs.yml`. It must see a site that already exists — not a half-written one.
     */
    fun `test applies selected features to the created site`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "featured")
        val feature = RecordingFeature()

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Featured",
                features = listOf(feature),
            ),
        )

        assertEquals(1, feature.applied.size)
        assertEquals(site.configFile, feature.applied.single().configFile)
        assertTrue("the feature must see an existing configuration file", site.configFile.exists())
    }

    /**
     * Use case: something slipped through the wizard — a template that cannot be written must be refused by
     * the service as well, instead of leaving a half-created structure behind.
     */
    fun `test refuses an invalid template`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "taken")
        VfsTestUtil.createFile(directory, "mkdocs.yml", "site_name: Existing\n")

        assertThrows(IllegalArgumentException::class.java) {
            service.create(MkDocsSiteTemplate(rootPath = directory.toNioPath(), siteName = "Second"))
        }
    }

    /**
     * Use case: the action is invoked on a file rather than a directory — from *File → New* with a file
     * selected in the project view. The site belongs into the directory holding that file.
     */
    fun `test resolves the target directory of a file`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "notes")
        val file: VirtualFile = VfsTestUtil.createFile(directory, "note.md", "")

        assertEquals(directory, service.targetDirectoryOf(file))
        assertEquals(directory, service.targetDirectoryOf(directory))
        assertNull(service.targetDirectoryOf(null))
    }

    /** Test double recording every site it is applied to. */
    private class RecordingFeature : MkDocsSiteFeature {

        val applied = mutableListOf<MkDocsSite>()

        override val id: String = "test.recording"

        override val displayName: String = "Recording"

        override val description: String = "Records the sites it was applied to."

        override fun apply(project: Project, site: MkDocsSite) {
            applied += site
        }
    }
}
