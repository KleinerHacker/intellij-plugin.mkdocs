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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers how the findings of the path check reach the editor: with which severity they are reported and over
 * which part of the value they are drawn.
 *
 * The severity of a problem depends on the operating system the IDE runs on, so the expectations are taken
 * from the very API the annotator asks — hard coding one platform's answer would turn the build red on the
 * other.
 */
class MkDocsPathAnnotatorTest : BasePlatformTestCase() {

    /**
     * Use case: a path copied from a terminal and left absolute. MkDocs resolves the value against the site,
     * so this is broken on every operating system and is reported as an error.
     */
    fun `test reports a problem broken everywhere as an error`() {
        site()
        configure("site_name: Handbook\ndocs_dir: /var/docs\n")

        val info = single(MkDocsBundle.message("reference.problem.absolutePath"))

        assertEquals(HighlightSeverity.ERROR, info.severity)
    }

    /**
     * Use case: a path written with backslashes. It works on the machine it was written on and breaks on the
     * build server, which is what a warning says and an error would not.
     */
    fun `test reports a problem broken elsewhere as a warning`() {
        site()
        configure("nav:\n  - guide\\tuning.md\n")

        val info = single(MkDocsBundle.message("reference.problem.backslashSeparator"))

        assertEquals(HighlightSeverity.WARNING, info.severity)
    }

    /**
     * Use case: a page called `con.md`, which Windows refuses outright and every other system accepts. The
     * severity therefore follows the platform the IDE runs on rather than the union of all of them.
     */
    fun `test the severity of a windows problem follows the platform`() {
        site()
        configure("nav:\n  - con.md\n")
        val expected =
            if (MkDocsPathProblem.RESERVED_NAME.isError(SystemInfo.isWindows)) HighlightSeverity.ERROR
            else HighlightSeverity.WARNING

        val info = single(MkDocsBundle.message("reference.problem.reservedName", "con.md"))

        assertEquals(expected, info.severity)
    }

    /**
     * Use case: the marking has to sit on the offending part of the value and nowhere else — here on the
     * leading separator of an absolute path, not on the whole key.
     */
    fun `test the marking covers the offending fragment only`() {
        site()
        val file = configure("site_name: Handbook\ndocs_dir: /var/docs\n")

        val info = single(MkDocsBundle.message("reference.problem.absolutePath"))
        val start = file.text.indexOf("/var/docs")

        assertEquals(start, info.startOffset)
        assertEquals(start + 1, info.endOffset)
    }

    /**
     * Use case: the value is written in quotes, as YAML allows anywhere. The quotes are no part of the path,
     * so the marking has to start behind the opening one.
     */
    fun `test the marking skips the quotes of a quoted value`() {
        site()
        val file = configure("site_name: Handbook\ndocs_dir: \"/var/docs\"\n")

        val info = single(MkDocsBundle.message("reference.problem.absolutePath"))

        assertEquals(file.text.indexOf("/var/docs"), info.startOffset)
    }

    /**
     * Use case: a value carrying a space, whose marking has to cover the whole name — the space alone would
     * not show which file has to be renamed.
     */
    fun `test the marking of a space covers the whole segment`() {
        site()
        val file = configure("nav:\n  - guide/getting started.md\n")

        val info = single(MkDocsBundle.message("reference.problem.spaceInSegment"))
        val start = file.text.indexOf("getting started.md")

        assertEquals(start, info.startOffset)
        assertEquals(start + "getting started.md".length, info.endOffset)
    }

    /**
     * Use case: the build output directory. The references leave it alone because it need not exist yet — but
     * whether MkDocs can create it at all is checked just like every other path, here a name Windows refuses.
     */
    fun `test checks the site dir as well`() {
        site()
        configure("site_name: Handbook\nsite_dir: build/out put\n")

        assertEquals(
            HighlightSeverity.WARNING,
            single(MkDocsBundle.message("reference.problem.spaceInSegment")).severity,
        )
    }

    /**
     * Use case: a site writing its build output outside the site — beside the checkout, at an absolute place
     * or on another volume. `site_dir` names where the build writes, not a part of the site, so none of the
     * three findings about leaving the site root may be reported for it.
     */
    fun `test allows a site dir beside the site`() {
        site()
        configure("site_name: Handbook\nsite_dir: ../build/docs\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: a site writing its build output to an absolute place, as a packaging step pointing at a web
     * root does. `site_dir` is not read relative to the site, so the absolute path must not be reported.
     */
    fun `test allows an absolute site dir`() {
        site()
        configure("site_name: Handbook\nsite_dir: /var/www/site\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: a site writing its build output onto another Windows volume. The drive letter names a machine,
     * which is exactly what a build output directory may do.
     */
    fun `test allows a site dir on another drive`() {
        site()
        configure("site_name: Handbook\nsite_dir: D:/out/site\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: the same values under `docs_dir`, which names the documentation sources. Those are part of the
     * site, so climbing out of it, an absolute path and a drive letter are all still reported.
     */
    fun `test still reports leaving the site for the docs dir`() {
        site()
        configure("site_name: Handbook\ndocs_dir: ../build/docs\n")

        assertEquals(HighlightSeverity.ERROR, single(MkDocsBundle.message("reference.problem.parentEscape")).severity)
    }

    /**
     * Use case: a navigation entry pointing at an absolute place. A page of the site lies inside the site, so
     * the value stays an error however `site_dir` is treated.
     */
    fun `test still reports an absolute nav target`() {
        site()
        configure("nav:\n  - /var/www/site\n")

        assertEquals(HighlightSeverity.ERROR, single(MkDocsBundle.message("reference.problem.absolutePath")).severity)
    }

    /**
     * Use case: a style sheet named with a drive letter. `extra_css` is loaded from below the documentation
     * directory, so naming a volume there is reported as before.
     */
    fun `test still reports a drive letter in extra css`() {
        site()
        configure("extra_css:\n  - D:/out/site.css\n")

        assertEquals(HighlightSeverity.ERROR, single(MkDocsBundle.message("reference.problem.driveLetter")).severity)
    }

    /**
     * Use case: a configuration file whose paths are all sound. Nothing may be reported, or every file would
     * carry a marking somewhere.
     */
    fun `test reports nothing for sound paths`() {
        site()
        configure("site_name: Handbook\ndocs_dir: docs\nnav:\n  - Home: index.md\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: any other YAML file of the project. Its values are no MkDocs paths, so a key spelled alike
     * must not be checked.
     */
    fun `test leaves a file that is no mkdocs configuration alone`() {
        site()
        myFixture.configureByText("docker-compose.yml", "docs_dir: /var/docs\n")

        assertEmpty(reportedProblems())
    }

    /** Creates the documentation directory of the site the configuration file is written into. */
    private fun site(): PsiFile = myFixture.addFileToProject("docs/index.md", "# Handbook\n")

    /** Configures a configuration file with [text]. */
    private fun configure(text: String): PsiFile = myFixture.configureByText("mkdocs.yml", text)

    /** Returns the single highlight of the configured file whose message is [message]. */
    private fun single(message: String): HighlightInfo {
        val infos = reportedProblems().filter { it.description == message }

        assertEquals("highlights saying '$message'", 1, infos.size)
        return infos.first()
    }

    /** Returns the highlights of the path check in the configured file. */
    private fun reportedProblems(): List<HighlightInfo> = myFixture.doHighlighting()
        .filter { info -> info.description?.let { isPathProblem(it) } == true }

    /**
     * Returns `true` if [description] is one of the messages the path check reports.
     *
     * Three of the messages carry the offending fragment, which is why they are recognised by the text behind
     * it rather than compared as a whole.
     *
     * @param description the message of a highlight
     */
    private fun isPathProblem(description: String): Boolean = MESSAGE_KEYS.any { key ->
        val message = MkDocsBundle.message(key, MARKER)
        if (message.contains(MARKER)) {
            description.endsWith(message.substringAfter(MARKER))
        } else {
            description == message
        }
    }

    private companion object {

        /** Stands in for the fragment a message carries, so the fixed part of it can be told apart. */
        const val MARKER = "@@"

        /** The keys of every message the path check can report. */
        val MESSAGE_KEYS = listOf(
            "reference.problem.forbiddenCharacter",
            "reference.problem.controlCharacter",
            "reference.problem.emptySegment",
            "reference.problem.trailingDotOrSpace",
            "reference.problem.absolutePath",
            "reference.problem.driveLetter",
            "reference.problem.parentEscape",
            "reference.problem.backslashSeparator",
            "reference.problem.reservedName",
            "reference.problem.nonAscii",
            "reference.problem.spaceInSegment",
            "reference.problem.tooLong",
        )
    }
}
