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

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what the file references of a configuration file resolve to: every kind of path value is followed to
 * the file or directory MkDocs itself would read, a value pointing nowhere is marked, and the completion of a
 * directory key offers directories only.
 */
class MkDocsPathReferenceContributorTest : BasePlatformTestCase() {

    /**
     * Use case: Ctrl+Click on the `docs_dir` value. It is read next to the configuration file, so the
     * documentation directory of the site is what opens.
     */
    fun `test resolves docs dir against the site root`() {
        site()
        val target = targetAtCaret("site_name: Handbook\ndocs_dir: do<caret>cs\n")

        assertEquals("docs", target.name)
        assertTrue("docs_dir names a directory", target.isDirectory)
    }

    /**
     * Use case: Ctrl+Click on `site_dir` while the site has already been built once. The build output
     * directory is there, so it opens like any other target.
     */
    fun `test resolves site dir against the site root`() {
        site()
        myFixture.addFileToProject("site/index.html", "<html></html>")
        val target = targetAtCaret("site_name: Handbook\nsite_dir: si<caret>te\n")

        assertEquals("site", target.name)
        assertTrue("site_dir names a directory", target.isDirectory)
    }

    /**
     * Use case: Ctrl+Click on the logo of the theme. Unlike the two directory keys it is read below the
     * documentation directory, which is what the default context of the reference set has to reflect.
     */
    fun `test resolves the logo against the documentation directory`() {
        site()
        myFixture.addFileToProject("docs/img/logo.png", "")
        val target = targetAtCaret("theme:\n  name: material\n  logo: img/lo<caret>go.png\n")

        assertEquals("logo.png", target.name)
    }

    /**
     * Use case: Ctrl+Click on the favicon, the second image key of the theme. It is read exactly like the
     * logo.
     */
    fun `test resolves the favicon against the documentation directory`() {
        site()
        myFixture.addFileToProject("docs/img/favicon.ico", "")
        val target = targetAtCaret("theme:\n  name: material\n  favicon: img/fav<caret>icon.ico\n")

        assertEquals("favicon.ico", target.name)
    }

    /**
     * Use case: Ctrl+Click on a style sheet listed in `extra_css`. The value hangs in a sequence rather than
     * behind a key, which must make no difference to the resolution.
     */
    fun `test resolves an entry of extra css`() {
        site()
        myFixture.addFileToProject("docs/stylesheets/extra.css", "body { color: red; }\n")
        val target = targetAtCaret("extra_css:\n  - stylesheets/ex<caret>tra.css\n")

        assertEquals("extra.css", target.name)
    }

    /**
     * Use case: Ctrl+Click on a page of the navigation written as a bare path. It is the most common
     * navigation there is and has to lead to the page below the documentation directory.
     */
    fun `test resolves a flat nav entry`() {
        site()
        val target = targetAtCaret("nav:\n  - ind<caret>ex.md\n")

        assertEquals("index.md", target.name)
    }

    /**
     * Use case: Ctrl+Click on a page buried in two navigation sections. The navigation nests without a fixed
     * depth, so the level the entry sits on must not matter.
     */
    fun `test resolves a nested nav entry`() {
        site()
        myFixture.addFileToProject("docs/guide/tuning.md", "# Tuning\n")
        val text = "nav:\n  - Guide:\n      - Advanced:\n          - Tuning: guide/tu<caret>ning.md\n"
        val target = targetAtCaret(text)

        assertEquals("tuning.md", target.name)
    }

    /**
     * Use case: a page of the navigation that was renamed away. The entry now points nowhere, and MkDocs
     * would fail the build over it — so the reference must not resolve and the value has to be marked.
     */
    fun `test marks a nav entry pointing nowhere`() {
        site()
        val reference = referenceAtCaret("nav:\n  - miss<caret>ing.md\n")

        assertNull("a renamed page must not resolve", reference!!.resolve())
        assertFalse("an unknown page has to be marked", reference.isSoft)
    }

    /**
     * Use case: a fresh checkout of a site that has never been built here. `site_dir` names a directory that
     * is not there and that nobody put under version control, so it must stay unmarked.
     */
    fun `test leaves a missing site dir unmarked`() {
        site()
        val reference = referenceAtCaret("site_name: Handbook\nsite_dir: si<caret>te\n")

        assertNull("the directory is not there yet", reference!!.resolve())
        assertTrue("a missing build output directory is nobody's business", reference.isSoft)
        assertEmpty(errorsOf())
    }

    /**
     * Use case: a navigation entry pointing at another site. An address is no path, so the plugin contributes
     * no reference to it and it is left alone.
     */
    fun `test contributes no reference to an external nav target`() {
        site()

        assertEmpty(fileReferencesAtCaret("nav:\n  - Upstream: https://www.mkdocs<caret>.org/\n"))
    }

    /**
     * Use case: an ordinary text value such as the site name. Turning it into a file reference would mark
     * half the configuration file red.
     */
    fun `test contributes no reference to an unrelated value`() {
        site()

        assertEmpty(fileReferencesAtCaret("site_name: Hand<caret>book\n"))
    }

    /**
     * Use case: completion inside `docs_dir`. The key names a directory, so a page lying next to the
     * configuration file must not be offered — accepting it would produce a configuration MkDocs refuses.
     */
    fun `test offers only directories for docs dir`() {
        site()
        myFixture.addFileToProject("README.md", "# Handbook\n")
        myFixture.configureByText("mkdocs.yml", "site_name: Handbook\ndocs_dir: <caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "docs")
        assertDoesntContain(offered, "README.md")
    }

    /**
     * Use case: completion inside `site_dir`, the second directory key. It has to behave like `docs_dir`
     * although its target need not exist yet.
     */
    fun `test offers only directories for site dir`() {
        site()
        myFixture.addFileToProject("README.md", "# Handbook\n")
        myFixture.configureByText("mkdocs.yml", "site_name: Handbook\nsite_dir: <caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "docs")
        assertDoesntContain(offered, "README.md")
    }

    /**
     * Use case: completion inside a navigation entry. Here the pages are what is wanted, so the file below
     * the documentation directory must be offered.
     */
    fun `test offers pages for a nav entry`() {
        site()
        myFixture.configureByText("mkdocs.yml", "nav:\n  - <caret>\n")

        myFixture.completeBasic()

        assertContainsElements(myFixture.lookupElementStrings ?: emptyList(), "index.md")
    }

    /**
     * Use case: completion inside an `extra_css` entry. MkDocs loads the value as a style sheet, so only a
     * `*.css` file may be offered — a page or a script lying next to it would produce a site that cannot be
     * built.
     */
    fun `test offers only style sheets for extra css`() {
        site()
        myFixture.addFileToProject("docs/css/extra.css", "")
        myFixture.addFileToProject("docs/css/theme.css", "")
        myFixture.addFileToProject("docs/css/extra.js", "")
        myFixture.addFileToProject("docs/css/readme.md", "")
        myFixture.configureByText("mkdocs.yml", "extra_css:\n  - css/<caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "extra.css", "theme.css")
        assertDoesntContain(offered, "extra.js", "readme.md")
    }

    /**
     * Use case: completion inside an `extra_javascript` entry, the counterpart of `extra_css`. Only a script
     * belongs there, in both extensions a browser loads as one.
     */
    fun `test offers only scripts for extra javascript`() {
        site()
        myFixture.addFileToProject("docs/js/extra.js", "")
        myFixture.addFileToProject("docs/js/module.mjs", "")
        myFixture.addFileToProject("docs/js/extra.css", "")
        myFixture.configureByText("mkdocs.yml", "extra_javascript:\n  - js/<caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "extra.js", "module.mjs")
        assertDoesntContain(offered, "extra.css")
    }

    /**
     * Use case: completion inside `theme.logo`. The header of the site renders an image, so only an image
     * file may be offered — and every format a browser draws has to be among them, not just the two most
     * common ones.
     */
    fun `test offers only images for the theme logo`() {
        site()
        myFixture.addFileToProject("docs/img/logo.png", "")
        myFixture.addFileToProject("docs/img/logo.svg", "")
        myFixture.addFileToProject("docs/img/logo.md", "")
        myFixture.configureByText("mkdocs.yml", "theme:\n  name: material\n  logo: img/<caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "logo.png", "logo.svg")
        assertDoesntContain(offered, "logo.md")
    }

    /**
     * Use case: completion inside `theme.favicon`, which the browser renders exactly like the logo and
     * therefore accepts the same files.
     */
    fun `test offers only images for the theme favicon`() {
        site()
        myFixture.addFileToProject("docs/img/favicon.ico", "")
        myFixture.addFileToProject("docs/img/favicon.png", "")
        myFixture.addFileToProject("docs/img/favicon.css", "")
        myFixture.configureByText("mkdocs.yml", "theme:\n  name: material\n  favicon: img/<caret>\n")

        myFixture.completeBasic()
        val offered = myFixture.lookupElementStrings ?: emptyList()

        assertContainsElements(offered, "favicon.ico", "favicon.png")
        assertDoesntContain(offered, "favicon.css")
    }

    /**
     * Use case: walking to the file through a sub directory while a filtered key is being written. The
     * directory carries no extension of its own and would fall out of every filter, which would leave the
     * user unable to reach the style sheet at all.
     */
    fun `test still offers directories for a filtered key`() {
        site()
        myFixture.addFileToProject("docs/css/extra.css", "")
        myFixture.addFileToProject("docs/img/logo.png", "")
        myFixture.configureByText("mkdocs.yml", "extra_css:\n  - <caret>\n")

        myFixture.completeBasic()

        assertContainsElements(myFixture.lookupElementStrings ?: emptyList(), "css", "img")
    }

    /**
     * Use case: a site writing its build output to an absolute place. `site_dir` names where the build writes
     * and not a part of the site, so the platform has to read the value as the absolute path it is instead of
     * hunting for it below the site root — and nothing may be reported over it.
     */
    fun `test reads an absolute site dir as absolute`() {
        site()
        val references = fileReferencesAtCaret("site_name: Handbook\nsite_dir: /var/www/si<caret>te\n")

        assertNotEmpty(references)
        assertTrue("site_dir may name an absolute place", references.first().fileReferenceSet.isAbsolutePathReference)
        assertEmpty(errorsOf())
    }

    /**
     * Use case: a build output directory beside the checkout, written with `..`. It leaves the site root,
     * which is ordinary for build output, so the value must not be marked.
     */
    fun `test leaves a site dir beside the site unmarked`() {
        site()
        val reference = referenceAtCaret("site_name: Handbook\nsite_dir: ../build/do<caret>cs\n")

        assertNotNull("a path beside the site is still a path", reference)
        assertTrue("a build output directory is nobody's business", reference!!.isSoft)
        assertEmpty(errorsOf())
    }

    /**
     * Use case: the same absolute value under `docs_dir`. MkDocs reads the documentation directory relative to
     * the site, so the reference must not be read as an absolute path.
     */
    fun `test reads an absolute docs dir as relative`() {
        site()
        val references = fileReferencesAtCaret("site_name: Handbook\ndocs_dir: /var/www/si<caret>te\n")

        assertNotEmpty(references)
        assertFalse(
            "only site_dir may name an absolute place",
            references.first().fileReferenceSet.isAbsolutePathReference,
        )
    }

    /** Creates the documentation directory of the site the configuration file is written into. */
    private fun site(): PsiFile = myFixture.addFileToProject("docs/index.md", "# Handbook\n")

    /** Configures a configuration file with [text] and returns the reference under the caret. */
    private fun referenceAtCaret(text: String) = run {
        myFixture.configureByText("mkdocs.yml", text)
        myFixture.getReferenceAtCaretPosition()
    }

    /**
     * Returns the file references the plugin contributed to the scalar under the caret of [text].
     *
     * Asking the scalar rather than the caret keeps the references of other contributors — the platform links
     * an address on its own — out of the answer.
     */
    private fun fileReferencesAtCaret(text: String): List<FileReference> {
        myFixture.configureByText("mkdocs.yml", text)
        val scalar = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset),
            YAMLScalar::class.java,
            false,
        )
        assertNotNull("no scalar under the caret", scalar)

        return scalar!!.references.filterIsInstance<FileReference>()
    }

    /** Returns the file or directory the reference under the caret of [text] resolves to. */
    private fun targetAtCaret(text: String): PsiFileSystemItem {
        val reference = referenceAtCaret(text)
        assertNotNull("no reference under the caret", reference)

        val resolved: PsiElement? = reference!!.resolve()
        assertNotNull("the reference resolves to nothing", resolved)
        return resolved as PsiFileSystemItem
    }

    /** Returns the messages of the errors reported in the configured file. */
    private fun errorsOf(): List<String> = myFixture.doHighlighting()
        .filter { it.severity == HighlightSeverity.ERROR }
        .mapNotNull { it.description }
}
