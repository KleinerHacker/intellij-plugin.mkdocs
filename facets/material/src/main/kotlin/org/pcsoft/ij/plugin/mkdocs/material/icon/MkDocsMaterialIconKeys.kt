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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialKeys

/**
 * The places a configuration file of *Material for MkDocs* names an icon at.
 *
 * Two features ask the same question and must never answer it differently: the completion offering the icon
 * names, and the inlay hint painting the drawing in front of a name already written. So the paths live here
 * and nowhere else.
 *
 * Decided on the whole path, never on the name of the nearest key: below `theme.icon` the key is the element
 * the icon is put on — `repo`, `edit`, `menu` — while the other places end in `icon` themselves.
 */
object MkDocsMaterialIconKeys {

    /** The path of the icons of the theme itself, `theme.icon.repo` and its like, `admonition` and `tag` included. */
    private const val PATH_THEME_ICON = "theme.icon."

    /** The tail of the path of the icon on the button switching between light and dark. */
    private const val PATH_TOGGLE = "palette.toggle.icon"

    /** The tail of the path of the icon of a link in the footer. */
    private const val PATH_SOCIAL = "extra.social.icon"

    /** The tail of the path of the icon of a rating of the feedback widget. */
    private const val PATH_RATING = "extra.analytics.feedback.ratings.icon"

    /**
     * Returns `true` if [position] sits in a value that names an icon of the theme.
     *
     * The caller must hold a read action.
     *
     * @param position the element to judge, a scalar of the configuration file or an element inside one
     */
    fun isIconValue(position: PsiElement): Boolean {
        val keyValue = position.parentOfType<YAMLScalar>()?.parent as? YAMLKeyValue
            ?: position.parentOfType<YAMLKeyValue>()
            ?: return false
        return isIconPath(MkDocsMaterialKeys.pathOf(keyValue))
    }

    /**
     * Returns `true` if the dotted [path] addresses a value naming an icon of the theme.
     *
     * @param path the dotted path of a key, as [MkDocsMaterialKeys.pathOf] builds it
     */
    fun isIconPath(path: String): Boolean =
        path.startsWith(PATH_THEME_ICON) ||
            path.endsWith(PATH_TOGGLE) ||
            path.endsWith(PATH_SOCIAL) ||
            path.endsWith(PATH_RATING)
}
