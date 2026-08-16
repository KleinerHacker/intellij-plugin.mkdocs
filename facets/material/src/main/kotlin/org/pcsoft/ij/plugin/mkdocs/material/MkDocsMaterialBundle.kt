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

package org.pcsoft.ij.plugin.mkdocs.material

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.MkDocsMaterialBundle"

/**
 * Message bundle for every user visible text of the Angular Material feature.
 *
 * Kept apart from the bundle of the plugin because the feature owns its
 * texts: a plugin without this feature ships none of them, and a translator working on the feature has one
 * file to look at rather than a section inside the plugin's.
 *
 * [DynamicBundle] is used so the IDE's language pack plugins can override the texts at runtime.
 */
object MkDocsMaterialBundle : DynamicBundle(BUNDLE) {

    /**
     * Resolves a localized message.
     *
     * @param key the property key inside `messages/MkDocsMaterialBundle.properties`
     * @param params the values substituted into the message pattern
     */
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
