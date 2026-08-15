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
 * A Markdown extension the *Material for MkDocs* theme builds upon, as listed under `markdown_extensions`.
 *
 * The theme renders a plain site without any of them, so **no extension is required unconditionally**. An
 * extension only becomes mandatory once something in the configuration actually asks for it — that is what
 * [MkDocsMarkdownExtensions.requiredBy] answers. Everything else is a recommendation the site is free to
 * ignore, which is why the annotator may only report the former as an error.
 *
 * The list of extensions is not written in code: it is read from `facets/material/markdown-extensions.yaml`
 * by [MkDocsMaterialDataService].
 *
 * @property id the identifier as it appears under `markdown_extensions`, for example `pymdownx.superfences`
 * @property pipPackage the PyPI package providing the extension, or `null` if Python Markdown itself ships it
 * @property level whether the extension can be forced by a feature at all, or is merely recommended
 * @property descriptionKey the bundle key of the one line description shown in QuickDoc and in the settings page
 * @property docUrl the address of the documentation of the extension, offered as a link in QuickDoc
 * @property defaultOptions the options the quick fix writes below the extension when it adds it
 * @property iconShorthand `true` for the extension the icon and emoji shorthands of the theme need
 */
data class MkDocsMarkdownExtension(
    val id: String,
    val pipPackage: String?,
    val level: MkDocsMarkdownExtensionLevel,
    val descriptionKey: String,
    val docUrl: String,
    val defaultOptions: List<Pair<String, String>> = emptyList(),
    val iconShorthand: Boolean = false
)
