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
 * A Markdown extension the *Material for MkDocs* theme builds upon, as listed under `markdown_extensions`.
 *
 * The theme renders a plain site without any of them, so **no extension is required unconditionally**. An
 * extension only becomes mandatory once something in the configuration actually asks for it — that is what
 * [requiredBy] answers. Everything else is a recommendation the site is free to ignore, which is why the
 * annotator may only report the former as an error.
 */
enum class MkDocsMarkdownExtension(
    /** The identifier as it appears under `markdown_extensions`, for example `pymdownx.superfences`. */
    val id: String,
    /** The PyPI package providing the extension, or `null` if Python Markdown itself ships it. */
    val pipPackage: String?,
    /** Whether the extension can be forced by a feature at all, or is merely recommended. */
    val level: Level,
    /** The bundle key of the one line description shown in QuickDoc and in the settings page. */
    val descriptionKey: String,
    /** The address of the documentation of the extension, offered as a link in QuickDoc. */
    val docUrl: String,
    /** The options the quick fix writes below the extension when it adds it. */
    val defaultOptions: List<Pair<String, String>> = emptyList()
) {

    //region Python Markdown
    /** `admonition` — call-out blocks such as note, warning and tip. */
    ADMONITION(
        "admonition", null, Level.RECOMMENDED, "material.extension.admonition",
        "https://python-markdown.github.io/extensions/admonition/"
    ),

    /** `attr_list` — HTML attributes and CSS classes attached to Markdown elements. */
    ATTR_LIST(
        "attr_list", null, Level.REQUIRED_BY_FEATURE, "material.extension.attr_list",
        "https://python-markdown.github.io/extensions/attr_list/"
    ),

    /** `md_in_html` — Markdown inside HTML blocks marked with `markdown="1"`. */
    MD_IN_HTML(
        "md_in_html", null, Level.REQUIRED_BY_FEATURE, "material.extension.md_in_html",
        "https://python-markdown.github.io/extensions/md_in_html/"
    ),

    /** `def_list` — definition lists, and the base of task lists. */
    DEF_LIST(
        "def_list", null, Level.RECOMMENDED, "material.extension.def_list",
        "https://python-markdown.github.io/extensions/definition_lists/"
    ),

    /** `footnotes` — footnote references and their definitions. */
    FOOTNOTES(
        "footnotes", null, Level.REQUIRED_BY_FEATURE, "material.extension.footnotes",
        "https://python-markdown.github.io/extensions/footnotes/"
    ),

    /** `tables` — pipe tables. */
    TABLES(
        "tables", null, Level.RECOMMENDED, "material.extension.tables",
        "https://python-markdown.github.io/extensions/tables/"
    ),

    /** `toc` — the table of contents and the anchor links next to the headings. */
    TOC(
        "toc", null, Level.RECOMMENDED, "material.extension.toc",
        "https://python-markdown.github.io/extensions/toc/",
        defaultOptions = listOf("permalink" to "true")
    ),
    //endregion

    //region PyMdown Extensions
    /** `pymdownx.superfences` — nested fenced blocks, custom fences and diagram support. */
    PYMDOWNX_SUPERFENCES(
        "pymdownx.superfences", PIP_PYMDOWN, Level.REQUIRED_BY_FEATURE, "material.extension.pymdownx.superfences",
        "https://facelessuser.github.io/pymdown-extensions/extensions/superfences/"
    ),

    /** `pymdownx.highlight` — syntax highlighting of code blocks through Pygments. */
    PYMDOWNX_HIGHLIGHT(
        "pymdownx.highlight", PIP_PYMDOWN, Level.REQUIRED_BY_FEATURE, "material.extension.pymdownx.highlight",
        "https://facelessuser.github.io/pymdown-extensions/extensions/highlight/",
        defaultOptions = listOf(
            "anchor_linenums" to "true",
            "line_spans" to "__span",
            "pygments_lang_class" to "true"
        )
    ),

    /** `pymdownx.inlinehilite` — syntax highlighting of inline code spans. */
    PYMDOWNX_INLINEHILITE(
        "pymdownx.inlinehilite", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.inlinehilite",
        "https://facelessuser.github.io/pymdown-extensions/extensions/inlinehilite/"
    ),

    /** `pymdownx.snippets` — content of other files embedded into a page. */
    PYMDOWNX_SNIPPETS(
        "pymdownx.snippets", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.snippets",
        "https://facelessuser.github.io/pymdown-extensions/extensions/snippets/"
    ),

    /** `pymdownx.tabbed` — content tabs. */
    PYMDOWNX_TABBED(
        "pymdownx.tabbed", PIP_PYMDOWN, Level.REQUIRED_BY_FEATURE, "material.extension.pymdownx.tabbed",
        "https://facelessuser.github.io/pymdown-extensions/extensions/tabbed/",
        defaultOptions = listOf("alternate_style" to "true")
    ),

    /** `pymdownx.emoji` — emoji shorthands, and the icon shorthands of the theme. */
    PYMDOWNX_EMOJI(
        "pymdownx.emoji", PIP_PYMDOWN, Level.REQUIRED_BY_FEATURE, "material.extension.pymdownx.emoji",
        "https://facelessuser.github.io/pymdown-extensions/extensions/emoji/",
        defaultOptions = listOf(
            "emoji_index" to "!!python/name:material.extensions.emoji.twemoji",
            "emoji_generator" to "!!python/name:material.extensions.emoji.to_svg"
        )
    ),

    /** `pymdownx.details` — collapsible call-outs. */
    PYMDOWNX_DETAILS(
        "pymdownx.details", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.details",
        "https://facelessuser.github.io/pymdown-extensions/extensions/details/"
    ),

    /** `pymdownx.critic` — the markup of Critic Markup for tracked changes. */
    PYMDOWNX_CRITIC(
        "pymdownx.critic", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.critic",
        "https://facelessuser.github.io/pymdown-extensions/extensions/critic/"
    ),

    /** `pymdownx.caret` — superscript and inserted text. */
    PYMDOWNX_CARET(
        "pymdownx.caret", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.caret",
        "https://facelessuser.github.io/pymdown-extensions/extensions/caret/"
    ),

    /** `pymdownx.keys` — keyboard keys rendered as key caps. */
    PYMDOWNX_KEYS(
        "pymdownx.keys", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.keys",
        "https://facelessuser.github.io/pymdown-extensions/extensions/keys/"
    ),

    /** `pymdownx.mark` — highlighted text. */
    PYMDOWNX_MARK(
        "pymdownx.mark", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.mark",
        "https://facelessuser.github.io/pymdown-extensions/extensions/mark/"
    ),

    /** `pymdownx.tilde` — subscript and deleted text. */
    PYMDOWNX_TILDE(
        "pymdownx.tilde", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.tilde",
        "https://facelessuser.github.io/pymdown-extensions/extensions/tilde/"
    ),

    /** `pymdownx.tasklist` — lists of check boxes. */
    PYMDOWNX_TASKLIST(
        "pymdownx.tasklist", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.tasklist",
        "https://facelessuser.github.io/pymdown-extensions/extensions/tasklist/",
        defaultOptions = listOf("custom_checkbox" to "true")
    ),

    /** `pymdownx.smartsymbols` — typographic replacements such as arrows and fractions. */
    PYMDOWNX_SMARTSYMBOLS(
        "pymdownx.smartsymbols", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.smartsymbols",
        "https://facelessuser.github.io/pymdown-extensions/extensions/smartsymbols/"
    ),

    /** `pymdownx.arithmatex` — mathematical expressions handed to MathJax or KaTeX. */
    PYMDOWNX_ARITHMATEX(
        "pymdownx.arithmatex", PIP_PYMDOWN, Level.RECOMMENDED, "material.extension.pymdownx.arithmatex",
        "https://facelessuser.github.io/pymdown-extensions/extensions/arithmatex/",
        defaultOptions = listOf("generic" to "true")
    );
    //endregion

    /**
     * How strongly an extension is tied to the configuration of the site.
     */
    enum class Level {

        /** Some feature of the theme can force this extension; without it that feature does not render. */
        REQUIRED_BY_FEATURE,

        /** Nothing forces this extension — it only widens what an author can write. */
        RECOMMENDED
    }

    companion object {

        /**
         * Resolves the extension written as [id].
         *
         * @param id the identifier as it appears under `markdown_extensions`
         * @return the extension, or `null` if it is none the theme cares about
         */
        fun byId(id: String): MkDocsMarkdownExtension? = entries.firstOrNull { it.id == id }

        /** Every extension that is merely a recommendation, in declaration order. */
        fun recommended(): List<MkDocsMarkdownExtension> = entries.filter { it.level == Level.RECOMMENDED }

        /**
         * The extensions the given configuration actually forces.
         *
         * Nothing is required by the theme as such: an extension only shows up here when a feature listed in
         * `theme.features` needs it, or when the site uses the icon shorthands the theme provides. An empty
         * configuration therefore yields an empty set, and the annotator reporting missing extensions as an
         * error stays quiet on a site that uses none of it.
         *
         * @param flags the identifiers currently listed in `theme.features`; unknown ones are ignored
         * @param usesIcons `true` if the site writes icon or emoji shorthands such as `:material-check:`
         * @return the extensions that have to be listed under `markdown_extensions`
         */
        fun requiredBy(flags: Set<String>, usesIcons: Boolean): Set<MkDocsMarkdownExtension> {
            val result = linkedSetOf<MkDocsMarkdownExtension>()
            flags.forEach { flagId ->
                MkDocsMaterialFeatureFlag.byId(flagId)?.requiredExtensions?.forEach { extensionId ->
                    byId(extensionId)?.let(result::add)
                }
            }
            if (usesIcons) result += PYMDOWNX_EMOJI
            return result
        }
    }
}

/** The PyPI package every `pymdownx.*` extension comes from. */
private const val PIP_PYMDOWN: String = "pymdown-extensions"
