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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialInstallationFixture

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the locator decides on its own: the configured override, its precedence and what happens when
 * it is gone. Where an installation lies is the business of the shared locator and is covered next to it —
 * a test here cannot install a package, and guessing at directories of a checkout is exactly what this
 * feature no longer does.
 */
class MkDocsMaterialIconLocatorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            MkDocsMaterialInstallationFixture.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: an interpreter pip cannot answer for — a system wide installation, a container mount. The
     * configured installation is the answer, and it is taken without pip being asked at all.
     */
    fun `test prefers the configured installation`() {
        // A real installation on disk rather than one of the fixture: the setting names a path of the local
        // file system, which is what an interpreter outside the project has, and the in-memory file system
        // of the fixture is not reachable through one.
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        MkDocsMaterialInstallationFixture.point(project, location.path)

        assertEquals(location.path, MkDocsMaterialIconLocator.locateInstallation(project))
        val icons = runReadActionBlocking { MkDocsMaterialIconLocator.locate(project) }
        assertNotNull(icons)
        assertNotNull(icons!!.findFileByRelativePath("material/check.svg"))
    }

    /**
     * Use case: a configured path that no longer exists, because the environment was moved. It is treated as
     * if nothing were configured, so what pip reports gets its turn instead of the locator falling silent.
     */
    fun `test ignores a configured path that is gone`() {
        MkDocsMaterialInstallationFixture.point(project, "/does/not/exist")

        assertNull(MkDocsMaterialIconLocator.locateInstallation(project))
    }

    /**
     * Use case: a directory that lies inside an installation but is none itself — the package directory,
     * chosen by hand. Without the metadata pip writes beside it there is nothing proving which distribution
     * wrote those files, so the path is refused rather than silently used.
     */
    fun `test ignores a directory that is no installation`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        MkDocsMaterialInstallationFixture.point(project, "${location.path}/material")

        assertNull(MkDocsMaterialIconLocator.locateInstallation(project))
    }

    /**
     * Use case: the feature names the distribution and the path inside the package for every place that has
     * to ask for the installation — the settings page does, and it must not spell them out a second time.
     */
    fun `test names the distribution and the path inside the package`() {
        assertEquals("mkdocs-material", MkDocsMaterialIconLocator.DISTRIBUTION)
        assertEquals("material/templates/.icons", MkDocsMaterialIconLocator.ICONS_INSIDE_PACKAGE)
    }
}
