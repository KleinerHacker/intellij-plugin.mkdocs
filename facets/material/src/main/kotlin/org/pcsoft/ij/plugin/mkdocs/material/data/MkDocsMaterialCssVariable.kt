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
 * The part of the page a [MkDocsMaterialCssVariable] paints.
 */
enum class MkDocsMaterialCssVariableGroup(
    /** The bundle key of the heading shown for this group. */
    val titleKey: String
) {

    /** The primary colour of the site: header, sidebar highlights, active entries. */
    PRIMARY("material.cssGroup.primary"),

    /** The accent colour of the site: hovered links, focused controls. */
    ACCENT("material.cssGroup.accent"),

    /** The foreground and background of the page itself. */
    DEFAULT("material.cssGroup.default"),

    /** Code blocks and their syntax highlighting. */
    CODE("material.cssGroup.code"),

    /** The typeset content: links, marked text, key caps, tables. */
    TYPESET("material.cssGroup.typeset"),

    /** Call-out blocks. */
    ADMONITION("material.cssGroup.admonition"),

    /** The footer at the bottom of the page. */
    FOOTER("material.cssGroup.footer"),

    /** The elevation shadows. */
    SHADOW("material.cssGroup.shadow"),

    /** The font families the theme loads. */
    FONT("material.cssGroup.font")
}

/**
 * One `--md-*` CSS custom property of the *Material for MkDocs* theme.
 *
 * Overriding these in a style sheet listed under `extra_css` is the supported way of theming a site beyond
 * the palette, so the plugin offers them in completion and documents them in QuickDoc.
 *
 * Read from `material/spec/css-variables.yaml` and handed out by [MkDocsMaterialDataService].
 *
 * @property name the property including the two leading hyphens, for example `--md-primary-fg-color`
 * @property group the part of the page the property paints
 * @property isColor `true` if the value is a colour, which makes the IDE offer a colour swatch for it
 * @property descriptionKey the bundle key of the one line description
 */
data class MkDocsMaterialCssVariable(
    val name: String,
    val group: MkDocsMaterialCssVariableGroup,
    val isColor: Boolean,
    val descriptionKey: String
)
