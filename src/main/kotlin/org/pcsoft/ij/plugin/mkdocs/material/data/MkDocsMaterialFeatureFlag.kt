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
 * One entry of the `theme.features` sequence of the *Material for MkDocs* theme.
 *
 * The identifier written into `mkdocs.yml` is [id]; the enum constant name only exists so the flag can be
 * referred to from code. Everything a feature needs to be offered, documented and validated hangs off the
 * constant: the section it belongs to, its description, whether it needs an Insiders build, which other flags
 * it depends on or clashes with, and which Markdown extensions it forces to be enabled.
 *
 * @see MkDocsMarkdownExtension.requiredBy
 */
enum class MkDocsMaterialFeatureFlag(
    /** The identifier as it appears in `theme.features`, for example `navigation.tabs`. */
    val id: String,
    /** The section of the page this flag changes. */
    val group: MkDocsMaterialFeatureGroup,
    /** The bundle key of the one line description shown in QuickDoc, completion and the settings page. */
    val descriptionKey: String,
    /** `true` if the flag only has an effect in an *Insiders* build of the theme. */
    val insiders: Boolean = false,
    /** The [id]s of the flags that have to be enabled as well for this flag to do anything. */
    val requires: Set<String> = emptySet(),
    /** The [id]s of the flags this one cannot be combined with. Kept symmetric by the companion. */
    val conflictsWith: Set<String> = emptySet(),
    /** The [MkDocsMarkdownExtension.id]s that have to be enabled for this flag to work. */
    val requiredExtensions: Set<String> = emptySet()
) {

    //region announce
    /** `announce.dismiss` — the announcement bar can be dismissed and stays dismissed. */
    ANNOUNCE_DISMISS("announce.dismiss", MkDocsMaterialFeatureGroup.ANNOUNCE, "material.feature.announce.dismiss"),
    //endregion

    //region content
    /** `content.action.edit` — an edit button linking the page into the repository. */
    CONTENT_ACTION_EDIT("content.action.edit", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.action.edit"),

    /** `content.action.view` — a button showing the raw source of the page in the repository. */
    CONTENT_ACTION_VIEW("content.action.view", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.action.view"),

    /** `content.code.annotate` — numbered annotations attached to lines of a code block. */
    CONTENT_CODE_ANNOTATE(
        "content.code.annotate", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.code.annotate",
        requiredExtensions = setOf("pymdownx.superfences", "attr_list", "md_in_html")
    ),

    /** `content.code.copy` — a copy to clipboard button on every code block. */
    CONTENT_CODE_COPY(
        "content.code.copy", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.code.copy",
        requiredExtensions = setOf("pymdownx.highlight")
    ),

    /** `content.code.select` — line selection inside a code block. */
    CONTENT_CODE_SELECT(
        "content.code.select", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.code.select",
        requiredExtensions = setOf("pymdownx.highlight")
    ),

    /** `content.footnote.tooltips` — footnotes are shown as tooltips instead of jumping to the bottom. */
    CONTENT_FOOTNOTE_TOOLTIPS(
        "content.footnote.tooltips", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.footnote.tooltips",
        requiredExtensions = setOf("footnotes")
    ),

    /** `content.tabs.link` — all content tabs of a page switch together. */
    CONTENT_TABS_LINK(
        "content.tabs.link", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.tabs.link",
        requiredExtensions = setOf("pymdownx.tabbed", "pymdownx.superfences")
    ),

    /** `content.tooltips` — titles and abbreviations are rendered as styled tooltips. */
    CONTENT_TOOLTIPS(
        "content.tooltips", MkDocsMaterialFeatureGroup.CONTENT, "material.feature.content.tooltips",
        requiredExtensions = setOf("attr_list", "md_in_html")
    ),
    //endregion

    //region header
    /** `header.autohide` — the header slides away while the reader scrolls down. */
    HEADER_AUTOHIDE("header.autohide", MkDocsMaterialFeatureGroup.HEADER, "material.feature.header.autohide"),
    //endregion

    //region navigation
    /** `navigation.expand` — every navigation section starts out expanded. */
    NAVIGATION_EXPAND(
        "navigation.expand", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.expand",
        conflictsWith = setOf("navigation.prune")
    ),

    /** `navigation.footer` — previous and next links in the footer. */
    NAVIGATION_FOOTER("navigation.footer", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.footer"),

    /** `navigation.indexes` — a section may carry a page of its own instead of a plain label. */
    NAVIGATION_INDEXES(
        "navigation.indexes", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.indexes",
        conflictsWith = setOf("toc.integrate")
    ),

    /** `navigation.instant` — pages are exchanged by script instead of being loaded anew. */
    NAVIGATION_INSTANT("navigation.instant", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.instant"),

    /** `navigation.instant.prefetch` — a link target is fetched as soon as the pointer touches it. */
    NAVIGATION_INSTANT_PREFETCH(
        "navigation.instant.prefetch", MkDocsMaterialFeatureGroup.NAVIGATION,
        "material.feature.navigation.instant.prefetch",
        insiders = true, requires = setOf("navigation.instant")
    ),

    /** `navigation.instant.progress` — a progress bar for instant loading that takes longer. */
    NAVIGATION_INSTANT_PROGRESS(
        "navigation.instant.progress", MkDocsMaterialFeatureGroup.NAVIGATION,
        "material.feature.navigation.instant.progress",
        requires = setOf("navigation.instant")
    ),

    /** `navigation.path` — a breadcrumb trail above the page title. */
    NAVIGATION_PATH("navigation.path", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.path"),

    /** `navigation.prune` — only the navigation around the current page is rendered. */
    NAVIGATION_PRUNE("navigation.prune", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.prune"),

    /** `navigation.sections` — top level entries are rendered as groups instead of collapsible items. */
    NAVIGATION_SECTIONS("navigation.sections", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.sections"),

    /** `navigation.tabs` — the top level of the navigation becomes a tab bar below the header. */
    NAVIGATION_TABS("navigation.tabs", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.tabs"),

    /** `navigation.tabs.sticky` — the tab bar stays visible while scrolling. */
    NAVIGATION_TABS_STICKY(
        "navigation.tabs.sticky", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.tabs.sticky",
        insiders = true, requires = setOf("navigation.tabs")
    ),

    /** `navigation.top` — a back to top button appears after scrolling up. */
    NAVIGATION_TOP("navigation.top", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.top"),

    /** `navigation.tracking` — the address bar follows the section the reader is currently in. */
    NAVIGATION_TRACKING("navigation.tracking", MkDocsMaterialFeatureGroup.NAVIGATION, "material.feature.navigation.tracking"),
    //endregion

    //region search
    /** `search.highlight` — the search terms are highlighted on the opened page. */
    SEARCH_HIGHLIGHT("search.highlight", MkDocsMaterialFeatureGroup.SEARCH, "material.feature.search.highlight"),

    /** `search.share` — a button copying a deep link to the current search. */
    SEARCH_SHARE("search.share", MkDocsMaterialFeatureGroup.SEARCH, "material.feature.search.share"),

    /** `search.suggest` — the search field completes the word being typed. */
    SEARCH_SUGGEST("search.suggest", MkDocsMaterialFeatureGroup.SEARCH, "material.feature.search.suggest"),
    //endregion

    //region toc
    /** `toc.follow` — the table of contents scrolls along with the reading position. */
    TOC_FOLLOW("toc.follow", MkDocsMaterialFeatureGroup.TOC, "material.feature.toc.follow"),

    /** `toc.integrate` — the table of contents is folded into the navigation sidebar. */
    TOC_INTEGRATE(
        "toc.integrate", MkDocsMaterialFeatureGroup.TOC, "material.feature.toc.integrate",
        conflictsWith = setOf("toc.follow")
    );
    //endregion

    /**
     * The flags this one cannot be combined with, in both directions.
     *
     * [conflictsWith] is declared on one side of a pair only; this function reads the symmetric closure the
     * companion computes, so a caller never has to know which side declared the pair.
     */
    fun conflicts(): Set<MkDocsMaterialFeatureFlag> = CONFLICTS[this] ?: emptySet()

    companion object {

        /** The symmetric closure of every declared [conflictsWith] relation. */
        private val CONFLICTS: Map<MkDocsMaterialFeatureFlag, Set<MkDocsMaterialFeatureFlag>>

        private val BY_ID: Map<String, MkDocsMaterialFeatureFlag> = entries.associateBy { it.id }

        init {
            val collected = mutableMapOf<MkDocsMaterialFeatureFlag, MutableSet<MkDocsMaterialFeatureFlag>>()
            entries.forEach { flag ->
                flag.conflictsWith.forEach { otherId ->
                    val other = BY_ID[otherId] ?: return@forEach
                    collected.getOrPut(flag) { mutableSetOf() } += other
                    collected.getOrPut(other) { mutableSetOf() } += flag
                }
            }
            CONFLICTS = collected.mapValues { (_, value) -> value.toSet() }
        }

        /**
         * Resolves the flag written as [id] in `theme.features`.
         *
         * @param id the identifier as it appears in the configuration file
         * @return the flag, or `null` if the theme knows no such feature
         */
        fun byId(id: String): MkDocsMaterialFeatureFlag? = BY_ID[id]

        /** Every known identifier, in declaration order — the order the settings page and completion use. */
        fun allIds(): List<String> = entries.map { it.id }
    }
}
