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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the shared locator makes of the answer of pip: a directory that exists is handed on, everything
 * else is not. Whether pip is on the machine running the build is not something a test may depend on, so the
 * answer is not driven — the cache of the pip service stands in for it, and the directories it points at are
 * real ones.
 */
class MkDocsInstallationLocatorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            service<MkDocsPipService>().invalidate()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a feature whose distribution is not installed. The locator has to answer with nothing, which
     * is the normal state of a fresh checkout rather than a fault.
     */
    fun `test finds nothing for a distribution that is not installed`() {
        val distribution = pretendInstalled("")

        assertTrue(MkDocsInstallationLocator.detectAll(distribution, "any/where").isEmpty())
        assertNull(MkDocsInstallationLocator.detect(distribution, "any/where"))
    }

    /**
     * Use case: pip reports an installation and the wanted directory is inside it — the normal case, driven
     * through a real directory because an installation lies outside the project and outside its fixture.
     */
    fun `test hands on a directory that exists inside the installation`() {
        val location = Files.createTempDirectory("mkdocs-installation")
        Files.createDirectories(location.resolve("material/templates/.icons"))

        val found = MkDocsInstallationLocator.detect(pretendInstalled(location.toString()), ICONS)

        assertNotNull(found)
        assertEquals("templates", found!!.parent.name)
    }

    /**
     * Use case: an installation of a version that does not ship the wanted directory. Nothing may be handed
     * on, because everything built on the answer would then walk a directory that is not the one meant.
     */
    fun `test finds nothing when the installation lacks the directory`() {
        val location = Files.createTempDirectory("mkdocs-installation")

        assertNull(MkDocsInstallationLocator.detect(pretendInstalled(location.toString()), ICONS))
    }

    /**
     * Use case: the path inside the installation names a file rather than a directory. A file is not a set of
     * icons, and taking it would turn every later directory listing into an empty one.
     */
    fun `test finds nothing when the path names a file`() {
        val location = Files.createTempDirectory("mkdocs-installation")
        Files.createDirectories(location.resolve("material/templates"))
        Files.writeString(location.resolve("material/templates/.icons"), "not a directory")

        assertNull(MkDocsInstallationLocator.detect(pretendInstalled(location.toString()), ICONS))
    }

    /**
     * Puts [location] into the cache of the pip service as the installation of a distribution of its own and
     * returns that distribution.
     *
     * Warming the cache is what stands in for pip here: the service asks only for a distribution it has no
     * answer for yet, so a cached answer is the one the locator reads, on every machine alike.
     *
     * @param location the directory the distribution is to be reported as installed in, empty for none
     */
    private fun pretendInstalled(location: String): String {
        val distribution = "mkdocs-test-${System.nanoTime()}"
        service<MkDocsPipService>().overrideLocation(distribution, location)
        return distribution
    }

    private companion object {

        /** The path inside the package the test asks for, the one the Material feature uses. */
        const val ICONS = "material/templates/.icons"
    }
}
