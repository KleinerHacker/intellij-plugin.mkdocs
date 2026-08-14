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

package org.pcsoft.ij.plugin.mkdocs.material.override

/**
 * A template of *Material for MkDocs* that a site can override.
 *
 * Overriding works by putting a file of the same path into the directory named by `theme.custom_dir`. What
 * goes into that file is almost always the same three lines — extend the original, open the block that is to
 * be changed, and put `{{ super() }}` back in — and getting them wrong produces a site that silently drops a
 * part of every page. So the scaffold is written for the author, and the content is theirs to fill in.
 *
 * Only the templates that are actually overridden in practice are offered. The full set of the theme runs to
 * dozens of files, most of which are internals rather than something a site would touch.
 *
 * @property path the path of the file below `custom_dir`, which is what makes it override the original
 * @property titleKey the bundle key of the label shown in the dialog
 * @property scaffold the content the file is created with
 */
enum class MkDocsMaterialOverride(
    val path: String,
    val titleKey: String,
    val scaffold: String,
) {

    /** `main.html` — the base template every page extends, and the place for site wide blocks. */
    MAIN("main.html", "material.override.main", MAIN_SCAFFOLD),

    /** `partials/header.html` — the bar at the top of every page. */
    HEADER("partials/header.html", "material.override.header", HEADER_SCAFFOLD),

    /** `partials/footer.html` — the bar at the bottom of every page, holding the navigation links. */
    FOOTER("partials/footer.html", "material.override.footer", FOOTER_SCAFFOLD),

    /** `partials/nav.html` — the navigation sidebar on the left. */
    NAV("partials/nav.html", "material.override.nav", NAV_SCAFFOLD),

    /** `partials/copyright.html` — the notice below the footer. */
    COPYRIGHT("partials/copyright.html", "material.override.copyright", COPYRIGHT_SCAFFOLD),

    /** `partials/logo.html` — the logo shown in the header. */
    LOGO("partials/logo.html", "material.override.logo", LOGO_SCAFFOLD);

    /** The name of the file, without the directories in front of it. */
    val fileName: String get() = path.substringAfterLast('/')
}

/**
 * Returns the scaffold of a partial that replaces the one of the theme.
 *
 * A partial is not extended — MkDocs includes the file of `custom_dir` *instead of* the original, so what is
 * written here is the whole thing. The scaffold says so rather than leaving an empty file behind that renders
 * a page with a hole in it.
 *
 * @param original the path of the partial inside the theme
 */
private fun replace(original: String): String =
    """
    {#
      Replaces $original of Material for MkDocs.
      The theme includes this file instead of its own, so everything the original rendered has to be
      rendered here. Start from the original of the installed version rather than from an empty file.
    #}
    """.trimIndent() + "\n"

/** The scaffold of `main.html`: the base template extended, with one block opened as an example. */
private const val MAIN_SCAFFOLD: String =
    "{% extends \"base.html\" %}\n" +
        "\n" +
        "{% block extrahead %}\n" +
        "  {{ super() }}\n" +
        "  {# Add to the head of every page here. #}\n" +
        "{% endblock %}\n"

/** The scaffold of `partials/header.html`. */
private val HEADER_SCAFFOLD: String = replace("partials/header.html")

/** The scaffold of `partials/footer.html`. */
private val FOOTER_SCAFFOLD: String = replace("partials/footer.html")

/** The scaffold of `partials/nav.html`. */
private val NAV_SCAFFOLD: String = replace("partials/nav.html")

/** The scaffold of `partials/copyright.html`. */
private val COPYRIGHT_SCAFFOLD: String = replace("partials/copyright.html")

/** The scaffold of `partials/logo.html`. */
private val LOGO_SCAFFOLD: String = replace("partials/logo.html")
