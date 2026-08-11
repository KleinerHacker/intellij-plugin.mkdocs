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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsScmProvider
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError
import java.nio.file.Path

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the third wizard step: the prefilling from the repository the project lives in, and the warning
 * about an entry that no longer matches it.
 */
class MkDocsRepositoryStepTest : BasePlatformTestCase() {

    /**
     * Use case: no version control at all, or a VCS the plugin has no provider for. Both fields simply start
     * empty, and nothing is reported — there is nothing to compare against.
     */
    fun `test starts empty without a repository`() {
        val step = MkDocsRepositoryStep(project, null)

        val template = applied(step)
        assertEquals("", template.repoName)
        assertEquals("", template.repoUrl)
        assertNull(step.mismatchWarning())
    }

    /**
     * Use case: the project is under Git. Both fields start out filled with what the repository says, so the
     * ordinary case needs no typing at all.
     */
    fun `test prefills both fields from the repository`() {
        registerProvider("git@github.com:acme/machine.git")

        val step = MkDocsRepositoryStep(project, null)

        val template = applied(step)
        assertEquals("acme/machine", template.repoName)
        assertEquals("https://github.com/acme/machine", template.repoUrl)
        assertNull("the prefilled values match the repository", step.mismatchWarning())
    }

    /**
     * Use case: the user replaces the prefilled address with another repository. The name follows along, the
     * way it followed the repository in the first place.
     */
    fun `test lets the name follow the address`() {
        registerProvider("https://github.com/acme/machine.git")
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoUrlForTest("https://github.com/other/project")

        assertEquals("other/project", applied(step).repoName)
    }

    /**
     * Use case: the user gave the repository a display name of their own. From then on the name is theirs
     * and must survive further edits of the address.
     */
    fun `test stops following the address once the name was edited`() {
        registerProvider("https://github.com/acme/machine.git")
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoNameForTest("The Machine")
        step.setRepoUrlForTest("https://github.com/other/project")

        assertEquals("The Machine", applied(step).repoName)
    }

    /**
     * Use case: the entered address points somewhere else than the repository the site is created in. That
     * is allowed — a site may document another repository — so it is a warning, never a refusal.
     */
    fun `test warns about an address pointing elsewhere`() {
        registerProvider("https://github.com/acme/machine.git")
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoUrlForTest("https://github.com/other/project")

        assertNotNull("the mismatch must be reported", step.mismatchWarning())
        assertNull("a mismatch must not block the wizard", step.validate())
    }

    /**
     * Use case: the user replaced the prefilled HTTPS address with the SSH address of the very same
     * repository. Both name the same thing, so warning about it would be noise.
     */
    fun `test does not warn about another spelling of the same repository`() {
        registerProvider("https://github.com/acme/machine.git")
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoUrlForTest("git@github.com:acme/machine.git")

        assertNull(step.mismatchWarning())
    }

    /**
     * Use case: only the display name was changed, which is a common thing to do — but it is still worth
     * pointing out that it no longer matches the repository.
     */
    fun `test warns about a name differing from the repository`() {
        registerProvider("https://github.com/acme/machine.git")
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoNameForTest("The Machine")

        assertNotNull(step.mismatchWarning())
    }

    /**
     * Use case: a half-typed address. `repo_url` becomes a link on every page, so an address that is none is
     * refused — while an empty field stays perfectly fine, the key is optional.
     */
    fun `test refuses an address that is no web address`() {
        val step = MkDocsRepositoryStep(project, null)

        step.setRepoUrlForTest("github.com/acme/machine")
        assertEquals(MkDocsSiteTemplateError.INVALID_REPO_URL, step.validate())

        step.setRepoUrlForTest("")
        assertNull(step.validate())
    }

    /** Registers a provider reporting [remote] for the duration of the test. */
    private fun registerProvider(remote: String) {
        ExtensionTestUtil.maskExtensions(
            MkDocsScmProvider.EP_NAME,
            listOf(FixedScmProvider(remote)),
            testRootDisposable,
        )
    }

    /** Applies the step to an otherwise empty template. */
    private fun applied(step: MkDocsRepositoryStep): MkDocsSiteTemplate =
        step.applyTo(MkDocsSiteTemplate(rootPath = Path.of("/tmp/site").toAbsolutePath(), siteName = "Site"))

    /** Test double reporting one fixed remote, whatever it is asked about. */
    private class FixedScmProvider(private val remote: String) : MkDocsScmProvider {

        override fun remoteUrl(project: Project, directory: VirtualFile?): String = remote

        override fun userName(project: Project, directory: VirtualFile?): String = "Ada Lovelace"
    }
}
