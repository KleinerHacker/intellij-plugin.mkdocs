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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.ide.wizard.Step

/**
 * What the site creation wizard expects from its own pages.
 *
 * The wizard shows a mix of fixed pages and pages contributed by a feature, and their order changes while it
 * is open. It therefore asks the step object in front of it rather than the step index: whether its input is
 * usable, and what it wants to pull from earlier pages when it is entered.
 *
 * Only implemented by the pages of this wizard — a feature contributes
 * [org.pcsoft.ij.plugin.mkdocs.types.MkDocsFeatureWizardStep] instead, which the wizard treats the same way.
 */
interface MkDocsValidatingStep : Step {

    /**
     * Returns why the current input cannot be used, or `null` if it is fine.
     *
     * The wizard only asks whether the result is `null` — it disables *Next* and *Finish* while it is not.
     */
    fun validate(): Any? = null

    /**
     * Carries what earlier pages produced into this one, called every time the page is entered.
     *
     * @param wizard the wizard showing this page, holding the pages entered before it
     */
    fun onEnter(wizard: MkDocsCreateSiteWizard) = Unit
}
