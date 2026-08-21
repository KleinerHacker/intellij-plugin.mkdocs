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

package org.pcsoft.ij.plugin.mkdocs.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsInstallationSettings
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsTool

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the page naming the three programs an MkDocs site is built with. It lives in the plugin project
 * because the page is registered by `plugin.xml` and worded out of the bundle the plugin ships — a module
 * project registers nothing in its test run.
 *
 * The search itself is never driven here: it starts a process per program, and which of them lie on the
 * machine running a build is not something a test may depend on.
 */
class MkDocsSettingsConfigurableTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            project.service<MkDocsInstallationSettings>().paths.clear()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: the page being built. Every text it shows comes out of the bundle, and a key that is missing
     * is not noticed while writing the page — the platform renders the key itself and the page still opens.
     */
    fun `test builds the page out of the bundle`() {
        val configurable = MkDocsSettingsConfigurable(project)

        assertEquals(MkDocsBundle.message("settings.title"), configurable.displayName)
        assertNotNull(configurable.createComponent())
        MkDocsTool.entries.forEach { tool ->
            assertFalse(MkDocsBundle.message("settings.tools.${tool.key}.label").isEmpty())
            assertFalse(MkDocsBundle.message("settings.tools.${tool.key}.chooser").isEmpty())
            assertFalse(MkDocsBundle.message("settings.tools.${tool.key}.progress").isEmpty())
        }
    }

    /**
     * Use case: the page opened on a project nothing was configured in. Every field stands on the automatic
     * entry, and the page must not report itself as modified — a settings dialog offering *Apply* on a page
     * nobody touched is how a user loses trust in what it says.
     */
    fun `test reports nothing as modified while every program is searched for`() {
        val configurable = MkDocsSettingsConfigurable(project)
        configurable.createComponent()

        assertFalse(configurable.isModified)
    }

    /**
     * Use case: a program named by hand in an earlier session. The page has to notice that its fields — which
     * start on the automatic entry — do not match what is stored, so that opening and applying restores the
     * configured program rather than silently dropping it.
     */
    fun `test reports a configured program as a difference`() {
        project.service<MkDocsInstallationSettings>().setPath(MkDocsTool.PYTHON.key, "/opt/venv/bin/python")
        val configurable = MkDocsSettingsConfigurable(project)
        configurable.createComponent()

        assertTrue(configurable.isModified)
    }

    /**
     * Use case: applying a page every field of which stands on the automatic entry. That is the way back from
     * a configured program, and it has to drop the stored path rather than store an empty one — an empty path
     * is not a program, and the locator reads it as one.
     */
    fun `test applying the automatic entry forgets a configured program`() {
        val settings = project.service<MkDocsInstallationSettings>()
        settings.setPath(MkDocsTool.MKDOCS.key, "/opt/own/mkdocs")
        val configurable = MkDocsSettingsConfigurable(project)
        configurable.createComponent()

        configurable.apply()

        assertEquals("", settings.pathOf(MkDocsTool.MKDOCS.key))
        assertFalse(settings.paths.containsKey(MkDocsTool.MKDOCS.key))
    }
}
