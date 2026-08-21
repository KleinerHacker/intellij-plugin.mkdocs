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
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the mark on a path of an MkDocs configuration file whose target does not exist. The file references
 * carry that answer already, but YAML draws nothing for an unresolved reference, so what is tested here is
 * that the mark reaches the editor at all — with which severity, over which part of the value, and for which
 * of the keys.
 */
class MkDocsMissingPathAnnotatorTest : BasePlatformTestCase() {

    /**
     * Use case: `docs_dir` naming a directory nobody created. MkDocs stops the build over it, so it is an
     * error, and it names a directory, so the message has to say directory.
     */
    fun `test reports a missing docs dir`() {
        site()
        configure("site_name: Handbook\ndocs_dir: manual\n")

        val info = single(message(DIRECTORY, "manual"))

        assertEquals(HighlightSeverity.ERROR, info.severity)
    }

    /**
     * Use case: a page renamed without its `nav` entry being followed. The entry points at a file, so the
     * message names a file rather than a directory.
     */
    fun `test reports a missing nav page`() {
        site()
        configure("nav:\n  - Home: missing.md\n")

        assertEquals(HighlightSeverity.ERROR, single(message(FILE, "missing.md")).severity)
    }

    /**
     * Use case: a style sheet deleted while its `extra_css` entry stayed behind. The value is resolved against
     * the documentation directory, so the mark has to appear although the file does not exist below the site
     * root either.
     */
    fun `test reports a missing style sheet`() {
        site()
        configure("extra_css:\n  - stylesheets/extra.css\n")

        assertEquals(HighlightSeverity.ERROR, single(message(DIRECTORY, "stylesheets")).severity)
    }

    /**
     * Use case: `theme.custom_dir` naming the override directory of the theme. It is resolved against the
     * configuration file rather than against `docs_dir`, and a missing one is reported like every other key.
     */
    fun `test reports a missing custom dir`() {
        site()
        configure("theme:\n  name: material\n  custom_dir: overrides\n")

        assertEquals(HighlightSeverity.ERROR, single(message(DIRECTORY, "overrides")).severity)
    }

    /**
     * Use case: `theme.logo` naming an image below a directory the site does not have. The mark sits on the
     * directory, which is where the path stops being true.
     */
    fun `test reports a missing logo`() {
        site()
        configure("theme:\n  name: material\n  logo: assets/logo.svg\n")

        assertEquals(HighlightSeverity.ERROR, single(message(DIRECTORY, "assets")).severity)
    }

    /**
     * Use case: a path whose last segment is missing while everything before it exists. The mark has to sit on
     * the file, which is the only segment that leads nowhere.
     */
    fun `test reports the last segment when the directories exist`() {
        site()
        myFixture.addFileToProject("docs/guide/index.md", "# Guide\n")
        val file = configure("nav:\n  - guide/tuning.md\n")

        val info = single(message(FILE, "tuning.md"))
        val start = file.text.indexOf("tuning.md")

        assertEquals(start, info.startOffset)
        assertEquals(start + "tuning.md".length, info.endOffset)
    }

    /**
     * Use case: a path whose directory is already gone. Every segment behind it leads nowhere as well, and
     * marking all of them would report one mistake three times — only the first is reported, and it is a
     * directory although the key reads a file.
     */
    fun `test reports the first missing segment only`() {
        site()
        val file = configure("nav:\n  - old/guide/tuning.md\n")

        val info = single(message(DIRECTORY, "old"))
        val start = file.text.indexOf("old")

        assertEquals(1, reportedProblems().size)
        assertEquals(start, info.startOffset)
        assertEquals(start + "old".length, info.endOffset)
    }

    /**
     * Use case: the value is written in quotes, as YAML allows anywhere. The quotes are no part of the path, so
     * the mark has to start behind the opening one.
     */
    fun `test the marking skips the quotes of a quoted value`() {
        site()
        val file = configure("site_name: Handbook\ndocs_dir: \"manual\"\n")

        assertEquals(file.text.indexOf("manual"), single(message(DIRECTORY, "manual")).startOffset)
    }

    /**
     * Use case: the build output directory of a fresh checkout. `site_dir` is soft — before the first build it
     * simply is not there — so a mark would turn every checkout red over nothing.
     */
    fun `test leaves a missing site dir alone`() {
        site()
        configure("site_name: Handbook\nsite_dir: build/site\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: `theme.logo` carrying the name of one of the theme's own icons rather than a path. It has no
     * extension, it is no file, and reporting it would mark an ordinary Material configuration.
     */
    fun `test leaves a theme icon name alone`() {
        site()
        configure("theme:\n  name: material\n  logo: material/library\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: an absolute path, which the path check already reports as an error. It cannot resolve *because*
     * of that, so a second mark saying so adds nothing to what the author already reads.
     */
    fun `test leaves a value the path check already reports alone`() {
        site()
        configure("site_name: Handbook\ndocs_dir: /var/docs\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: a configuration file whose paths all lead somewhere. Nothing may be reported, or every sound
     * file would carry a mark.
     */
    fun `test reports nothing for existing targets`() {
        site()
        configure("site_name: Handbook\ndocs_dir: docs\nnav:\n  - Home: index.md\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: any other YAML file of the project. Its values are no MkDocs paths, so a key spelled alike must
     * not be marked.
     */
    fun `test leaves a file that is no mkdocs configuration alone`() {
        site()
        myFixture.configureByText("docker-compose.yml", "docs_dir: manual\n")

        assertEmpty(reportedProblems())
    }

    /** Creates the documentation directory of the site the configuration file is written into. */
    private fun site(): PsiFile = myFixture.addFileToProject("docs/index.md", "# Handbook\n")

    /** Configures a configuration file with [text]. */
    private fun configure(text: String): PsiFile = myFixture.configureByText("mkdocs.yml", text)

    /**
     * Returns the message reported for a missing target.
     *
     * @param key the bundle key of the file or the directory message
     * @param text the segment of the path that leads nowhere
     */
    private fun message(key: String, text: String): String = MkDocsBundle.message(key, text)

    /** Returns the single highlight of the configured file whose message is [message]. */
    private fun single(message: String): HighlightInfo {
        val infos = reportedProblems().filter { it.description == message }

        assertEquals("highlights saying '$message'", 1, infos.size)
        return infos.first()
    }

    /** Returns the highlights of the existence check in the configured file. */
    private fun reportedProblems(): List<HighlightInfo> = myFixture.doHighlighting()
        .filter { info -> info.description?.let { isMissingProblem(it) } == true }

    /**
     * Returns `true` if [description] is one of the two messages the existence check reports.
     *
     * Both carry the offending segment, so they are recognised by the fixed text in front of it.
     *
     * @param description the message of a highlight
     */
    private fun isMissingProblem(description: String): Boolean =
        listOf(FILE, DIRECTORY).any { description.startsWith(MkDocsBundle.message(it, MARKER).substringBefore(MARKER)) }

    private companion object {

        /** The key of the message reported for a file that is not there. */
        const val FILE = "reference.problem.missingFile"

        /** The key of the message reported for a directory that is not there. */
        const val DIRECTORY = "reference.problem.missingDirectory"

        /** Stands in for the segment a message carries, so the fixed part of it can be told apart. */
        const val MARKER = "@@"
    }
}
