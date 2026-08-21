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

/** What an entry of the navigation stands for, which decides its icon and what opening it does. */
enum class MkDocsNavTreeNodeKind {

    /** A group of entries, without a page of its own. */
    SECTION,

    /** A page of the site. */
    PAGE,

    /** A target outside the site. */
    LINK,
}

/**
 * One node of the navigation tree, with everything the renderer and the navigation need.
 *
 * Computed on a background thread and handed to the event dispatch thread as a whole, so the renderer never
 * has to read a file or resolve a path while it paints.
 *
 * @property kind what the entry stands for
 * @property label the text of the node — the title from `nav`, the first heading of the page, or the file name
 * @property hint the greyed text behind the label: the file name of a page — followed by its full path in
 *                brackets if the page sits in a subdirectory — or the address of a link, empty for a section
 * @property path the full path of the entry as `nav` writes it: the path of a page relative to the
 *                documentation directory, the address of a link, and empty for a section
 * @property file the page the node opens, or `null` for a section, a link, or a path leading nowhere
 * @property url the address the node opens, or `null` for anything but a link
 */
data class MkDocsNavTreeNode(
    val kind: MkDocsNavTreeNodeKind,
    val label: String,
    val hint: String,
    val path: String,
    val file: VirtualFile?,
    val url: String?,
) {

    /** `true` for a page whose target could not be found below the documentation directory. */
    val unresolved: Boolean
        get() = kind == MkDocsNavTreeNodeKind.PAGE && file == null
}
