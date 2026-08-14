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

package org.pcsoft.ij.plugin.mkdocs.types

import com.intellij.ide.wizard.Step

/**
 * A wizard page contributed by an [MkDocsSiteFeature].
 *
 * The site creation wizard shows these pages behind its own ones, for as long as the feature they belong to
 * is selected. They are created once per wizard by [MkDocsSiteFeature.createSteps] and kept afterwards, so
 * unticking and re-ticking a feature does not throw away what the user typed.
 *
 * A step contributes to the result in one place only: [applyTo]. The wizard collects input, nothing more —
 * writing anything into the new site is left to [MkDocsSiteFeature.apply].
 */
interface MkDocsFeatureWizardStep : Step {

    /** The feature this page belongs to. Names the page in the dialog title. */
    val feature: MkDocsSiteFeature

    /**
     * Returns why the current input cannot be used, or `null` if it is fine.
     *
     * The wizard only asks whether the result is `null` — it disables *Next* and *Finish* while it is not.
     * What the value is beyond that is up to the implementation.
     */
    fun validate(): Any? = null

    /**
     * Applies what was entered on this page to [template].
     *
     * Called after the core pages contributed theirs, in the order the pages are shown.
     *
     * @param template the template collected so far
     */
    fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate = template
}
