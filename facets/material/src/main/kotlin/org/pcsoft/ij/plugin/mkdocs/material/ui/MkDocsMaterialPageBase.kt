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

import com.intellij.openapi.ui.DialogPanel
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import javax.swing.JComponent

/**
 * What the four Material settings pages have in common.
 *
 * Two things, and both matter for how the pages are used. The built component is created once and cached, so
 * a host asking for it twice — the wizard does, once per time the step is shown — gets the controls carrying
 * the state the page was reset with rather than a fresh, empty form. And the controls themselves are fields
 * of the page, created with it, so [reset] and [applyTo] work on a page whose component was never built at
 * all: that is what lets the model half of a page be tested without a screen.
 *
 * @param id the identifier of the page
 * @param titleKey the bundle key of the page title
 */
abstract class MkDocsMaterialPageBase(
    override val id: String,
    private val titleKey: String,
) : MkDocsMaterialSettingsPage {

    override val title: String
        get() = MkDocsMaterialBundle.messageOrDefault(titleKey, titleKey) ?: titleKey

    override var onChanged: () -> Unit = {}

    /** The built page, or `null` while it was never asked for. */
    private var built: JComponent? = null

    /**
     * Builds the content of the page, without the frame around it.
     *
     * Called at most once, from [component].
     */
    protected abstract fun createContent(): DialogPanel

    final override fun component(): JComponent =
        built ?: MkDocsMaterialPagePanel(createContent()).also { built = it }

    /** Tells the host that something on the page changed. */
    protected fun fireChanged() {
        onChanged()
    }
}
