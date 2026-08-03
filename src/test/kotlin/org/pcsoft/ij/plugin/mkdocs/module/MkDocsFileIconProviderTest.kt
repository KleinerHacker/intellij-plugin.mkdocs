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
 * Covers which files [MkDocsFileIconProvider] claims: only MkDocs configuration files get the plugin icon,
 * everything else is left to the platform.
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
     * Use case: an icon is requested outside any project, for example from a file chooser. The decision
     * depends on the file name alone, so it works without a project just as well.
     */
    fun `test works without a project`() {
        val file = addFile("mkdocs.yml")

        assertSame(MkDocsIcons.ConfigFile, provider.getIcon(file, 0, null))
    }

    private fun addFile(relativePath: String): VirtualFile =
        myFixture.addFileToProject(relativePath, "").virtualFile
}
