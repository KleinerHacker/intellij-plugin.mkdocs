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

import com.intellij.openapi.util.TextRange
import org.junit.Assert.*
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the path check itself, one case per problem plus the paths that are simply fine. The check is a pure
 * function of the path string, so it needs neither the platform nor a fixture.
 */
class MkDocsPathValidatorTest {

    /**
     * Use case: the paths a configuration file is normally full of — a page, a nested page, a style sheet, a
     * directory written with a trailing slash and a step out of the documentation directory that stays inside
     * the site. None of them may be reported, or the file would be yellow from the first day.
     */
    @Test
    fun `valid paths are not reported`() {
        for (path in listOf(
            "index.md",
            "guide/advanced/tuning.md",
            "stylesheets/extra.css",
            "docs",
            "build/site/",
            "./index.md",
            "sub/../index.md",
        )) {
            assertEquals(
                "$path must not be reported",
                emptyList<MkDocsPathFinding>(),
                MkDocsPathValidator.validate(path)
            )
        }
    }

    /**
     * Use case: an empty value. There is nothing to check, and the emptiness itself is not the path check's
     * business — the kind detection drops a blank value before it ever gets here.
     */
    @Test
    fun `an empty path yields nothing`() {
        assertEquals(emptyList<MkDocsPathFinding>(), MkDocsPathValidator.validate(""))
    }

    /**
     * Use case: a page whose name carries a character Windows refuses, here the colon of a title copied into
     * the file name. The offending character alone is reported, so the marking points at what to remove.
     */
    @Test
    fun `a forbidden character is reported at its position`() {
        val finding = single("guide/how:to.md", MkDocsPathProblem.FORBIDDEN_CHARACTER)

        assertEquals(":", finding.text)
        assertEquals(TextRange(9, 10), finding.range)
    }

    /**
     * Use case: a control character that slipped into the value through a copy from another document. No file
     * system accepts one in a name, so this is broken everywhere.
     */
    @Test
    fun `a control character is reported`() {
        val finding = single("index\u0007.md", MkDocsPathProblem.CONTROL_CHARACTER)

        assertEquals(TextRange(5, 6), finding.range)
        assertTrue("a control character breaks on every system", finding.problem.isError(windows = false))
    }

    /**
     * Use case: two separators with no name between them, the usual result of joining two path fragments by
     * hand. MkDocs does not silently collapse them.
     */
    @Test
    fun `an empty segment between two names is reported`() {
        val finding = single("guide//tuning.md", MkDocsPathProblem.EMPTY_SEGMENT)

        assertEquals(TextRange(6, 6), finding.range)
    }

    /**
     * Use case: a directory written with a trailing slash. That is how a directory may be spelled, so the
     * empty segment at the end must not be reported — the same for the leading one, which is already covered
     * by the absolute path.
     */
    @Test
    fun `a trailing separator is no empty segment`() {
        assertTrue(problemsOf("build/site/").none { it == MkDocsPathProblem.EMPTY_SEGMENT })
        assertTrue(problemsOf("/docs").none { it == MkDocsPathProblem.EMPTY_SEGMENT })
    }

    /**
     * Use case: a name ending in a dot or a space, which Windows strips on creation so the file is never
     * found again under the name the configuration file spells.
     */
    @Test
    fun `a segment ending in a dot or a space is reported`() {
        assertEquals(TextRange(5, 6), single("guide./index.md", MkDocsPathProblem.TRAILING_DOT_OR_SPACE).range)
        assertEquals(TextRange(5, 6), single("guide /index.md", MkDocsPathProblem.TRAILING_DOT_OR_SPACE).range)
    }

    /**
     * Use case: a path copied from a terminal and left absolute. MkDocs resolves every one of these values
     * against the site, so a path starting at the root of the file system finds nothing.
     */
    @Test
    fun `an absolute path is reported`() {
        assertEquals(TextRange(0, 1), single("/var/docs", MkDocsPathProblem.ABSOLUTE_PATH).range)
    }

    /**
     * Use case: a path pasted from the Windows explorer, drive letter and all. It is absolute in the worst
     * way — it names a machine, not a place inside the site.
     */
    @Test
    fun `a drive letter is reported`() {
        val problems = problemsOf("C:/docs")

        assertTrue(problems.contains(MkDocsPathProblem.DRIVE_LETTER))
        assertFalse(
            "the colon of the drive is not a forbidden character",
            problems.contains(MkDocsPathProblem.FORBIDDEN_CHARACTER)
        )
    }

    /**
     * Use case: a page addressed with one `..` too many, which leaves the directory the value is resolved
     * against. MkDocs does not follow a path out of the site.
     */
    @Test
    fun `a step out of the base directory is reported`() {
        val finding = single("../outside.md", MkDocsPathProblem.PARENT_ESCAPE)

        assertEquals(TextRange(0, 2), finding.range)
        assertTrue("leaving the site never works", finding.problem.isError(windows = false))
    }

    /**
     * Use case: a path written with backslashes, as the Windows explorer spells it. It works on the machine
     * it was written on and breaks on every build server, which is exactly what a warning is for.
     */
    @Test
    fun `a backslash separator is reported as a warning only`() {
        val finding = single("guide\\tuning.md", MkDocsPathProblem.BACKSLASH_SEPARATOR)

        assertEquals(TextRange(5, 6), finding.range)
        assertFalse("a backslash is never an error", finding.problem.isError(windows = true))
    }

    /**
     * Use case: a page called `con.md`. Windows reserves the name for a device whatever extension follows, so
     * the file cannot even be created there.
     */
    @Test
    fun `a reserved windows name is reported`() {
        assertEquals("con.md", single("con.md", MkDocsPathProblem.RESERVED_NAME).text)
        assertEquals("LPT1", single("guide/LPT1", MkDocsPathProblem.RESERVED_NAME).text)
    }

    /**
     * Use case: a page named in a national alphabet. The run of non ASCII letters is reported as one finding
     * — writing a word that way is one decision of the author, not one per letter.
     */
    @Test
    fun `a run of non ascii characters is reported as one finding`() {
        val findings = MkDocsPathValidator.validate("docs/Übersicht.md")
            .filter { it.problem == MkDocsPathProblem.NON_ASCII }

        assertEquals(1, findings.size)
        assertEquals("Ü", findings.first().text)
    }

    /**
     * Use case: a name carrying a space, which every address of the built site then has to escape. It works
     * everywhere, so it stays a warning.
     */
    @Test
    fun `a space inside a segment is reported as a warning only`() {
        val finding = single("guide/getting started.md", MkDocsPathProblem.SPACE_IN_SEGMENT)

        assertEquals("getting started.md", finding.text)
        assertFalse("a space works on every system", finding.problem.isError(windows = true))
    }

    /**
     * Use case: a path deep and long enough to run into the path limit of Windows once the checkout directory
     * is put in front of it.
     */
    @Test
    fun `an overlong path is reported`() {
        val path = (1..20).joinToString("/") { "segment-of-some-length" }

        assertTrue(path.length > MkDocsPathValidator.MAX_PATH_LENGTH)
        assertEquals(TextRange(0, path.length), single(path, MkDocsPathProblem.TOO_LONG).range)
    }

    /**
     * Use case: a single name longer than any common file system allows for one entry. The whole path may
     * still be short, so the segment has to be measured on its own.
     */
    @Test
    fun `an overlong segment is reported`() {
        val path = "a".repeat(MkDocsPathValidator.MAX_SEGMENT_LENGTH + 1) + ".md"
        val findings = MkDocsPathValidator.validate(path).filter { it.problem == MkDocsPathProblem.TOO_LONG }

        assertTrue("the segment itself must be reported", findings.any { it.range.startOffset == 0 })
    }

    /**
     * Use case: a value collecting several problems at once. They are reported in the order they appear in
     * the text, so the first marking the reader sees is the first thing wrong.
     */
    @Test
    fun `findings are ordered by their position`() {
        val findings = MkDocsPathValidator.validate("/gui de\\x?y.md")

        assertTrue(findings.size > 1)
        assertEquals(
            findings.map { it.range.startOffset },
            findings.map { it.range.startOffset }.sorted(),
        )
    }

    /**
     * Use case: the split into "broken here" and "broken elsewhere". Five problems break on every operating
     * system, four only on Windows and three never rise beyond a warning.
     */
    @Test
    fun `severity follows the operating system`() {
        for (problem in listOf(
            MkDocsPathProblem.CONTROL_CHARACTER,
            MkDocsPathProblem.EMPTY_SEGMENT,
            MkDocsPathProblem.ABSOLUTE_PATH,
            MkDocsPathProblem.DRIVE_LETTER,
            MkDocsPathProblem.PARENT_ESCAPE,
        )) {
            assertTrue("$problem breaks everywhere", problem.isError(windows = true))
            assertTrue("$problem breaks everywhere", problem.isError(windows = false))
        }
        for (problem in listOf(
            MkDocsPathProblem.FORBIDDEN_CHARACTER,
            MkDocsPathProblem.TRAILING_DOT_OR_SPACE,
            MkDocsPathProblem.RESERVED_NAME,
            MkDocsPathProblem.TOO_LONG,
        )) {
            assertTrue("$problem breaks on Windows", problem.isError(windows = true))
            assertFalse("$problem works elsewhere", problem.isError(windows = false))
        }
        for (problem in listOf(
            MkDocsPathProblem.BACKSLASH_SEPARATOR,
            MkDocsPathProblem.NON_ASCII,
            MkDocsPathProblem.SPACE_IN_SEGMENT,
        )) {
            assertFalse("$problem is never an error", problem.isError(windows = true))
            assertFalse("$problem is never an error", problem.isError(windows = false))
        }
    }

    /**
     * Use case: the value of `site_dir`, which names the build output and may write outside the site — next to
     * the checkout, above it or onto another volume. None of the three findings about leaving the base
     * directory may be reported for such a value.
     */
    @Test
    fun `a value allowed to leave the base directory reports no leaving problem`() {
        for (path in listOf("../build/docs", "/var/www/site", "D:/out/site")) {
            assertEquals(
                "$path must not be reported for a value that may leave the site",
                emptyList<MkDocsPathProblem>(),
                MkDocsPathValidator.validate(path, mayLeaveBaseDirectory = true)
                    .map { it.problem }
                    .filter { it in LEAVING },
            )
        }
    }

    /**
     * Use case: the same three values under a key that has to stay inside the site, such as `docs_dir` or a
     * navigation entry. Here leaving the base directory is exactly what has to be reported, which is what the
     * default of the new parameter keeps doing.
     */
    @Test
    fun `the same values are reported for a value that has to stay inside`() {
        assertTrue(problemsOf("../build/docs").contains(MkDocsPathProblem.PARENT_ESCAPE))
        assertTrue(problemsOf("/var/www/site").contains(MkDocsPathProblem.ABSOLUTE_PATH))
        assertTrue(problemsOf("D:/out/site").contains(MkDocsPathProblem.DRIVE_LETTER))
    }

    /**
     * Use case: a build output directory whose name a file system refuses anyway. Being allowed to lie outside
     * the site says nothing about how the name is spelled, so every other finding is still reported.
     */
    @Test
    fun `everything else is still reported for a value that may leave the base directory`() {
        val problems = MkDocsPathValidator.validate("/var/con/out put\u0007/x?y//z ", mayLeaveBaseDirectory = true)
            .map { it.problem }

        assertFalse("leaving the site is allowed here", problems.any { it in LEAVING })
        for (expected in listOf(
            MkDocsPathProblem.CONTROL_CHARACTER,
            MkDocsPathProblem.EMPTY_SEGMENT,
            MkDocsPathProblem.TRAILING_DOT_OR_SPACE,
            MkDocsPathProblem.FORBIDDEN_CHARACTER,
            MkDocsPathProblem.RESERVED_NAME,
            MkDocsPathProblem.SPACE_IN_SEGMENT,
        )) {
            assertTrue("$expected is still reported", problems.contains(expected))
        }
    }

    /** Returns the single finding of [problem] in [path], failing if there is none or more than one. */
    private fun single(path: String, problem: MkDocsPathProblem): MkDocsPathFinding {
        val findings = MkDocsPathValidator.validate(path).filter { it.problem == problem }

        assertEquals("findings of $problem in '$path'", 1, findings.size)
        return findings.first()
    }

    /** Returns the problems found in [path]. */
    private fun problemsOf(path: String): List<MkDocsPathProblem> =
        MkDocsPathValidator.validate(path).map { it.problem }

    private companion object {

        /** The problems that say nothing but "this path leads out of the base directory". */
        val LEAVING = setOf(
            MkDocsPathProblem.ABSOLUTE_PATH,
            MkDocsPathProblem.DRIVE_LETTER,
            MkDocsPathProblem.PARENT_ESCAPE,
        )
    }
}
