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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers which files [MkDocsFileIconProvider] claims: MkDocs configuration files and the Markdown pages
 * below the documentation directory of a site. Everything else is left to the platform.
 */
class MkDocsFileIconProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsFileIconProvider()

    /**
     * Use case: the configuration file of a site is shown in the project view. It must stand out from the
     * other YAML files of the project, so it gets the MkDocs configuration icon.
     */
    fun `test replaces the icon of mkdocs yml`() {
        val file = addFile("mkdocs.yml")

        assertSame(MkDocsIcons.ConfigFile, provider.getIcon(file, 0, project))
    }

    /**
     * Use case: the site uses the `mkdocs.yaml` spelling. MkDocs accepts both, so both must be iconised
     * alike.
     */
    fun `test replaces the icon of mkdocs yaml`() {
        val file = addFile("mkdocs.yaml")

        assertSame(MkDocsIcons.ConfigFile, provider.getIcon(file, 0, project))
    }

    /**
     * Use case: the file was created on a case insensitive file system as `MkDocs.YML`. MkDocs would load it,
     * so the IDE has to recognise it as well.
     */
    fun `test recognises the file name case insensitively`() {
        val file = addFile("MkDocs.YML")

        assertSame(MkDocsIcons.ConfigFile, provider.getIcon(file, 0, project))
    }

    /**
     * Use case: any other YAML file of the project, including one whose name merely contains "mkdocs". It is
     * no MkDocs configuration and must keep the icon the platform gives it.
     */
    fun `test leaves other yaml files alone`() {
        assertNull(provider.getIcon(addFile("config.yml"), 0, project))
        assertNull(provider.getIcon(addFile("mkdocs.base.yml"), 0, project))
        assertNull(provider.getIcon(addFile("mkdocs.txt"), 0, project))
    }

    /**
     * Use case: a directory named like a configuration file. Only files carry the configuration icon — a
     * directory must keep its folder icon.
     */
    fun `test leaves directories alone`() {
        val directory = addFile("mkdocs.yml/nested.txt").parent

        assertNull(provider.getIcon(directory, 0, project))
    }

    /**
     * Use case: an icon is requested outside any project, for example from a file chooser. For the
     * configuration file the decision depends on the file name alone, so it works without a project.
     */
    fun `test works without a project`() {
        val file = addFile("mkdocs.yml")

        assertSame(MkDocsIcons.ConfigFile, provider.getIcon(file, 0, null))
    }

    /**
     * Use case: the start page of a site. It is a page MkDocs publishes, so it carries the page icon rather
     * than the generic Markdown one.
     */
    fun `test marks a page in the documentation directory`() {
        addFile("pages/mkdocs.yml")
        val page = addFile("pages/docs/index.md")

        assertSame(MkDocsIcons.MarkdownFile, provider.getIcon(page, 0, project))
    }

    /**
     * Use case: a page filed away in a subdirectory of the documentation directory. MkDocs publishes it just
     * the same, however deeply it is buried, so the icon must follow recursively.
     */
    fun `test marks a page nested below the documentation directory`() {
        addFile("nested/mkdocs.yml")
        val page = addFile("nested/docs/guide/advanced/tuning.md")

        assertSame(MkDocsIcons.MarkdownFile, provider.getIcon(page, 0, project))
    }

    /**
     * Use case: a site pointing `docs_dir` somewhere else. The pages live where the configuration says, not
     * where the convention would put them.
     */
    fun `test follows a configured documentation directory`() {
        addFile("renamed/mkdocs.yml", "site_name: Handbook\ndocs_dir: manual\n")
        val page = addFile("renamed/manual/index.md")
        val outside = addFile("renamed/docs/index.md")

        assertSame(MkDocsIcons.MarkdownFile, provider.getIcon(page, 0, project))
        assertNull("only the configured directory holds pages", provider.getIcon(outside, 0, project))
    }

    /**
     * Use case: a Markdown file in the site root, such as a README. It sits outside the documentation
     * directory, is never published, and must keep the platform icon.
     */
    fun `test leaves markdown outside the documentation directory alone`() {
        addFile("readme/mkdocs.yml")
        val readme = addFile("readme/README.md")

        assertNull(provider.getIcon(readme, 0, project))
    }

    /**
     * Use case: a Markdown file in a project without any MkDocs site. Nothing here is a page, so the platform
     * icon stays.
     */
    fun `test leaves markdown outside a site alone`() {
        val loose = addFile("plain/notes.md")

        assertNull(provider.getIcon(loose, 0, project))
    }

    /**
     * Use case: an icon for a page is requested without a project. Identifying the site needs the PSI of the
     * configuration file, which there is no project to read from — so no icon is claimed.
     */
    fun `test claims no page without a project`() {
        addFile("noproject/mkdocs.yml")
        val page = addFile("noproject/docs/index.md")

        assertNull(provider.getIcon(page, 0, null))
    }

    /**
     * Use case: the `requirements.txt` of a site, lying next to the configuration file. It pins the MkDocs
     * version, the theme and the plugins the site is built with, so it gets the MkDocs requirements icon.
     */
    fun `test marks the requirements file in the site root`() {
        addFile("reqroot/mkdocs.yml")
        val requirements = addFile("reqroot/requirements.txt")

        assertSame(MkDocsIcons.RequirementsFile, provider.getIcon(requirements, 0, project))
    }

    /**
     * Use case: a `requirements.txt` of some unrelated Python setup, in a directory holding no MkDocs
     * configuration. It has nothing to do with a site and must keep the platform icon.
     */
    fun `test leaves a requirements file outside a site alone`() {
        val requirements = addFile("python/requirements.txt")

        assertNull(provider.getIcon(requirements, 0, project))
    }

    /**
     * Use case: a `requirements.txt` in a subdirectory of a site, for example inside `docs`. MkDocs reads its
     * requirements from the site root only, so a nested one is not the requirements file of the site.
     */
    fun `test leaves a requirements file below the site root alone`() {
        addFile("nestedreq/mkdocs.yml")
        val requirements = addFile("nestedreq/docs/requirements.txt")

        assertNull(provider.getIcon(requirements, 0, project))
    }

    /**
     * Use case: the file was created as `Requirements.TXT` on a case insensitive file system. `pip` would
     * still install from it, so the IDE has to recognise it as well.
     */
    fun `test recognises the requirements file name case insensitively`() {
        addFile("reqcase/mkdocs.yml")
        val requirements = addFile("reqcase/Requirements.TXT")

        assertSame(MkDocsIcons.RequirementsFile, provider.getIcon(requirements, 0, project))
    }

    /**
     * Use case: an icon for the requirements file is requested outside any project, for example from a file
     * chooser. The decision needs the name of the file and the entries of its parent directory only, so it
     * works without a project.
     */
    fun `test marks the requirements file without a project`() {
        addFile("reqnoproject/mkdocs.yml")
        val requirements = addFile("reqnoproject/requirements.txt")

        assertSame(MkDocsIcons.RequirementsFile, provider.getIcon(requirements, 0, null))
    }

    /**
     * Use case: a style sheet the configuration pulls in through `extra_css`. That file shapes the built
     * site, so it gets an icon of its own wherever the IDE renders it.
     */
    fun `test marks a referenced style sheet`() {
        addFile("css-ref/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        addFile("css-ref/docs/index.md")
        val stylesheet = addFile("css-ref/docs/stylesheets/extra.css", "body { color: red; }\n")

        assertSame(MkDocsIcons.StylesheetFile, provider.getIcon(stylesheet, 0, project))
    }

    /**
     * Use case: a style sheet lying in the stylesheets directory that `extra_css` never names. MkDocs does
     * not load it, so it stays an ordinary CSS file and keeps the icon of its file type.
     */
    fun `test leaves an unreferenced style sheet alone`() {
        addFile("css-unref/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        addFile("css-unref/docs/index.md")
        val other = addFile("css-unref/docs/stylesheets/unused.css", "body { color: red; }\n")

        assertNull(provider.getIcon(other, 0, project))
    }

    /**
     * Use case: a style sheet named from somewhere else below the documentation directory. `extra_css`
     * resolves against that directory, so the location of the file decides nothing here.
     */
    fun `test marks a referenced style sheet outside the stylesheets directory`() {
        addFile("css-elsewhere/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - theme/print.css\n")
        addFile("css-elsewhere/docs/index.md")
        val stylesheet = addFile("css-elsewhere/docs/theme/print.css", "body { color: red; }\n")

        assertSame(MkDocsIcons.StylesheetFile, provider.getIcon(stylesheet, 0, project))
    }

    /**
     * Use case: an icon for a style sheet is requested outside any project, for example from a file chooser.
     * Without a project there is no PSI to read `extra_css` from, so nothing can be claimed.
     */
    fun `test leaves a style sheet alone without a project`() {
        addFile("css-noproject/mkdocs.yml", "site_name: Handbook\nextra_css:\n  - stylesheets/extra.css\n")
        val stylesheet = addFile("css-noproject/docs/stylesheets/extra.css", "body { color: red; }\n")

        assertNull(provider.getIcon(stylesheet, 0, null))
    }

    private fun addFile(relativePath: String, text: String = ""): VirtualFile =
        myFixture.addFileToProject(relativePath, text).virtualFile
}
