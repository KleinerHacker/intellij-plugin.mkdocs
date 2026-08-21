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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Needs the platform because `nav` is read through the bundled YAML plugin's PSI.
 */
class MkDocsNavTest : BasePlatformTestCase() {

    /**
     * Use case: the shortest `nav` entry there is — a bare path. It carries no title, so the tree has to
     * derive the label from the page itself later on.
     */
    fun `test reads a bare path as a page without a title`() {
        val nodes = readNav("nav:\n  - index.md\n")

        assertEquals(listOf(MkDocsNavPage(null, "index.md")), nodes)
    }

    /**
     * Use case: the common `nav` entry — a title in front of the path. The title is what MkDocs renders, so
     * it is what the tree node shows.
     */
    fun `test reads a titled entry as a page`() {
        val nodes = readNav("nav:\n  - Home: index.md\n")

        assertEquals(listOf(MkDocsNavPage("Home", "index.md")), nodes)
    }

    /**
     * Use case: a title carrying a space or a colon has to be quoted in YAML. The quoting is syntax and must
     * not end up in the label of the tree node.
     */
    fun `test strips quotes from the title`() {
        val nodes = readNav("nav:\n  - 'User Guide': guide.md\n  - \"Release notes\": notes.md\n")

        assertEquals(
            listOf(MkDocsNavPage("User Guide", "guide.md"), MkDocsNavPage("Release notes", "notes.md")),
            nodes,
        )
    }

    /**
     * Use case: a site grouping its pages under headings. A section holds children and no page of its own.
     */
    fun `test reads a section with its children`() {
        val text = """
            nav:
              - Home: index.md
              - Guide:
                  - guide/install.md
                  - Writing: guide/writing.md
        """.trimIndent()

        val nodes = readNav(text)

        assertEquals(
            listOf(
                MkDocsNavPage("Home", "index.md"),
                MkDocsNavSection(
                    "Guide",
                    listOf(
                        MkDocsNavPage(null, "guide/install.md"),
                        MkDocsNavPage("Writing", "guide/writing.md"),
                    ),
                ),
            ),
            nodes,
        )
    }

    /**
     * Use case: a larger site nesting sections inside sections. Every level has to survive into the tree.
     */
    fun `test reads nested sections`() {
        val text = """
            nav:
              - Outer:
                  - Inner:
                      - deep/page.md
        """.trimIndent()

        val nodes = readNav(text)

        assertEquals(
            listOf(
                MkDocsNavSection(
                    "Outer",
                    listOf(MkDocsNavSection("Inner", listOf(MkDocsNavPage(null, "deep/page.md")))),
                ),
            ),
            nodes,
        )
    }

    /**
     * Use case: a section announced but not filled yet. Dropping it would hide work in progress, so the node
     * stays and simply has no children.
     */
    fun `test reads an empty section`() {
        val nodes = readNav("nav:\n  - Coming soon:\n")

        assertEquals(listOf(MkDocsNavSection("Coming soon", emptyList())), nodes)
    }

    /**
     * Use case: a site linking to something outside itself — the project homepage, an issue tracker, a
     * mail address. Such an entry is no page and must not be resolved against the documentation directory.
     */
    fun `test reads external targets as links`() {
        val text = """
            nav:
              - Homepage: https://example.org
              - Contact: mailto:team@example.org
              - Mirror: //cdn.example.org/docs
        """.trimIndent()

        val nodes = readNav(text)

        assertEquals(
            listOf(
                MkDocsNavLink("Homepage", "https://example.org"),
                MkDocsNavLink("Contact", "mailto:team@example.org"),
                MkDocsNavLink("Mirror", "//cdn.example.org/docs"),
            ),
            nodes,
        )
    }

    /**
     * Use case: a site pointing at generated documentation shipped inside its documentation directory. The
     * target is no Markdown file, but it is still a page of the site rather than a link leaving it.
     */
    fun `test reads a non markdown target as a page`() {
        val nodes = readNav("nav:\n  - API Docs: dokka/html/index.html\n")

        assertEquals(listOf(MkDocsNavPage("API Docs", "dokka/html/index.html")), nodes)
    }

    /**
     * Use case: a path written with a leading `./` or with backslashes, which happens on Windows. Both mean
     * the same file, so both have to be normalised to the same path.
     */
    fun `test normalises the path of an entry`() {
        val nodes = readNav("nav:\n  - One: ./guide/install.md\n  - Two: guide\\writing.md\n")

        assertEquals(
            listOf(MkDocsNavPage("One", "guide/install.md"), MkDocsNavPage("Two", "guide/writing.md")),
            nodes,
        )
    }

    /**
     * Use case: a flow mapping carrying several entries at once, which YAML allows and MkDocs accepts. Each
     * key stands for an entry of its own, in the order they are written in.
     */
    fun `test reads several entries out of one mapping`() {
        val nodes = readNav("nav:\n  - {Home: index.md, About: about.md}\n")

        assertEquals(
            listOf(MkDocsNavPage("Home", "index.md"), MkDocsNavPage("About", "about.md")),
            nodes,
        )
    }

    /**
     * Use case: a site without `nav`, which is the MkDocs default. The tool window has to tell that apart
     * from an empty navigation, so the answer is `null` rather than an empty list.
     */
    fun `test reports a missing nav as null`() {
        assertNull(readNav("site_name: Handbook\n"))
    }

    /**
     * Use case: `nav` is written but left empty. That is a navigation, only one without entries — the tool
     * window reports it differently from a missing one.
     */
    fun `test reports an empty nav as an empty list`() {
        assertEquals(emptyList<MkDocsNavNode>(), readNav("site_name: Handbook\nnav:\n"))
    }

    /**
     * Use case: `nav` written as an empty flow sequence, which is the explicit way of saying the same thing.
     */
    fun `test reports an empty sequence as an empty list`() {
        assertEquals(emptyList<MkDocsNavNode>(), readNav("nav: []\n"))
    }

    /**
     * Use case: the user is in the middle of typing and `nav` carries something MkDocs would not accept. The
     * tree must not show an invented structure, so nothing is read at all.
     */
    fun `test refuses a nav that is no sequence`() {
        assertNull(readNav("nav: index.md\n"))
    }

    /**
     * Use case: `nav` written as a mapping instead of a list, a common slip. MkDocs rejects it, and so does
     * the tree.
     */
    fun `test refuses a nav written as a mapping`() {
        assertNull(readNav("nav:\n  home: index.md\n"))
    }

    /**
     * Use case: a single entry the parser cannot make sense of — here a mapping where a path or a list
     * belongs. It is skipped, and the entries around it still reach the tree.
     */
    fun `test skips an entry it cannot make sense of`() {
        val text = """
            nav:
              - Home: index.md
              -
              - Broken:
                  key: value
              - About: about.md
        """.trimIndent()

        val nodes = readNav(text)

        assertEquals(
            listOf(MkDocsNavPage("Home", "index.md"), MkDocsNavPage("About", "about.md")),
            nodes,
        )
    }

    /**
     * Use case: a pathologically nested configuration file. The tree stops at the depth limit rather than
     * following the structure without end.
     */
    fun `test stops at the maximum depth`() {
        val nodes = readNav(deeplyNestedNav(MkDocsNav.MAX_DEPTH + 3))

        assertNotNull(nodes)
        assertEquals(MkDocsNav.MAX_DEPTH, sectionDepthOf(nodes!!))
    }

    /**
     * Use case: an entry pointing at a page that exists. The tree node has to open that file, so the path is
     * resolved against the documentation directory of the site.
     */
    fun `test resolves a path against the documentation directory`() {
        val docsDir = docsDirWith("guide/install.md")

        val resolved = MkDocsNav.resolve(docsDir, MkDocsNavPage(null, "guide/install.md"))

        assertNotNull(resolved)
        assertEquals("install.md", resolved!!.name)
    }

    /**
     * Use case: an entry pointing at a page that was renamed or never written. Resolution reports it rather
     * than throwing, so the tree can mark the entry as unresolved.
     */
    fun `test reports an unresolvable path`() {
        val docsDir = docsDirWith("index.md")

        assertNull(MkDocsNav.resolve(docsDir, MkDocsNavPage(null, "missing.md")))
        assertNull(MkDocsNav.resolve(docsDir, MkDocsNavPage(null, "")))
    }

    /**
     * Use case: a target reaching out of the documentation directory with `..`, which MkDocs tolerates. The
     * resolution has to follow it just as the file system would.
     */
    fun `test resolves a path leaving the documentation directory`() {
        val docsDir = docsDirWith("index.md")
        myFixture.addFileToProject("site/README.md", "# Readme\n")

        val resolved = MkDocsNav.resolve(docsDir, MkDocsNavPage(null, "../README.md"))

        assertNotNull(resolved)
        assertEquals("README.md", resolved!!.name)
    }

    /**
     * Use case: telling a target leaving the site apart from a page of the site. Everything carrying a scheme
     * or starting protocol relative leaves, everything else stays.
     */
    fun `test recognises external targets`() {
        assertTrue(MkDocsNav.isExternal("https://example.org"))
        assertTrue(MkDocsNav.isExternal("http://example.org"))
        assertTrue(MkDocsNav.isExternal("mailto:team@example.org"))
        assertTrue(MkDocsNav.isExternal("//cdn.example.org"))
        assertFalse(MkDocsNav.isExternal("index.md"))
        assertFalse(MkDocsNav.isExternal("guide/install.md"))
        assertFalse(MkDocsNav.isExternal("../README.md"))
    }

    /**
     * Reads the `nav` of a configuration file written from [text].
     *
     * Every test writes into a directory of its own so the configuration files of two tests cannot reach one
     * another.
     *
     * @param text the content of the configuration file
     */
    private fun readNav(text: String): List<MkDocsNavNode>? {
        val directory = getTestName(true).replace(NON_WORD, "")
        val file = myFixture.addFileToProject("$directory/mkdocs.yml", text).virtualFile
        return runReadActionBlocking { MkDocsNav.read(project, file) }
    }

    /**
     * Creates a documentation directory holding [path] and returns the directory.
     *
     * @param path the page to create, relative to the documentation directory
     */
    private fun docsDirWith(path: String): VirtualFile {
        myFixture.addFileToProject("$DOCS_DIR/$path", "# Page\n")
        return myFixture.findFileInTempDir(DOCS_DIR)
    }

    /**
     * Builds a `nav` nesting [levels] sections into one another.
     *
     * @param levels how many sections to nest
     */
    private fun deeplyNestedNav(levels: Int): String {
        val builder = StringBuilder("nav:\n")
        var indent = INDENT_STEP
        repeat(levels) { level ->
            builder.append(" ".repeat(indent)).append("- Level ").append(level).append(":\n")
            indent += NESTED_INDENT_STEP
        }
        builder.append(" ".repeat(indent)).append("- page.md\n")
        return builder.toString()
    }

    /**
     * Returns how many sections are nested into one another below [nodes].
     *
     * @param nodes the entries of one level of the navigation
     */
    private fun sectionDepthOf(nodes: List<MkDocsNavNode>): Int {
        val section = nodes.filterIsInstance<MkDocsNavSection>().firstOrNull() ?: return 0
        return 1 + sectionDepthOf(section.children)
    }

    private companion object {

        /** Everything a directory name should not carry, so a test name can become one. */
        val NON_WORD = Regex("""[^A-Za-z0-9]""")

        /** The documentation directory the resolution tests work in. */
        const val DOCS_DIR = "site/docs"

        /** Indentation of the entries directly below `nav`. */
        const val INDENT_STEP = 2

        /** Additional indentation each nested section needs. */
        const val NESTED_INDENT_STEP = 4
    }
}
