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

package org.pcsoft.ij.plugin.mkdocs.material.data

/**
 * The knowledge the plugin has about the *Material for MkDocs* theme, as plain data.
 *
 * Architecture note: `.claude/rules/architecture.md` lists `types`, `services`, `settings`, `build`,
 * `inspection` and `schema` as the specialised packages below the root package. `material` is an approved
 * deviation — a top level sub package of its own for everything belonging to the Material theme. Its `data`
 * sub package deliberately carries **no** IntelliJ Platform dependency, so the same constants can feed the
 * generated JSON schema, the settings pages, the annotators, QuickDoc and the completion, and can be tested
 * with plain unit tests.
 *
 * The section a [MkDocsMaterialFeatureFlag] belongs to. Material groups its `theme.features` flags by the
 * part of the page they change, and the settings page shows them under exactly these headings.
 */
enum class MkDocsMaterialFeatureGroup(
    /** The bundle key of the heading shown for this group. */
    val titleKey: String
) {

    /** Everything below the `navigation.` prefix: sidebar, tabs, instant loading. */
    NAVIGATION("material.featureGroup.navigation"),

    /** Everything below the `toc.` prefix: the table of contents of a page. */
    TOC("material.featureGroup.toc"),

    /** Everything below the `search.` prefix: the built in client side search. */
    SEARCH("material.featureGroup.search"),

    /** Everything below the `header.` prefix: the bar at the top of the page. */
    HEADER("material.featureGroup.header"),

    /** Everything below the `content.` prefix: the rendered page body itself. */
    CONTENT("material.featureGroup.content"),

    /** Everything below the `announce.` prefix: the announcement bar above the header. */
    ANNOUNCE("material.featureGroup.announce")
}
