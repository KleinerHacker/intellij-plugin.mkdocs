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

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Paints one node of the navigation tree.
 *
 * A page carries the same icon the project view gives a Markdown file of a site, so the two views agree on
 * what a page looks like. The path or the address of an entry follows the label in grey — two pages can well
 * carry the same heading, and then only the path tells them apart.
 */
class MkDocsNavTreeRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? MkDocsNavTreeNode ?: return

        icon = when (node.kind) {
            MkDocsNavTreeNodeKind.SECTION -> MkDocsIcons.NavSection
            MkDocsNavTreeNodeKind.PAGE -> MkDocsIcons.MarkdownFile
            MkDocsNavTreeNodeKind.LINK -> AllIcons.General.Web
        }

        // A page whose target is not there is greyed rather than dropped: what is missing is the point.
        val attributes = if (node.unresolved) {
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        } else {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(node.label, attributes)
        if (node.hint.isNotEmpty()) {
            append("  ${node.hint}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        toolTipText = if (node.unresolved) {
            // The tooltip names the full path: a bare file name would not say where the page is missing from.
            MkDocsBundle.message("toolwindow.sitePage.node.missing", node.path)
        } else {
            null
        }
    }
}
