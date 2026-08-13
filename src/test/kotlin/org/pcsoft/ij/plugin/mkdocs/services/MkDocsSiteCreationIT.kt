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
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.pcsoft.ij.plugin.mkdocs.module.facet.MkDocsFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.material.MkDocsMaterialFacet
import org.pcsoft.ij.plugin.mkdocs.module.facet.material.MkDocsMaterialSiteFeature
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
        assertEmpty(
            "the assets directory is created empty, without any placeholder file",
            directory.findFileByRelativePath("docs/assets")!!.children.toList(),
        )
        assertNotNull(directory.findFileByRelativePath("docs/stylesheets"))
        assertEmpty(
            "the stylesheets directory is created empty as well",
            directory.findFileByRelativePath("docs/stylesheets")!!.children.toList(),
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
     * otherwise not find the sources; the assets and stylesheets directories have no MkDocs key and therefore
     * live in the facet.
     */
    fun `test creating a site keeps renamed directories`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "manual")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Manual",
                docsDirName = "sources",
                assetsDirName = "media",
                stylesheetsDirName = "css",
            ),
        )

        assertEquals("site_name: Manual\ndocs_dir: sources\n", VfsUtilCore.loadText(site.configFile))
        assertNotNull(directory.findFileByRelativePath("sources/index.md"))
        assertNotNull(directory.findFileByRelativePath("sources/media"))
        assertNotNull(directory.findFileByRelativePath("sources/css"))

        val module = ModuleManager.getInstance(project).findModuleByName("Manual")
        assertNotNull(module)
        val configuration = MkDocsFacet.getInstance(module!!)!!.configuration
        assertEquals("media", configuration.assetsDirName)
        assertEquals("css", configuration.stylesheetsDirName)
    }

    /**
     * Use case: a site created inside a build system module. The wizard suggested that build system's output
     * directory, and `site_dir` has to be written so MkDocs actually builds there.
     */
    fun `test creating a site keeps a custom output directory`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "built")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Built",
                siteDirName = "target/docs",
            ),
        )

        assertEquals("site_name: Built\nsite_dir: target/docs\n", VfsUtilCore.loadText(site.configFile))
    }

    /**
     * Use case: a site left at the MkDocs default output directory. Repeating a default in the configuration
     * file tells the reader nothing, so the key is omitted.
     */
    fun `test creating a site omits the default output directory`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "plainbuild")

        val site = service.create(
            MkDocsSiteTemplate(rootPath = directory.toNioPath(), siteName = "Plain Build"),
        )

        assertEquals("site_name: Plain Build\n", VfsUtilCore.loadText(site.configFile))
    }

    /**
     * Use case: a site name carrying YAML syntax — a colon, a hash, a quote. Written as it is, such a name
     * would turn the generated file into something YAML reads differently, or refuses altogether.
     */
    fun `test creating a site quotes a site name carrying yaml syntax`() {
        val colon = service.create(
            MkDocsSiteTemplate(
                rootPath = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "colon").toNioPath(),
                siteName = "Handbook: The Guide",
            ),
        )
        assertEquals("site_name: 'Handbook: The Guide'\n", VfsUtilCore.loadText(colon.configFile))

        val hash = service.create(
            MkDocsSiteTemplate(
                rootPath = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "hash").toNioPath(),
                siteName = "Docs #1",
            ),
        )
        assertEquals("site_name: 'Docs #1'\n", VfsUtilCore.loadText(hash.configFile))

        val quote = service.create(
            MkDocsSiteTemplate(
                rootPath = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "quote").toNioPath(),
                siteName = "Ann's Docs",
            ),
        )
        assertEquals("site_name: 'Ann''s Docs'\n", VfsUtilCore.loadText(quote.configFile))
    }

    /**
     * Use case: the user filled in every optional page of the wizard. All of it has to reach the
     * configuration file, in the order the keys belong there — the site describes itself first, then its
     * repository, then its footer.
     */
    fun `test creating a site writes every optional key`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "complete")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Complete",
                siteAuthor = "Ada Lovelace",
                siteDescription = "Everything about the machine.",
                siteUrl = "https://example.org/docs",
                repoName = "acme/machine",
                repoUrl = "https://github.com/acme/machine",
                copyright = "© 2026 Acme",
            ),
        )

        assertEquals(
            "site_name: Complete\n" +
                "site_author: Ada Lovelace\n" +
                "site_description: Everything about the machine.\n" +
                "site_url: 'https://example.org/docs'\n" +
                "repo_name: acme/machine\n" +
                "repo_url: 'https://github.com/acme/machine'\n" +
                "copyright: '© 2026 Acme'\n",
            VfsUtilCore.loadText(site.configFile),
        )
    }

    /**
     * Use case: the user walked past every optional page without filling anything in. An empty key renders
     * an empty link or an empty description, so nothing at all must be written for those pages.
     */
    fun `test creating a site omits every empty optional key`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "minimal")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Minimal",
                siteAuthor = "   ",
                siteDescription = "",
                siteUrl = "",
                repoName = "",
                repoUrl = "",
                copyright = "",
            ),
        )

        assertEquals("site_name: Minimal\n", VfsUtilCore.loadText(site.configFile))
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

    /**
     * Use case: the user ticks *Angular Material* in the feature step of the wizard. The finished site has to
     * declare the Material theme and carry the facet right away, without waiting for the next detection run.
     *
     * The written file is checked in full, not just for a substring: the theme goes into a configuration file
     * the wizard has only just written, so a second write from the facet listener would show up here as a
     * duplicated key or a mangled mapping. The event queue is drained before the check, because that listener
     * does its work deferred.
     */
    fun `test creating a site with the angular material feature writes the theme`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "handbook")

        val site = service.create(
            MkDocsSiteTemplate(
                rootPath = directory.toNioPath(),
                siteName = "Handbook",
                features = listOf(MkDocsMaterialSiteFeature()),
            )
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals(
            "the feature adds exactly one theme key to what the wizard wrote",
            "site_name: Handbook\ntheme:\n  name: material",
            VfsUtilCore.loadText(site.configFile).trimEnd(),
        )

        val module = ModuleManager.getInstance(project).findModuleByName("Handbook")
        assertNotNull(module)
        assertNotNull(
            "the feature must attach the facet without waiting for the next detection run",
            MkDocsMaterialFacet.getInstance(module!!),
        )
    }

    /**
     * Use case: the wizard runs without the feature ticked, which is the default. Nothing may write a theme
     * into the new site then, and no Angular Material facet may appear.
     */
    fun `test creating a site without the feature writes no theme`() {
        val directory = VfsTestUtil.createDir(getOrCreateProjectBaseDir(), "plain")

        val site = service.create(MkDocsSiteTemplate(rootPath = directory.toNioPath(), siteName = "Plain"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("site_name: Plain\n", VfsUtilCore.loadText(site.configFile))

        val module = ModuleManager.getInstance(project).findModuleByName("Plain")
        assertNotNull(module)
        assertNull(MkDocsMaterialFacet.getInstance(module!!))
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
