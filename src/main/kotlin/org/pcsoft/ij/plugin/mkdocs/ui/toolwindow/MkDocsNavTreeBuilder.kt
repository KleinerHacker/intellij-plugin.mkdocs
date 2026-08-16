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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsPageTitleService
import org.pcsoft.ij.plugin.mkdocs.types.*
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Turns the navigation of a site into the tree the tool window shows.
 *
 * Everything expensive happens here and not in the renderer: resolving a path against the documentation
 * directory touches the virtual file system, and reading the heading of a page reads the file. Both are done
 * once, on a background thread, and the finished nodes are handed to the event dispatch thread.
 *
 * The label of a page follows the order MkDocs itself uses: the title written in `nav` wins, then the first
 * heading of the page, then the file name without its extension.
 */
object MkDocsNavTreeBuilder {

    /**
     * Builds the — invisible — root of the navigation tree.
     *
     * @param project the project the site belongs to
     * @param docsDir the documentation directory of the site, or `null` if it does not exist
     * @param nodes the entries read out of `nav`
     * @return the root node, carrying one child per entry of [nodes]
     */
    fun build(project: Project, docsDir: VirtualFile?, nodes: List<MkDocsNavNode>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode()
        appendAll(project, docsDir, nodes, root)
        return root
    }

    /**
     * Appends every entry of [nodes] below [parent].
     *
     * @param project the project the site belongs to
     * @param docsDir the documentation directory of the site, or `null` if it does not exist
     * @param nodes the entries of one level of the navigation
     * @param parent the node the entries belong below
     */
    private fun appendAll(
        project: Project,
        docsDir: VirtualFile?,
        nodes: List<MkDocsNavNode>,
        parent: DefaultMutableTreeNode,
    ) {
        for (node in nodes) {
            parent.add(treeNodeFor(project, docsDir, node))
        }
    }

    /**
     * Turns a single entry of the navigation into a tree node.
     *
     * @param project the project the site belongs to
     * @param docsDir the documentation directory of the site, or `null` if it does not exist
     * @param node the entry to convert
     */
    private fun treeNodeFor(
        project: Project,
        docsDir: VirtualFile?,
        node: MkDocsNavNode,
    ): DefaultMutableTreeNode = when (node) {
        is MkDocsNavSection -> {
            val label = node.title ?: MkDocsBundle.message("toolwindow.sitePage.node.section")
            val treeNode = DefaultMutableTreeNode(
                MkDocsNavTreeNode(MkDocsNavTreeNodeKind.SECTION, label, "", "", null, null),
            )
            appendAll(project, docsDir, node.children, treeNode)
            treeNode
        }

        is MkDocsNavLink -> DefaultMutableTreeNode(
            MkDocsNavTreeNode(
                MkDocsNavTreeNodeKind.LINK,
                node.title ?: node.url,
                node.url,
                node.url,
                null,
                node.url,
            ),
        )

        is MkDocsNavPage -> {
            val file = docsDir?.let { MkDocsNav.resolve(it, node) }
            val label = node.title
                ?: file?.let { MkDocsPageTitleService.getInstance(project).titleOf(it) }
                ?: MkDocsPageTitle.fallback(node.path)
            DefaultMutableTreeNode(
                MkDocsNavTreeNode(MkDocsNavTreeNodeKind.PAGE, label, hintOf(node.path), node.path, file, null),
            )
        }
    }

    /**
     * Builds the greyed text behind the label of a page.
     *
     * A navigation is read page by page, and the file name is what identifies a page at a glance. The full
     * path only earns its place once the page sits in a subdirectory, where the file name alone can well
     * appear more than once.
     *
     * @param path the path of the page as `nav` writes it, relative to the documentation directory
     * @return the file name, followed by the full path in brackets for a page inside a subdirectory
     */
    private fun hintOf(path: String): String {
        val fileName = path.substringAfterLast(PATH_SEPARATOR)
        return if (fileName.equals(path, ignoreCase = true)) fileName else "$fileName ($path)"
    }

    /** Separator MkDocs writes paths of `nav` with, on every operating system. */
    private const val PATH_SEPARATOR = '/'
}
