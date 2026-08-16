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

package org.pcsoft.ij.plugin.mkdocs

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the lookup every caller asking "which configuration file does this module stand for" relies on —
 * the facet editor tab, the tool window and the optional features alike.
 */
class MkDocsSiteFilesTest : BasePlatformTestCase() {

    /**
     * Use case: the regular case — the facet was created by the detection and its `configFilePath` points at
     * the file it was derived from. The lookup must find that file, so the tab shows the site information.
     */
    fun `test finds the configured file`() {
        myFixture.addFileToProject("mkdocs.yml", "site_name: My Documentation\n")

        val found = MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml")

        assertNotNull("expected the configured file to be found", found)
        assertEquals("mkdocs.yml", found!!.name)
    }

    /**
     * Use case: the common layout in which the site is not the module itself — the configuration file sits in
     * a `docs/` subdirectory of a module that mainly contains something else. `configFilePath` is stored
     * relative to the site root and therefore holds the bare file name, so the lookup has to search the
     * content root recursively instead of only its direct children.
     */
    fun `test finds a configuration file below the module root`() {
        myFixture.addFileToProject("docs/mkdocs.yml", "site_name: My Documentation\n")

        val found = MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml")

        assertNotNull("expected a nested configuration file to be found", found)
        assertEquals("mkdocs.yml", found!!.name)
        assertTrue("expected the file below docs/", found.path.endsWith("docs/mkdocs.yml"))
    }

    /**
     * Use case: a facet reached the module model through a hand-edited or merged `.iml` and carries no path
     * at all, but the module does contain a site. The lookup falls back to searching the content roots, so a
     * valid site is not reported as an error.
     */
    fun `test falls back to a configuration file in the content root`() {
        myFixture.addFileToProject("mkdocs.yaml", "site_name: Handbook\n")

        val found = MkDocsSiteFiles.findConfigFile(myFixture.module, "")

        assertNotNull("expected the fallback search to find the file", found)
        assertEquals("mkdocs.yaml", found!!.name)
    }

    /**
     * Use case: the facet records `mkdocs.yml`, but the site on disk meanwhile uses the `mkdocs.yaml`
     * spelling. Both are valid MkDocs configuration files, so the module still has a site and must not be
     * reported as broken.
     */
    fun `test accepts the other configuration file spelling`() {
        myFixture.addFileToProject("docs/mkdocs.yaml", "site_name: Handbook\n")

        val found = MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml")

        assertNotNull("expected the other spelling to be accepted", found)
        assertEquals("mkdocs.yaml", found!!.name)
    }

    /**
     * Use case: a configuration file copied into a build output is an artefact, not a site. The lookup skips
     * the same directories as the detection, so such a file must not make an empty facet look valid.
     */
    fun `test ignores a configuration file inside a build output`() {
        myFixture.addFileToProject("build/mkdocs.yml", "site_name: Artefact\n")
        myFixture.addFileToProject("site/mkdocs.yml", "site_name: Artefact\n")

        assertNull(MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml"))
    }

    /**
     * Use case: exactly the situation the validation exists for — a module carries the MkDocs facet without
     * holding an MkDocs configuration file. The lookup must report nothing so the tab shows the error instead
     * of two empty labels.
     */
    fun `test reports nothing without a configuration file`() {
        myFixture.addFileToProject("readme.md", "# no mkdocs here\n")

        assertNull(MkDocsSiteFiles.findConfigFile(myFixture.module, ""))
        assertNull(MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml"))
    }

    /**
     * Use case: the configured path points at a directory named like a configuration file. A directory is not
     * a configuration file, and no real one exists either, so the lookup must still report nothing.
     */
    fun `test ignores a directory at the configured path`() {
        myFixture.addFileToProject("mkdocs.yml/placeholder.txt", "")

        assertNull(MkDocsSiteFiles.findConfigFile(myFixture.module, "mkdocs.yml"))
    }
}
