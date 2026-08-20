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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what makes a directory an installation of the theme and what is read out of it. Both are answers a
 * user acts on: the settings page refuses a path on the first, and every icon offered anywhere comes from the
 * second.
 *
 * Driven through `MkDocsMaterialInstallation.read`, the reading itself, rather than through `problemOf` and
 * `iconNames`, which answer from `MkDocsMaterialInstallationCache`. The cache is a service and needs a
 * platform application; this test has none, and what it covers is the reading anyway. What the cache adds is
 * covered by `MkDocsMaterialInstallationCacheTest`.
 */
class MkDocsMaterialInstallationTest {

    /**
     * Use case: the directory pip installed the theme into. Everything the check asks for is there, so
     * nothing is wrong with it and the settings page accepts it.
     */
    @Test
    fun `accepts an installation pip wrote`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))

        assertNull(MkDocsMaterialInstallation.read(location.path).problem)
    }

    /**
     * Use case: a path pointing at nothing — typed by hand, or an environment that was deleted. There is no
     * directory to read, and that is the first thing the user has to be told.
     */
    @Test
    fun `reports a path that is no directory`() {
        assertEquals(
            MkDocsMaterialInstallation.Problem.NO_DIRECTORY,
            MkDocsMaterialInstallation.read(File(temp(), "gone").path).problem,
        )
    }

    /**
     * Use case: any other directory of the file system, chosen by mistake. pip wrote no metadata there, so
     * nothing says this is an installation of anything at all.
     */
    @Test
    fun `reports a directory without the metadata of pip`() {
        assertEquals(
            MkDocsMaterialInstallation.Problem.NO_DIST_INFO,
            MkDocsMaterialInstallation.read(temp().path).problem,
        )
    }

    /**
     * Use case: a `*.dist-info` of the right shape whose metadata names another distribution — a directory
     * assembled by hand, or a package renamed. The name is what is actually being checked, not the file name.
     */
    @Test
    fun `reports metadata naming another distribution`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        File(distInfo(location), "METADATA").writeText("Metadata-Version: 2.4\nName: mkdocs\n")

        assertEquals(
            MkDocsMaterialInstallation.Problem.WRONG_NAME,
            MkDocsMaterialInstallation.read(location.path).problem,
        )
    }

    /**
     * Use case: an installation whose listing is gone. The icons are read out of exactly that file, so an
     * installation without it is one nothing can be read from — and saying so beats an empty popup.
     */
    @Test
    fun `reports a missing listing`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        File(distInfo(location), "RECORD").delete()

        assertEquals(
            MkDocsMaterialInstallation.Problem.NO_RECORD,
            MkDocsMaterialInstallation.read(location.path).problem,
        )
    }

    /**
     * Use case: a listing that is not text at all, because something else was written over it. It cannot be
     * read as the lines pip writes, which is the same finding as it being missing.
     */
    @Test
    fun `reports a listing that is no text`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        File(distInfo(location), "RECORD").writeBytes(byteArrayOf(0x00, 0xC3.toByte(), 0x28, 0x00))

        assertEquals(
            MkDocsMaterialInstallation.Problem.NO_RECORD,
            MkDocsMaterialInstallation.read(location.path).problem,
        )
    }

    /**
     * Use case: a listing that is text, but not the comma separated lines pip writes. Nothing can be taken
     * out of it, which is the same finding as a listing that cannot be decoded at all.
     */
    @Test
    fun `reports a listing that is not the listing of pip`() {
        val location = MkDocsMaterialInstallationFixture.write(listOf("material/check.svg"))
        File(distInfo(location), "RECORD").writeText("just a sentence\nand another one\n")

        assertEquals(
            MkDocsMaterialInstallation.Problem.NO_RECORD,
            MkDocsMaterialInstallation.read(location.path).problem,
        )
    }

    /**
     * Use case: the names offered everywhere the theme addresses an icon. They are the paths below the icon
     * sets without the extension, and they come out of the listing the installation itself wrote.
     */
    @Test
    fun `reads the icon names out of the listing`() {
        val location = MkDocsMaterialInstallationFixture.write(
            listOf("material/check.svg", "fontawesome/brands/github.svg"),
        )

        assertEquals(
            listOf("fontawesome/brands/github", "material/check"),
            MkDocsMaterialInstallation.read(location.path).iconNames,
        )
    }

    /**
     * Use case: a directory that is no installation. Nothing is read out of it, and that has to be an empty
     * list rather than an exception — the completion asks this question while a user is typing.
     */
    @Test
    fun `reads nothing out of a directory that is no installation`() {
        assertTrue(MkDocsMaterialInstallation.read(temp().path).iconNames.isEmpty())
    }

    /**
     * Returns the `*.dist-info` directory of the installation at [location].
     *
     * @param location the installation directory the fixture wrote
     */
    private fun distInfo(location: File): File =
        location.listFiles()!!.first { it.name.endsWith(".dist-info") }

    /**
     * Returns an empty directory of the file system.
     */
    private fun temp(): File = Files.createTempDirectory("mkdocs-empty").toFile()
}
