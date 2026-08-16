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

import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import javax.swing.JComponent

/**
 * One page of the Material settings, written once and shown in two places.
 *
 * The site creation wizard shows the pages as its own steps, and the Angular Material facet shows the very
 * same page objects as tabs of the Project Structure dialog. Neither host knows what a page contains: it hands
 * a [MkDocsMaterialSettings] in through [reset] and takes one back out through [applyTo], and everything
 * between the two is the page's business.
 *
 * A page only ever touches the keys it owns. [applyTo] therefore returns a copy of what it was given with its
 * own fields replaced, which is what lets a host fold the four pages over one snapshot in any order and get
 * the sum of their edits rather than the last one to run.
 */
interface MkDocsMaterialSettingsPage {

    /** Stable identifier of the page. Never shown to the user; a test and a host address a page by it. */
    val id: String

    /** The name of the page, shown as the wizard title and as the label of the facet tab. */
    val title: String

    /**
     * The built page.
     *
     * Built on the first call and kept afterwards — a host may ask for it more than once, and the controls
     * carry the state the page was reset with.
     */
    fun component(): JComponent

    /**
     * Fills the controls of the page from [settings].
     *
     * Every later [applyTo] is relative to this: a page that was reset and left alone gives back exactly what
     * it was given.
     *
     * @param settings the snapshot to show
     */
    fun reset(settings: MkDocsMaterialSettings)

    /**
     * Returns [settings] with the keys of this page replaced by what its controls currently say.
     *
     * @param settings the snapshot to change
     * @return the changed snapshot, or [settings] itself if the page changes nothing
     */
    fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings

    /**
     * Tells whether the page would change [original].
     *
     * @param original the snapshot the page was reset with
     */
    fun isModified(original: MkDocsMaterialSettings): Boolean = applyTo(original) != original

    /**
     * Returns why the current input cannot be used, or `null` if it is fine.
     *
     * The wizard disables *Next* and *Finish* while this is not `null`.
     */
    fun validate(): String? = null

    /**
     * Called after every change the user made on the page.
     *
     * Set by the host: the wizard re-checks its buttons, the facet dialog notices that something is modified.
     */
    var onChanged: () -> Unit
}
