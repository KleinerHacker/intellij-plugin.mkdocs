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

package org.pcsoft.ij.plugin.mkdocs

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.SimpleTextAttributes

/**
 * Central registry of every text attribute shipped with the plugin.
 *
 * The concrete colors are not defined here but in the color scheme files under the `colorSchemes` resource
 * folder (`MkDocsDefault.xml` for light themes, `MkDocsDarcula.xml` for dark themes), registered through the
 * `additionalTextAttributes` extension point. Resolving happens against the active global scheme, so a theme
 * switch between light and dark is picked up automatically.
 */
object MkDocsTextAttributes {

    /** Attribute of the MkDocs site name rendered behind a site root directory in the project view. */
    @JvmField
    val SiteName: TextAttributesKey = TextAttributesKey.createTextAttributesKey("MKDOCS_SITE_NAME")

    /**
     * Resolves [key] against the currently active color scheme.
     *
     * @param key the attribute to resolve
     * @return the attribute as needed by the coloured tree renderers of the platform
     */
    @JvmStatic
    fun asSimpleTextAttributes(key: TextAttributesKey): SimpleTextAttributes =
        SimpleTextAttributes.fromTextAttributes(EditorColorsManager.getInstance().globalScheme.getAttributes(key))
}