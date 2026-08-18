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
 * How strongly an extension is tied to the configuration of the site.
 */
enum class MkDocsMarkdownExtensionLevel {

    /** Some feature of the theme can force this extension; without it that feature does not render. */
    REQUIRED_BY_FEATURE,

    /** Nothing forces this extension — it only widens what an author can write. */
    RECOMMENDED
}

/**
 * The kind of value an option of a Markdown extension takes.
 */
enum class MkDocsMarkdownExtensionOptionKind {

    /** `true` or `false`. */
    BOOLEAN,

    /** Free text. */
    STRING,

    /** A whole number. */
    INTEGER,

    /** One of the values listed in [MkDocsMarkdownExtensionOption.values]. */
    ENUM,

    /** A reference to a Python callable, written as a `!!python/name:` tag. */
    PYTHON_REFERENCE
}

/**
 * One option a Markdown extension accepts below its own entry, such as `permalink` below `- toc:`.
 *
 * The list of options is not written in code: it is read from `material/spec/markdown-extensions.yaml` by
 * [MkDocsMaterialDataService], the same way the extensions themselves are.
 *
 * @property key the name of the option, as written below the entry of the extension
 * @property kind the kind of value the option takes
 * @property descriptionKey the bundle key of the one line description shown in completion and in QuickDoc
 * @property values the values the option accepts, empty unless [kind] is [MkDocsMarkdownExtensionOptionKind.ENUM]
 * @property defaultValue the value the extension falls back to, or `null` if it has none worth showing
 * @property recommendedValue the value the quick fix writes for this option, or `null` if it leaves it out
 */
data class MkDocsMarkdownExtensionOption(
    val key: String,
    val kind: MkDocsMarkdownExtensionOptionKind,
    val descriptionKey: String,
    val values: List<String> = emptyList(),
    val defaultValue: String? = null,
    val recommendedValue: String? = null
)

/**
 * A Markdown extension the *Material for MkDocs* theme builds upon, as listed under `markdown_extensions`.
 *
 * The theme renders a plain site without any of them, so **no extension is required unconditionally**. An
 * extension only becomes mandatory once something in the configuration actually asks for it — that is what
 * [MkDocsMarkdownExtensions.requiredBy] answers. Everything else is a recommendation the site is free to
 * ignore, which is why the annotator may only report the former as an error.
 *
 * The list of extensions is not written in code: it is read from `material/spec/markdown-extensions.yaml`
 * by [MkDocsMaterialDataService].
 *
 * @property id the identifier as it appears under `markdown_extensions`, for example `pymdownx.superfences`
 * @property pipPackage the PyPI package providing the extension, or `null` if Python Markdown itself ships it
 * @property level whether the extension can be forced by a feature at all, or is merely recommended
 * @property descriptionKey the bundle key of the one line description shown in QuickDoc and in the settings page
 * @property docUrl the address of the documentation of the extension, offered as a link in QuickDoc
 * @property options the options the extension accepts below its entry, in the order of the resource
 * @property iconShorthand `true` for the extension the icon and emoji shorthands of the theme need
 */
data class MkDocsMarkdownExtension(
    val id: String,
    val pipPackage: String?,
    val level: MkDocsMarkdownExtensionLevel,
    val descriptionKey: String,
    val docUrl: String,
    val options: List<MkDocsMarkdownExtensionOption> = emptyList(),
    val iconShorthand: Boolean = false
) {

    /**
     * The options the quick fix writes below the extension when it adds it, in the order it writes them.
     *
     * Derived from [options] rather than kept next to them: an option and the value recommended for it are one
     * fact, and describing it twice is what lets the two drift apart.
     */
    val recommendedOptions: List<Pair<String, String>>
        get() = options.mapNotNull { option -> option.recommendedValue?.let { option.key to it } }

    /**
     * Returns the option named [key], or `null` if the extension does not know it.
     *
     * @param key the name of the option, as written below the entry of the extension
     */
    fun optionByKey(key: String): MkDocsMarkdownExtensionOption? = options.firstOrNull { it.key == key }
}
