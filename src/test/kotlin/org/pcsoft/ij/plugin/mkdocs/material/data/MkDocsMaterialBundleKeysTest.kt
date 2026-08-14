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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ResourceBundle

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The guard that a missing message is found here and not by a user: every key any Material data constant
 * refers to has to exist in `messages/MkDocsBundle.properties`. The bundle is read through [ResourceBundle]
 * rather than through `MkDocsBundle`, because the data model deliberately runs without the IDE platform.
 */
class MkDocsMaterialBundleKeysTest {

    private val bundle: ResourceBundle = ResourceBundle.getBundle("messages.MkDocsBundle")

    private fun assertResolves(key: String) {
        assertTrue("missing bundle key: $key", bundle.containsKey(key))
        assertTrue("blank bundle value: $key", bundle.getString(key).isNotBlank())
    }

    /**
     * Use case: the settings page renders one section per feature group and takes the heading from the bundle.
     */
    @Test
    fun `feature group titles resolve`() {
        MkDocsMaterialFeatureGroup.entries.forEach { assertResolves(it.titleKey) }
    }

    /**
     * Use case: every feature flag is documented in completion, in QuickDoc and in the generated schema.
     */
    @Test
    fun `feature descriptions resolve`() {
        MkDocsMaterialFeatureFlag.entries.forEach { assertResolves(it.descriptionKey) }
    }

    /**
     * Use case: the palette drop down labels the two schemes.
     */
    @Test
    fun `scheme titles resolve`() {
        MkDocsMaterialScheme.entries.forEach { assertResolves(it.titleKey) }
    }

    /**
     * Use case: the extension list and the annotator explain what an extension does before offering to add it.
     */
    @Test
    fun `extension descriptions resolve`() {
        MkDocsMarkdownExtension.entries.forEach { assertResolves(it.descriptionKey) }
    }

    /**
     * Use case: the CSS variables are grouped in QuickDoc and in completion, and each carries its own
     * documentation.
     */
    @Test
    fun `css variable groups and descriptions resolve`() {
        MkDocsMaterialCssVariableGroup.entries.forEach { assertResolves(it.titleKey) }
        MkDocsMaterialCssVariables.all.forEach { assertResolves(it.descriptionKey) }
    }

    /**
     * Use case: the `extra` keys are described in the generated schema, down to the nested ones — a missing
     * description on a deeply nested key is exactly what a hand written check would overlook.
     */
    @Test
    fun `extra key descriptions resolve, including nested ones`() {
        fun walk(field: MkDocsMaterialExtraField) {
            assertResolves(field.descriptionKey)
            field.children.forEach(::walk)
        }
        MkDocsMaterialExtraKeys.ALL.forEach(::walk)
    }
}
