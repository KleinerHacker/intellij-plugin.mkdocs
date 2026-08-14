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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers which scalars of a configuration file [MkDocsPathKind] reads as a path. Everything built on top of
 * the kinds — the references, the gutter icons and the path check — asks this one question first, so a scalar
 * wrongly claimed here would be marked, linked and checked as a file everywhere.
 *
 * Needs the platform because the decision is made on the YAML PSI of the bundled plugin.
 */
class MkDocsPathKindTest : BasePlatformTestCase() {

    /**
     * Use case: the `docs_dir` key of a site whose sources do not lie in the default directory. It names the
     * directory MkDocs reads the pages from and is resolved against the site root.
     */
    fun `test reads docs dir as a directory below the site root`() {
        val kind = kindOf("site_name: Handbook\ndocs_dir: manual\n", "manual")

        assertEquals(MkDocsPathKind.DOCS_DIR, kind)
        assertTrue("docs_dir names a directory", kind!!.directory)
        assertFalse("a missing documentation directory breaks the build", kind.soft)
        assertFalse("docs_dir is resolved against the site root", kind.relativeToDocsDir)
    }

    /**
     * Use case: the `site_dir` key, naming the build output directory. It is a directory below the site root
     * as well, but a soft one: before the first build it simply does not exist.
     */
    fun `test reads site dir as a soft directory`() {
        val kind = kindOf("site_name: Handbook\nsite_dir: build/site\n", "build/site")

        assertEquals(MkDocsPathKind.SITE_DIR, kind)
        assertTrue("site_dir names a directory", kind!!.directory)
        assertTrue("build output need not exist yet", kind.soft)
        assertFalse("site_dir is resolved against the site root", kind.relativeToDocsDir)
    }

    /**
     * Use case: the logo of the theme, written below the `theme` key. It names an image file MkDocs looks for
     * below the documentation directory.
     */
    fun `test reads the logo of the theme`() {
        val kind = kindOf("theme:\n  name: material\n  logo: img/logo.png\n", "img/logo.png")

        assertEquals(MkDocsPathKind.LOGO, kind)
        assertFalse("the logo is a file", kind!!.directory)
        assertTrue("the logo is resolved against docs_dir", kind.relativeToDocsDir)
    }

    /**
     * Use case: the favicon of the site, the second image key below `theme`. It is read exactly like the
     * logo.
     */
    fun `test reads the favicon of the theme`() {
        assertEquals(
            MkDocsPathKind.FAVICON,
            kindOf("theme:\n  name: material\n  favicon: img/favicon.ico\n", "img/favicon.ico"),
        )
    }

    /**
     * Use case: a style sheet pulled in through `extra_css`. The value is an entry of a sequence rather than
     * the value of a key, so it is recognised through the sequence item it hangs in.
     */
    fun `test reads an entry of extra css`() {
        val kind = kindOf("extra_css:\n  - stylesheets/extra.css\n", "stylesheets/extra.css")

        assertEquals(MkDocsPathKind.EXTRA_CSS, kind)
        assertFalse("a style sheet is a file", kind!!.directory)
        assertTrue("extra_css is resolved against docs_dir", kind.relativeToDocsDir)
    }

    /**
     * Use case: the shortest navigation entry there is — a bare path without a title of its own. It points at
     * a page below the documentation directory.
     */
    fun `test reads a bare nav entry`() {
        assertEquals(MkDocsPathKind.NAV, kindOf("nav:\n  - index.md\n", "index.md"))
    }

    /**
     * Use case: the common navigation entry, a title in front of the path. The title is the key and the page
     * is the value, so the path is what the entry points at.
     */
    fun `test reads a titled nav entry`() {
        assertEquals(MkDocsPathKind.NAV, kindOf("nav:\n  - Home: index.md\n", "index.md"))
    }

    /**
     * Use case: a page buried inside two navigation sections. The navigation nests without a fixed depth, so
     * the recognition must not depend on the level the entry sits on.
     */
    fun `test reads a nav entry nested in sections`() {
        val text = "nav:\n  - Guide:\n      - Advanced:\n          - Tuning: guide/tuning.md\n"

        assertEquals(MkDocsPathKind.NAV, kindOf(text, "guide/tuning.md"))
    }

    /**
     * Use case: a navigation entry pointing at another site. An address is not a path — turning it into a
     * file reference would mark every external link of the navigation red.
     */
    fun `test ignores an external nav target`() {
        assertNull(kindOf("nav:\n  - Upstream: https://www.mkdocs.org/\n", "https://www.mkdocs.org/"))
    }

    /**
     * Use case: the name of a navigation section, which is a key opening a list of entries rather than a
     * value pointing at a page. There is no file behind it.
     */
    fun `test ignores the name of a nav section`() {
        val file = myFixture.configureByText("mkdocs.yml", "nav:\n  - Guide:\n      - guide/index.md\n")
        val recognised = runReadActionBlocking {
            PsiTreeUtil.findChildrenOfType(file, YAMLScalar::class.java)
                .filter { MkDocsPathKind.of(it) != null }
                .map { it.textValue }
        }

        assertEquals("only the page of the section is a path", listOf("guide/index.md"), recognised)
    }

    /**
     * Use case: an ordinary text value of the configuration file, such as the site name. It happens to look
     * like anything else in the file and must stay text.
     */
    fun `test ignores a scalar below an unrelated key`() {
        assertNull(kindOf("site_name: Handbook\n", "Handbook"))
    }

    /**
     * Use case: a `docs_dir` written inside the options of a plugin. MkDocs reads its own keys at the top
     * level only, so a nested one is an option that happens to share the name.
     */
    fun `test ignores a nested docs dir`() {
        assertNull(kindOf("plugins:\n  - search:\n      docs_dir: manual\n", "manual"))
    }

    /**
     * Use case: any other YAML file of the project, a CI workflow for instance. Its keys are none of MkDocs'
     * business even when they are spelled alike.
     */
    fun `test ignores a file that is no mkdocs configuration`() {
        assertNull(kindOf("docs_dir: manual\n", "manual", name = "docker-compose.yml"))
    }

    /**
     * Use case: a key written without a value, or with nothing but spaces. There is no path to resolve, and
     * reporting the empty value as an unknown target would be noise.
     */
    fun `test ignores a blank value`() {
        assertNull(kindOf("docs_dir: \"\"\n", ""))
        assertNull(kindOf("docs_dir: \"   \"\n", "   "))
    }

    /**
     * Use case: resolving where a value is looked for. `docs_dir` is read next to the configuration file,
     * while a navigation entry is read below the documentation directory.
     */
    fun `test resolves the base directory per kind`() {
        myFixture.addFileToProject("base/mkdocs.yml", "site_name: Handbook\n")
        val page = myFixture.addFileToProject("base/docs/index.md", "").virtualFile
        val configFile = page.parent.parent.findChild("mkdocs.yml")!!

        runReadActionBlocking {
            assertEquals(
                configFile.parent,
                MkDocsPathKind.DOCS_DIR.baseDirectoryOf(project, configFile),
            )
            assertEquals(page.parent, MkDocsPathKind.NAV.baseDirectoryOf(project, configFile))
        }
    }

    /**
     * Use case: the override directory of the theme. It is the one key below `theme` that is *not* read
     * below `docs_dir`: the templates it holds are not content of the site, they are what renders it, and
     * MkDocs looks for them next to `mkdocs.yml`.
     */
    fun `test reads the override directory of the theme`() {
        val kind = kindOf("theme:\n  name: material\n  custom_dir: overrides\n", "overrides")

        assertEquals(MkDocsPathKind.CUSTOM_DIR, kind)
        assertTrue("custom_dir names a directory", kind!!.directory)
        assertFalse("a missing override directory breaks the build", kind.soft)
        assertFalse("custom_dir is resolved against the site root", kind.relativeToDocsDir)
    }

    /**
     * Use case: a script written in the plain form MkDocs has always accepted. It is loaded by the built site
     * from below `docs_dir`, exactly like a style sheet is.
     */
    fun `test reads a plain extra javascript entry`() {
        val kind = kindOf("extra_javascript:\n  - js/extra.js\n", "js/extra.js")

        assertEquals(MkDocsPathKind.EXTRA_JAVASCRIPT, kind)
        assertFalse("a script is a file", kind!!.directory)
        assertTrue("a script is resolved against docs_dir", kind.relativeToDocsDir)
    }

    /**
     * Use case: the mapping form MkDocs 1.6 added, where the script carries `type` and `defer` next to it.
     * The path sits behind the `path` key there, and it means exactly what the plain form means.
     */
    fun `test reads the mapping form of an extra javascript entry`() {
        val kind = kindOf(
            "extra_javascript:\n  - path: js/extra.js\n    defer: true\n",
            "js/extra.js",
        )

        assertEquals(MkDocsPathKind.EXTRA_JAVASCRIPT, kind)
    }

    /**
     * Use case: a `path` key somewhere else in the file — a plugin option, for instance. Only an entry of the
     * top level `extra_javascript` sequence names a script; everywhere else the same word is someone's
     * setting that happens to share the name.
     */
    fun `test ignores a path key outside extra javascript`() {
        assertNull(kindOf("plugins:\n  - search:\n      path: js/extra.js\n", "js/extra.js"))
    }

    /**
     * Returns the kind of the first scalar of [text] whose value is [value].
     *
     * @param text the content of the configuration file
     * @param value the value of the scalar to inspect
     * @param name the name the file is written under
     */
    private fun kindOf(text: String, value: String, name: String = "mkdocs.yml"): MkDocsPathKind? {
        val file = myFixture.configureByText(name, text)
        return runReadActionBlocking {
            val scalar = PsiTreeUtil.findChildrenOfType(file, YAMLScalar::class.java)
                .first { it.textValue == value }
            MkDocsPathKind.of(scalar)
        }
    }
}
