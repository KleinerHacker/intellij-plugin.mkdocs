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

package org.pcsoft.ij.plugin.mkdocs.ui.toolwindow

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNavLink
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNavNode
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNavPage
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsNavSection
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Needs the platform for the virtual file system the entries of `nav` are resolved against.
 */
class MkDocsNavTreeBuilderTest : BasePlatformTestCase() {

    /**
     * Use case: the entry carries a title in `nav`. MkDocs renders that title, so the tree shows it as well —
     * even though the page announces a different one.
     */
    fun `test prefers the title written in nav`() {
        val docsDir = docsDirWith("index.md" to "# From the heading\n")

        val node = firstNodeOf(docsDir, MkDocsNavPage("From nav", "index.md"))

        assertEquals("From nav", node.label)
    }

    /**
     * Use case: the entry is a bare path. The label then comes from the first heading of the page, which is
     * what MkDocs would render.
     */
    fun `test falls back to the first heading of the page`() {
        val docsDir = docsDirWith("index.md" to "# From the heading\n")

        val node = firstNodeOf(docsDir, MkDocsNavPage(null, "index.md"))

        assertEquals("From the heading", node.label)
    }

    /**
     * Use case: the entry is a bare path and the page carries no heading either. The file name without its
     * extension is the last thing left to label the node with.
     */
    fun `test falls back to the file name`() {
        val docsDir = docsDirWith("install.md" to "Just a paragraph.\n")

        val node = firstNodeOf(docsDir, MkDocsNavPage(null, "install.md"))

        assertEquals("install", node.label)
    }

    /**
     * Use case: a page of a site carries the icon the project view gives it, so the two views agree on what a
     * page looks like. The path follows the label in grey, which is what tells two equally named pages apart.
     */
    fun `test carries the path of the page as its hint`() {
        val docsDir = docsDirWith("guide/install.md" to "# Install\n")

        val node = firstNodeOf(docsDir, MkDocsNavPage(null, "guide/install.md"))

        assertEquals(MkDocsNavTreeNodeKind.PAGE, node.kind)
        assertEquals("guide/install.md", node.hint)
        assertNotNull(node.file)
        assertFalse(node.unresolved)
    }

    /**
     * Use case: `nav` points at a page that was renamed or never written. The entry stays in the tree and is
     * marked as unresolved — hiding it would hide the mistake.
     */
    fun `test marks an entry whose page is missing`() {
        val docsDir = docsDirWith("index.md" to "# Home\n")

        val node = firstNodeOf(docsDir, MkDocsNavPage(null, "missing.md"))

        assertTrue(node.unresolved)
        assertNull(node.file)
        assertEquals("missing", node.label)
    }

    /**
     * Use case: the site has no documentation directory yet. Nothing can be resolved, but the entries of
     * `nav` still belong into the tree.
     */
    fun `test builds entries without a documentation directory`() {
        val node = firstNodeOf(null, MkDocsNavPage(null, "index.md"))

        assertTrue(node.unresolved)
        assertEquals("index", node.label)
    }

    /**
     * Use case: a site grouping its pages under headings. The section becomes a node of its own, carrying its
     * children in the order `nav` writes them.
     */
    fun `test builds a section with its children`() {
        val docsDir = docsDirWith("guide/install.md" to "# Install\n", "guide/writing.md" to "# Writing\n")

        val root = build(
            docsDir,
            MkDocsNavSection(
                "Guide",
                listOf(MkDocsNavPage(null, "guide/install.md"), MkDocsNavPage(null, "guide/writing.md")),
            ),
        )

        val section = nodeAt(root, 0)
        assertEquals(MkDocsNavTreeNodeKind.SECTION, section.kind)
        assertEquals("Guide", section.label)
        assertEquals("", section.hint)

        val children = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(2, children.childCount)
        assertEquals("Install", nodeAt(children, 0).label)
        assertEquals("Writing", nodeAt(children, 1).label)
    }

    /**
     * Use case: a site linking somewhere outside itself. The entry is no page, so nothing is resolved and the
     * address becomes the hint.
     */
    fun `test builds a link`() {
        val node = firstNodeOf(null, MkDocsNavLink("Homepage", "https://example.org"))

        assertEquals(MkDocsNavTreeNodeKind.LINK, node.kind)
        assertEquals("Homepage", node.label)
        assertEquals("https://example.org", node.hint)
        assertEquals("https://example.org", node.url)
        assertFalse(node.unresolved)
    }

    /**
     * Use case: `nav` is a written order, not a set. The tree has to keep it exactly.
     */
    fun `test keeps the order of nav`() {
        val docsDir = docsDirWith("a.md" to "# A\n", "b.md" to "# B\n", "c.md" to "# C\n")

        val root = build(
            docsDir,
            MkDocsNavPage(null, "c.md"),
            MkDocsNavPage(null, "a.md"),
            MkDocsNavPage(null, "b.md"),
        )

        assertEquals(listOf("C", "A", "B"), (0 until root.childCount).map { nodeAt(root, it).label })
    }

    /**
     * Builds the tree for [nodes] and returns its root.
     *
     * @param docsDir the documentation directory the entries are resolved against, or `null` if there is none
     * @param nodes the entries of `nav`
     */
    private fun build(docsDir: VirtualFile?, vararg nodes: MkDocsNavNode): DefaultMutableTreeNode =
        MkDocsNavTreeBuilder.build(project, docsDir, nodes.toList())

    /**
     * Builds the tree for a single entry and returns the node it produced.
     *
     * @param docsDir the documentation directory the entry is resolved against, or `null` if there is none
     * @param node the entry of `nav`
     */
    private fun firstNodeOf(docsDir: VirtualFile?, node: MkDocsNavNode): MkDocsNavTreeNode =
        nodeAt(build(docsDir, node), 0)

    /**
     * Returns the payload of the child of [parent] at [index].
     *
     * @param parent a node of the navigation tree
     * @param index position of the child
     */
    private fun nodeAt(parent: DefaultMutableTreeNode, index: Int): MkDocsNavTreeNode =
        (parent.getChildAt(index) as DefaultMutableTreeNode).userObject as MkDocsNavTreeNode

    /**
     * Creates a documentation directory holding [pages] and returns it.
     *
     * @param pages the pages to create, each a path relative to the documentation directory and its content
     */
    private fun docsDirWith(vararg pages: Pair<String, String>): VirtualFile {
        for ((path, text) in pages) {
            myFixture.tempDirFixture.createFile("$DOCS_DIR/$path", text)
        }
        return myFixture.findFileInTempDir(DOCS_DIR)
    }

    private companion object {

        /** The documentation directory the tests work in. */
        const val DOCS_DIR = "site/docs"
    }
}
