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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProfile
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProvider
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import java.nio.file.Path
import java.time.Year

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the fourth wizard step: where the footer notice comes from, and when the user has to pick one.
 */
class MkDocsCopyrightStepTest : BasePlatformTestCase() {

    /**
     * Use case: the IDE has no copyright notice configured, because the Copyright plugin is switched off or
     * the project never needed one. The step then builds a notice of its own from the year and the author
     * entered on the previous page, and shows no selection at all.
     */
    fun `test falls back to a notice built from the author`() {
        val step = MkDocsCopyrightStep(project)

        assertFalse("nothing to choose from", step.isProfileChoiceVisible())

        step.suggestNoticeFor("Acme")
        assertEquals("© ${Year.now().value} Acme", applied(step).copyright)
    }

    /**
     * Use case: exactly one notice is configured. There is nothing to decide, so it is simply used and the
     * selection stays hidden.
     */
    fun `test uses a single configured notice without asking`() {
        registerProfiles(listOf(profile("Apache", "© 2026 Acme, Apache 2.0")), default = null)

        val step = MkDocsCopyrightStep(project)

        assertFalse("a single notice needs no choice", step.isProfileChoiceVisible())
        assertEquals("© 2026 Acme, Apache 2.0", applied(step).copyright)
    }

    /**
     * Use case: several notices are configured and one of them is marked as the default. That marking is a
     * decision the user already made, so it is followed and nothing is asked again.
     */
    fun `test follows the marked default notice`() {
        val apache = profile("Apache", "© 2026 Acme, Apache 2.0")
        val proprietary = profile("Proprietary", "© 2026 Acme, all rights reserved")
        registerProfiles(listOf(apache, proprietary), default = proprietary)

        val step = MkDocsCopyrightStep(project)

        assertFalse("a marked default needs no choice", step.isProfileChoiceVisible())
        assertEquals("© 2026 Acme, all rights reserved", applied(step).copyright)
    }

    /**
     * Use case: several notices are configured and none is marked. Only the user can say which one belongs
     * under this site, so the selection is shown — starting at the first entry.
     */
    fun `test offers a choice between several notices without a default`() {
        registerProfiles(
            listOf(profile("Apache", "© 2026 Acme, Apache 2.0"), profile("Proprietary", "© 2026 Acme")),
            default = null,
        )

        val step = MkDocsCopyrightStep(project)

        assertTrue("the user has to decide", step.isProfileChoiceVisible())
        assertEquals("© 2026 Acme, Apache 2.0", applied(step).copyright)
    }

    /**
     * Use case: the notice is a footer line, not a source header — the user shortens it. Whatever the IDE
     * suggested, the text stays theirs, and nothing entered later overwrites it.
     */
    fun `test keeps a notice the user edited`() {
        registerProfiles(listOf(profile("Apache", "© 2026 Acme, Apache 2.0")), default = null)
        val step = MkDocsCopyrightStep(project)

        step.setCopyrightForTest("© 2026 Acme")
        step.suggestNoticeFor("Someone Else")

        assertEquals("© 2026 Acme", applied(step).copyright)
    }

    /**
     * Use case: the user cleared the field. No notice is what they asked for, so no key is written.
     */
    fun `test writes no notice for an empty field`() {
        val step = MkDocsCopyrightStep(project)
        step.setCopyrightForTest("   ")

        assertEquals("", applied(step).copyright)
    }

    /**
     * Use case: a notice configured as the multi-line comment block of a source header. `copyright` is a
     * single string, so the lines have to be joined rather than kept.
     */
    fun `test reduces a multi line notice to one line`() {
        assertEquals(
            "© 2026 Acme. All rights reserved.",
            MkDocsCopyrightProfile.singleLine("  © 2026 Acme.\n   All rights reserved.\n"),
        )
    }

    /** Registers [profiles] and [default] for the duration of the test. */
    private fun registerProfiles(profiles: List<MkDocsCopyrightProfile>, default: MkDocsCopyrightProfile?) {
        ExtensionTestUtil.maskExtensions(
            MkDocsCopyrightProvider.EP_NAME,
            listOf(FixedCopyrightProvider(profiles, default)),
            testRootDisposable,
        )
    }

    private fun profile(name: String, notice: String) = MkDocsCopyrightProfile(name, notice)

    /** Applies the step to an otherwise empty template. */
    private fun applied(step: MkDocsCopyrightStep): MkDocsSiteTemplate =
        step.applyTo(MkDocsSiteTemplate(rootPath = Path.of("/tmp/site").toAbsolutePath(), siteName = "Site"))

    /** Test double reporting a fixed set of notices. */
    private class FixedCopyrightProvider(
        private val profiles: List<MkDocsCopyrightProfile>,
        private val default: MkDocsCopyrightProfile?,
    ) : MkDocsCopyrightProvider {

        override fun profiles(project: Project): List<MkDocsCopyrightProfile> = profiles

        override fun defaultProfile(project: Project): MkDocsCopyrightProfile? = default
    }
}
