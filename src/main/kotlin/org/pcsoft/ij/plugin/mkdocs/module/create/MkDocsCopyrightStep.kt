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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsCopyrightService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsCopyrightProfile
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Fourth step of the site creation wizard: the notice shown in the footer of every page.
 *
 * The IDE usually knows one already — the Copyright settings hold the notice the project stamps onto its
 * sources. Which of them applies is not always decided: a project may carry several notices without marking
 * one as the default, and then the choice is the user's. Whatever the choice, the text itself stays editable,
 * because a footer line is rarely word for word the notice of a source file.
 *
 * @param project the project the site is created in
 */
class MkDocsCopyrightStep(project: Project) : Step {

    /** Value written to `copyright`. */
    var copyright: String = ""

    private val service = MkDocsCopyrightService.getInstance(project)

    /** Every notice configured in the IDE, empty when the Copyright plugin is absent. */
    private val profiles: List<MkDocsCopyrightProfile> = service.profiles()

    /** The notice marked as the default one, or `null` if the user marked none. */
    private val defaultProfile: MkDocsCopyrightProfile? = service.defaultProfile()

    /**
     * `true` when the user has to pick a notice themselves.
     *
     * Only the case with several notices and no default: one notice is an unambiguous suggestion, and a
     * marked default is a decision the user already made.
     */
    private val needsChoice: Boolean = defaultProfile == null && profiles.size > 1

    /** `true` once the user edited the notice, which stops it from following the selected profile. */
    private var noticeEditedByUser = false

    /** Guards the notice field against mistaking the plugin's own writes for user input. */
    private var updatingNotice = false

    private val profileBox = ComboBox(DefaultComboBoxModel(profiles.map { it.name }.toTypedArray())).apply {
        isVisible = needsChoice
        addActionListener { applySelectedProfile() }
    }

    private val noticeField = JBTextField(initialNotice()).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!updatingNotice) noticeEditedByUser = true
            }
        })
    }

    private val panel: DialogPanel = panel {
        if (needsChoice) {
            row(MkDocsBundle.message("create.site.field.copyrightProfile")) {
                cell(profileBox).align(AlignX.FILL)
            }
        }
        row(MkDocsBundle.message("create.site.field.copyright")) {
            cell(noticeField).align(AlignX.FILL)
        }
        row {
            comment(MkDocsBundle.message("create.site.copyright.hint"))
        }
    }.apply { border = JBUI.Borders.empty(8) }

    override fun _init() = Unit

    override fun _commit(finishChosen: Boolean) {
        copyright = noticeField.text.trim()
    }

    /** No icon, for the same reason as in [MkDocsLayoutStep]. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = if (needsChoice) profileBox else noticeField

    /**
     * The notice the field starts with.
     *
     * A marked default wins, then a single configured notice; with several notices and no default the field
     * follows the selection instead, which starts at the first entry.
     */
    private fun initialNotice(): String =
        defaultProfile?.notice ?: profiles.firstOrNull()?.notice ?: ""

    /** Puts the notice of the selected profile into the field, unless the user edited it. */
    private fun applySelectedProfile() {
        if (noticeEditedByUser) return
        val notice = profiles.getOrNull(profileBox.selectedIndex)?.notice ?: return
        if (noticeField.text == notice) return
        updatingNotice = true
        try {
            noticeField.text = notice
        } finally {
            updatingNotice = false
        }
    }

    /**
     * Fills the field with a notice built from [author] when the IDE has none configured.
     *
     * Called when the step is entered, because the author is only known once the second step was filled in.
     *
     * @param author the author entered in [MkDocsSiteInfoStep]
     */
    fun suggestNoticeFor(author: String) {
        if (profiles.isNotEmpty() || noticeEditedByUser) return
        val suggestion = MkDocsCopyrightService.fallbackNotice(author)
        if (noticeField.text == suggestion) return
        updatingNotice = true
        try {
            noticeField.text = suggestion
        } finally {
            updatingNotice = false
        }
    }

    /**
     * Applies what was entered here to [template].
     *
     * @param template the template collected so far
     */
    fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate {
        copyright = noticeField.text.trim()
        return template.copy(copyright = copyright)
    }

    /** `true` when the profile selection is shown, exposed for tests. */
    @TestOnly
    internal fun isProfileChoiceVisible(): Boolean = needsChoice

    /** Sets the notice as if it had been typed. */
    @TestOnly
    internal fun setCopyrightForTest(value: String) {
        noticeField.text = value
    }

    /** The notice as it currently stands, exposed for tests. */
    @TestOnly
    internal fun currentNotice(): String = noticeField.text
}
