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

package org.pcsoft.ij.plugin.mkdocs.material

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the one thing the cache is here for: that an installation is read once and not again. The file it
 * reads is the `RECORD` of the distribution, some thousand lines long, and what asks for it are the completion
 * popup on every keystroke, the inlay hints and the annotator on every highlighting pass. Reading it per
 * question is what made all of them slow, so "read a second time" is a defect, not a detail.
 */
class MkDocsMaterialInstallationCacheTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // An application level service outlives the fixture, so what another test left in it would answer here.
        cache().invalidate()
    }

    override fun tearDown() {
        try {
            cache().invalidate()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: the same installation asked about twice, which is what every keystroke in a configuration file
     * does. The second question is answered out of the cache, without the directory being read again.
     */
    fun `test reads an installation only once`() {
        var reads = 0

        cache().dataOf(LOCATION) { reads++; INSTALLED }
        cache().dataOf(LOCATION) { reads++; INSTALLED }

        assertEquals(1, reads)
    }

    /**
     * Use case: the answer given to the second caller. It is the one that was read, not an empty stand-in —
     * the icons of every popup after the first come out of the cache.
     */
    fun `test answers the second question with what was read`() {
        cache().dataOf(LOCATION) { INSTALLED }

        val second = cache().dataOf(LOCATION) { error("read a second time") }

        assertEquals(listOf("material/check"), second.iconNames)
        assertNull(second.problem)
    }

    /**
     * Use case: a package installed next to the running IDE. Nothing re-checks an installation on its own, so
     * the way to pick it up is throwing the answer away — and the next question has to read again.
     */
    fun `test reads again after being invalidated`() {
        var reads = 0

        cache().dataOf(LOCATION) { reads++; INSTALLED }
        cache().invalidate()
        cache().dataOf(LOCATION) { reads++; INSTALLED }

        assertEquals(2, reads)
    }

    /**
     * Use case: a path the user is still typing on the settings page. It is no directory yet, and remembering
     * that would outlive the typing — the directory typed out in full would stay "not there".
     */
    fun `test does not remember a path that is no directory`() {
        var reads = 0

        cache().dataOf(LOCATION) { reads++; MkDocsMaterialInstallation.DataSet.of(NO_DIRECTORY) }
        cache().dataOf(LOCATION) { reads++; MkDocsMaterialInstallation.DataSet.of(NO_DIRECTORY) }

        assertEquals(2, reads)
    }

    /**
     * Use case: a finding other than a missing directory — a directory holding no metadata of pip, say. It
     * does not become an installation by itself either, so it is remembered like any other answer.
     */
    fun `test remembers a directory that is no installation`() {
        var reads = 0
        val noDistInfo = MkDocsMaterialInstallation.DataSet.of(MkDocsMaterialInstallation.Problem.NO_DIST_INFO)

        cache().dataOf(LOCATION) { reads++; noDistInfo }
        cache().dataOf(LOCATION) { reads++; noDistInfo }

        assertEquals(1, reads)
    }

    /**
     * Use case: the seam the tests of this feature pin an installation with. What lies in the `site-packages`
     * of the machine running a build must never answer for a test, so a reading can be put in by hand.
     */
    fun `test takes a reading handed to it`() {
        cache().remember(LOCATION, INSTALLED)

        val answer = cache().dataOf(LOCATION) { error("read although the reading was handed in") }

        assertEquals(listOf("material/check"), answer.iconNames)
    }

    /**
     * Use case: two environments in one project, each with a theme of its own. They are told apart by their
     * directory, so the answer of one is never handed to the other.
     */
    fun `test keeps the installations apart`() {
        cache().remember(LOCATION, INSTALLED)
        cache().remember(OTHER_LOCATION, MkDocsMaterialInstallation.DataSet(null, listOf("material/close")))

        assertEquals(listOf("material/check"), cache().dataOf(LOCATION) { error("read") }.iconNames)
        assertEquals(listOf("material/close"), cache().dataOf(OTHER_LOCATION) { error("read") }.iconNames)
    }

    /**
     * Use case: the file behind an icon name, asked for whenever a popup or an inlay hint paints the drawing.
     * The name is resolved below the sets once; every repaint after that is answered out of the cache.
     */
    fun `test resolves the file of an icon only once`() {
        val file = myFixture.addFileToProject("icons/material/check.svg", "<svg/>").virtualFile
        val root = file.parent.parent
        var resolves = 0

        cache().fileOf(root, "material/check") { resolves++; file }
        cache().fileOf(root, "material/check") { resolves++; file }

        assertEquals(1, resolves)
    }

    /**
     * Use case: a name the installation does not carry. Nothing is remembered for it — a theme installed while
     * the project is open would otherwise stay invisible behind an answer that was only ever a miss.
     */
    fun `test does not remember a name it could not resolve`() {
        val root = myFixture.addFileToProject("icons/material/check.svg", "<svg/>").virtualFile.parent.parent
        var resolves = 0

        cache().fileOf(root, "material/gone") { resolves++; null }
        cache().fileOf(root, "material/gone") { resolves++; null }

        assertEquals(2, resolves)
    }

    /**
     * Use case: the same drawing painted again, which every repaint of a popup does. It is built once, and
     * the size is part of what tells two of them apart — a popup asks for 16 pixels, an inlay hint for 12.
     */
    fun `test builds an icon once per size`() {
        val file = myFixture.addFileToProject("icons/material/check.svg", "<svg/>").virtualFile
        var renders = 0

        cache().iconOf(file, 16) { renders++; MkDocsMaterialIcons.Feature }
        cache().iconOf(file, 16) { renders++; MkDocsMaterialIcons.Feature }
        cache().iconOf(file, 12) { renders++; MkDocsMaterialIcons.Feature }

        assertEquals(2, renders)
    }

    /**
     * Use case: the installation was re-read. Everything that followed from the old one goes with it — the
     * file behind a name and the drawing alike, because the same name can now be another file.
     */
    fun `test drops the files and the icons as well`() {
        val file = myFixture.addFileToProject("icons/material/check.svg", "<svg/>").virtualFile
        val root = file.parent.parent
        var reads = 0
        cache().fileOf(root, "material/check") { reads++; file }
        cache().iconOf(file, 16) { reads++; MkDocsMaterialIcons.Feature }

        cache().invalidate()

        cache().fileOf(root, "material/check") { reads++; file }
        cache().iconOf(file, 16) { reads++; MkDocsMaterialIcons.Feature }
        assertEquals(4, reads)
    }

    /**
     * Returns the cache under test.
     */
    private fun cache(): MkDocsMaterialInstallationCache = service()

    private companion object {

        /** The installation directory the tests ask about. */
        const val LOCATION = "/tmp/site-packages"

        /** A second installation directory, for telling the two apart. */
        const val OTHER_LOCATION = "/tmp/other-site-packages"

        /** What is read out of [LOCATION]. */
        val INSTALLED = MkDocsMaterialInstallation.DataSet(null, listOf("material/check"))

        /** The finding of a path that is no directory. */
        val NO_DIRECTORY = MkDocsMaterialInstallation.Problem.NO_DIRECTORY
    }
}
