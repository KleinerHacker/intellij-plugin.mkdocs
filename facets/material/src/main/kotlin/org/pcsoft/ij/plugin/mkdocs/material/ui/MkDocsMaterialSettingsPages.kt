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

package org.pcsoft.ij.plugin.mkdocs.material.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings

/**
 * The four Material settings pages, built once and wired to each other.
 *
 * Both hosts go through this class rather than through the pages themselves, and that is the whole point: the
 * site creation wizard and the Angular Material facet show the same four page objects, so a page never exists
 * twice with two behaviours to keep in step. What a host adds is the frame around them — steps in one case,
 * tabs in the other.
 *
 * The wiring is what the pages cannot do on their own: the extensions page has to know what the features page
 * currently has ticked, because *required* is a statement about the feature selection and not about the theme.
 *
 * @param project the project the site belongs to, or `null` in a wizard that has none yet
 * @param docsDir the documentation directory of the site, or `null` while it does not exist
 * @param siteRoot the directory holding the configuration file, or `null` while it does not exist
 */
class MkDocsMaterialSettingsPages(
    project: Project? = null,
    docsDir: () -> VirtualFile? = { null },
    siteRoot: () -> VirtualFile? = { null },
) {

    /** The page holding the palette and the fonts. */
    val appearance: MkDocsMaterialAppearancePage = MkDocsMaterialAppearancePage()

    /** The page holding `theme.features`. */
    val features: MkDocsMaterialFeaturesPage = MkDocsMaterialFeaturesPage()

    /** The page holding the paths, the language and the writing direction. */
    val assets: MkDocsMaterialAssetsPage = MkDocsMaterialAssetsPage(project, docsDir, siteRoot)

    /** The page holding the Markdown extensions. */
    val extensions: MkDocsMaterialExtensionsPage = MkDocsMaterialExtensionsPage { features.selectedFlags() }

    /** The pages in the order both hosts show them. */
    val pages: List<MkDocsMaterialSettingsPage> = listOf(appearance, features, assets, extensions)

    /** Called after any page changed, so a host can re-check its buttons. */
    var onChanged: () -> Unit = {}

    init {
        pages.forEach { page ->
            page.onChanged = {
                // Ticking a feature can turn a recommendation into a requirement, which the other page shows.
                if (page === features) extensions.refresh()
                onChanged()
            }
        }
    }

    /**
     * Fills every page from [settings].
     *
     * @param settings the snapshot to show
     */
    fun reset(settings: MkDocsMaterialSettings) {
        pages.forEach { it.reset(settings) }
    }

    /**
     * Returns [settings] with the edits of every page folded into it.
     *
     * The order does not matter — each page only replaces the keys it owns.
     *
     * @param settings the snapshot to change
     */
    fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings =
        pages.fold(settings) { current, page -> page.applyTo(current) }

    /**
     * Tells whether any page would change [original].
     *
     * @param original the snapshot the pages were reset with
     */
    fun isModified(original: MkDocsMaterialSettings): Boolean = applyTo(original) != original

    /** The first reason the current input cannot be used, or `null` if there is none. */
    fun validate(): String? = pages.firstNotNullOfOrNull { it.validate() }
}
