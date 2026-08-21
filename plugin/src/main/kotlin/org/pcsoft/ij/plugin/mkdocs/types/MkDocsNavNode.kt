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

/**
 * One entry of the `nav` section of an MkDocs configuration file.
 *
 * MkDocs knows three kinds of entry, and the tree of the tool window shows all three. The type carries no
 * `VirtualFile` and no `Project`: resolving a path against the documentation directory is a separate step,
 * which keeps the shape of the navigation readable — and testable — on its own.
 *
 * @property title the title written in `nav`, or `null` if the entry carries none
 */
sealed interface MkDocsNavNode {

    val title: String?
}

/**
 * A page of the site.
 *
 * @property title the title written in `nav`, or `null` if the entry is a bare path
 * @property path the target as written, relative to the documentation directory
 */
data class MkDocsNavPage(
    override val title: String?,
    val path: String,
) : MkDocsNavNode

/**
 * A group of entries, shown as a folder in the navigation.
 *
 * A section has no page of its own — MkDocs renders it as a heading above its children.
 *
 * @property title the title written in `nav`, or `null` if the entry carries none
 * @property children the entries below the section, in the order they are written in
 */
data class MkDocsNavSection(
    override val title: String?,
    val children: List<MkDocsNavNode>,
) : MkDocsNavNode

/**
 * A link leaving the site.
 *
 * @property title the title written in `nav`, or `null` if the entry carries none
 * @property url the address as written
 */
data class MkDocsNavLink(
    override val title: String?,
    val url: String,
) : MkDocsNavNode
