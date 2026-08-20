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

package org.pcsoft.ij.plugin.mkdocs.material.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the marking of a name in `mkdocs.yml` that the installed theme offers no icon for: which of the two
 * findings is reported, and which part of the name is marked. A site renders nothing at all for such a name,
 * silently, so the two have to be told apart — a wrong set is a name of the wrong family, a wrong icon below
 * a right set is a typo.
 */
class MkDocsMaterialIconAnnotatorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and pip must not answer for the
        // machine the build runs on.
        MkDocsMaterialInstalledTheme.install(project, ICON_NAMES.map { "$it.svg" })
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstalledTheme.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a name of a set the installation does not ship — a theme of another version, or a set the
     * author remembered wrongly. Nothing below such a name can be right, so the set is what is marked.
     */
    fun `test marks a set the installation does not ship`() {
        val problems = problemsOf("materail/check")

        assertSize(1, problems)
        assertTrue(problems.first().description.contains("icon set"))
        assertEquals("materail", textOf(problems.first()))
    }

    /**
     * Use case: a typo in the name of an icon below a set that is there. The set is right and stays unmarked;
     * what is wrong is the name behind the separator.
     */
    fun `test marks an icon a known set does not hold`() {
        val problems = problemsOf("material/chekc")

        assertSize(1, problems)
        assertFalse(problems.first().description.contains("icon set"))
        assertEquals("chekc", textOf(problems.first()))
    }

    /**
     * Use case: a nested set, where everything but the last segment is the set. The name behind the last
     * separator is marked and the two levels in front of it are not.
     */
    fun `test marks the name below a nested set`() {
        val problems = problemsOf("fontawesome/brands/gitlab")

        assertSize(1, problems)
        assertEquals("gitlab", textOf(problems.first()))
    }

    /**
     * Use case: the finding is an error. A site builds no icon for such a name — it renders nothing there —
     * which is not a matter of taste.
     */
    fun `test reports the finding as an error`() {
        assertTrue(problemsOf("material/chekc").all { it.severity == HighlightSeverity.ERROR })
    }

    /**
     * Use case: the names the installation actually holds, flat and nested. Nothing is reported for them.
     */
    fun `test stays quiet on an installed icon`() {
        assertEmpty(problemsOf("material/check"))
        assertEmpty(problemsOf("fontawesome/brands/github"))
    }

    /**
     * Use case: a key that is being written, with nothing behind it yet. An empty value is not a wrong name,
     * and reporting it would put red under every line the author has just opened.
     */
    fun `test stays quiet on an empty value`() {
        assertEmpty(problemsOf(""))
    }

    /**
     * Use case: a checkout whose environment carries no theme. Every name would be unknown then, and the
     * missing installation is what `MkDocsMaterialInstallationAnnotator` states once for the whole file.
     */
    fun `test stays quiet without an installation`() {
        MkDocsMaterialInstalledTheme.uninstall(project)

        assertEmpty(problemsOf("material/chekc"))
    }

    /**
     * Use case: another key of the same file. `theme.name` names the theme, and a name that is no icon there
     * must not be judged as one.
     */
    fun `test stays quiet at a key that names no icon`() {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
            """.trimIndent() + "\n",
        )

        assertEmpty(reported())
    }

    /**
     * Use case: a YAML file that is no configuration file of MkDocs, holding the very content that gets
     * marked under the name of one. Its name decides, exactly as everywhere else in the plugin.
     */
    fun `test stays quiet in a YAML file that is not a configuration file`() {
        assertEmpty(problemsOf("material/chekc", name = "other.yml"))
    }

    /**
     * Returns what the annotator reports for a configuration file naming [icon] as the repository icon.
     *
     * @param icon the value written behind `theme.icon.repo`
     * @param name the file name to write the content under
     */
    private fun problemsOf(icon: String, name: String = "mkdocs.yml"): List<HighlightInfo> {
        myFixture.configureByText(
            name,
            """
            site_name: Handbook
            theme:
              name: material
              icon:
                repo: $icon
            """.trimIndent() + "\n",
        )
        return reported()
    }

    /**
     * Returns the findings of this annotator among everything the file was highlighted with.
     */
    private fun reported(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.description?.contains("ships no icon") == true }

    /**
     * Returns the text [problem] was reported on.
     *
     * @param problem the finding in question
     */
    private fun textOf(problem: HighlightInfo): String =
        myFixture.file.text.substring(problem.startOffset, problem.endOffset)

    private companion object {

        /** The icons the installation of this test ships. */
        val ICON_NAMES = listOf("material/check", "material/alert", "fontawesome/brands/github")
    }
}
