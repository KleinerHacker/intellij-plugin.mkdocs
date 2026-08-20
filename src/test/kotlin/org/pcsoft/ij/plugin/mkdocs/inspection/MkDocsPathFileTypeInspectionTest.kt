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

package org.pcsoft.ij.plugin.mkdocs.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the check saying that a path of `mkdocs.yml` names a file its key cannot use: which values are
 * reported, which are deliberately left alone, and which part of the value the report is drawn over.
 */
class MkDocsPathFileTypeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MkDocsPathFileTypeInspection())
    }

    /**
     * Use case: a page written into `extra_css`, which is what the completion no longer offers but a hand
     * written value still produces. The file exists, so nothing else marks it, while the built site would
     * load a Markdown page as a style sheet.
     */
    fun `test reports a page behind extra css`() {
        site()
        myFixture.addFileToProject("docs/css/extra.md", "")
        configure("extra_css:\n  - css/extra.md\n")

        val info = single()

        assertEquals(HighlightSeverity.WARNING, info.severity)
        assertEquals(
            MkDocsBundle.message(
                "inspection.pathFileType.problem",
                MkDocsBundle.message("inspection.pathFileType.type.extraCss"),
                ".css",
            ),
            info.description,
        )
    }

    /**
     * Use case: a style sheet written into `extra_javascript`. The counterpart of the case above, and the
     * message has to name the extensions of a script rather than those of a style sheet.
     */
    fun `test reports a style sheet behind extra javascript`() {
        site()
        configure("extra_javascript:\n  - js/extra.css\n")

        val info = single()

        assertEquals(
            MkDocsBundle.message(
                "inspection.pathFileType.problem",
                MkDocsBundle.message("inspection.pathFileType.type.extraJavascript"),
                ".js, .mjs",
            ),
            info.description,
        )
    }

    /**
     * Use case: the mapping form of an `extra_javascript` entry, where the path sits behind the `path` key.
     * It means the same thing as the plain form and therefore has to be checked the same way.
     */
    fun `test reports the mapping form of an extra javascript entry`() {
        site()
        configure("extra_javascript:\n  - path: js/extra.css\n    defer: true\n")

        assertEquals(HighlightSeverity.WARNING, single().severity)
    }

    /**
     * Use case: a page behind `theme.logo`, which the header of every page renders as an image. The same
     * value below `theme.favicon` is the icon of the browser tab and is just as unusable.
     */
    fun `test reports a page behind the theme images`() {
        site()
        configure("theme:\n  name: material\n  logo: img/logo.md\n  favicon: img/favicon.md\n")

        assertEquals("reported values", 2, reportedProblems().size)
    }

    /**
     * Use case: the values these keys are meant to carry. Nothing may be reported over them, or the check
     * would mark every correct configuration file.
     */
    fun `test reports nothing for a matching file type`() {
        site()
        configure(
            "theme:\n  name: material\n  logo: img/logo.svg\n  favicon: img/favicon.ico\n" +
                "extra_css:\n  - css/extra.css\nextra_javascript:\n  - js/extra.mjs\n"
        )

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: an image exported with an upper case extension, as a camera or an export dialogue writes it.
     * The file type is the same, so the spelling of the extension must not produce a report.
     */
    fun `test reports nothing for an extension in upper case`() {
        site()
        configure("theme:\n  name: material\n  logo: img/LOGO.PNG\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: a Material site naming one of the icons of the theme as its logo. `material/library` is no
     * path and carries no extension, so it must be left alone rather than marked as a missing image.
     */
    fun `test reports nothing for a value without an extension`() {
        site()
        configure("theme:\n  name: material\n  logo: material/library\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: a navigation entry. MkDocs renders the Markdown of a site but copies everything else next to
     * it, so `nav` prescribes no file type and nothing may be reported over its targets.
     */
    fun `test reports nothing for a key prescribing no type`() {
        site()
        configure("nav:\n  - Handbook: handbook.pdf\n")

        assertEmpty(reportedProblems())
    }

    /**
     * Use case: the same keys written in a file that is not an MkDocs configuration file. Nothing in it is
     * read by MkDocs, so nothing in it is any of the plugin's business.
     */
    fun `test reports nothing outside a configuration file`() {
        site()
        myFixture.configureByText("other.yml", "extra_css:\n  - css/extra.md\n")

        assertEmpty(reportedProblems())
    }

    /** Creates the documentation directory of the site, which every path is resolved against. */
    private fun site(): PsiFile = myFixture.addFileToProject("docs/index.md", "# Handbook\n")

    /** Configures a configuration file with [text]. */
    private fun configure(text: String): PsiFile = myFixture.configureByText("mkdocs.yml", text)

    /** Returns the single report of the configured file, failing if there is not exactly one. */
    private fun single(): HighlightInfo {
        val infos = reportedProblems()

        assertEquals("reported values", 1, infos.size)
        return infos.first()
    }

    /**
     * Returns the highlights this inspection contributed to the configured file.
     *
     * Told apart from the highlights of everything else running on the same file — the path check, the
     * unresolved references — by the fixed part of the message, which is what every report of this check
     * starts with.
     */
    private fun reportedProblems(): List<HighlightInfo> {
        val prefix = MkDocsBundle.message("inspection.pathFileType.problem", MARKER, MARKER)
            .substringBefore(MARKER)
        return myFixture.doHighlighting().filter { it.description?.startsWith(prefix) == true }
    }

    private companion object {

        /** Stands in for the fragments the message carries, so its fixed part can be told apart. */
        const val MARKER = "@@"
    }
}
