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

import com.intellij.ide.wizard.CommitStepException
import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsScmService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Second step of the site creation wizard: everything the site is called towards its readers.
 *
 * These are the `site_*` keys of the configuration file, in the order MkDocs itself documents them. Only the
 * site name is required — the remaining keys are left out of the generated file when their field is empty,
 * because a key written empty says less than a key that is absent.
 *
 * @param project the project the site is created in
 * @param directory the directory the wizard started from, used to find the author in the repository
 */
class MkDocsSiteInfoStep(project: Project, directory: VirtualFile?) : Step {

    /** Value written to `site_name`. */
    var siteName: String = ""

    /** Value written to `site_author`. */
    var siteAuthor: String = ""

    /** Value written to `site_description`. */
    var siteDescription: String = ""

    /** Value written to `site_url`. */
    var siteUrl: String = ""

    /** `true` once the user edited the site name, which stops it from following the technical name. */
    private var siteNameEditedByUser = false

    /** Guards the site name field against mistaking the plugin's own writes for user input. */
    private var updatingSiteName = false

    /**
     * Called after every change to the input, so the wizard can enable or disable its buttons.
     *
     * Set by [MkDocsCreateSiteWizard]; the step itself has no access to them.
     */
    var onInputChanged: (() -> Unit)? = null

    private val siteNameField = JBTextField().apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!updatingSiteName) siteNameEditedByUser = true
                onInputChanged?.invoke()
            }
        })
    }

    private val authorField = JBTextField(MkDocsScmService.getInstance(project).userName(directory).orEmpty())

    private val descriptionField = JBTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private val urlField = JBTextField().apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onInputChanged?.invoke()
            }
        })
    }

    private val panel: DialogPanel = panel {
        row(MkDocsBundle.message("create.site.field.siteName")) {
            cell(siteNameField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.siteAuthor")) {
            cell(authorField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.siteDescription")) {
            scrollCell(descriptionField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.siteUrl")) {
            cell(urlField).align(AlignX.FILL)
        }
        row {
            comment(MkDocsBundle.message("create.site.info.hint"))
        }
    }.apply { border = JBUI.Borders.empty(8) }

    override fun _init() = Unit

    /**
     * Writes the entered values back into the properties this step exposes and refuses invalid input.
     *
     * @param finishChosen `true` if the user pressed *Finish* rather than *Next*
     * @throws CommitStepException if the input cannot produce a site
     */
    @Throws(CommitStepException::class)
    override fun _commit(finishChosen: Boolean) {
        readInput()
        validate()?.let { error ->
            throw CommitStepException(MkDocsBundle.message(error.messageKey, siteUrl))
        }
    }

    /** No icon, for the same reason as in [MkDocsLayoutStep]. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = siteNameField

    /**
     * Prefills the site name with the technical name entered in the first step.
     *
     * Stops as soon as the user typed a site name of their own — the two names are independent, and the
     * prefill exists only to spare the common case of typing the same thing twice.
     *
     * @param name the name entered in [MkDocsLayoutStep]
     */
    fun suggestSiteName(name: String) {
        if (siteNameEditedByUser) return
        val suggestion = name.trim()
        if (siteNameField.text == suggestion) return
        updatingSiteName = true
        try {
            siteNameField.text = suggestion
        } finally {
            updatingSiteName = false
        }
    }

    /** Copies the current field contents into the properties of this step. */
    private fun readInput() {
        siteName = siteNameField.text.trim()
        siteAuthor = authorField.text.trim()
        siteDescription = descriptionField.text.trim()
        siteUrl = urlField.text.trim()
    }

    /** Returns why the current input cannot be used, or `null` if it is fine. */
    fun validate(): MkDocsSiteTemplateError? = when {
        siteNameField.text.isBlank() -> MkDocsSiteTemplateError.BLANK_SITE_NAME
        !MkDocsSiteTemplate.isUsableUrl(urlField.text) -> MkDocsSiteTemplateError.INVALID_SITE_URL
        else -> null
    }

    /**
     * Applies what was entered here to [template].
     *
     * @param template the template collected so far
     */
    fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate {
        readInput()
        return template.copy(
            siteName = siteName,
            siteAuthor = siteAuthor,
            siteDescription = siteDescription,
            siteUrl = siteUrl,
        )
    }

    /** Sets the site name as if it had been typed. */
    @TestOnly
    internal fun setSiteNameForTest(value: String) {
        siteNameField.text = value
    }

    /** Sets the author as if it had been typed. */
    @TestOnly
    internal fun setSiteAuthorForTest(value: String) {
        authorField.text = value
    }

    /** Sets the description as if it had been typed. */
    @TestOnly
    internal fun setSiteDescriptionForTest(value: String) {
        descriptionField.text = value
    }

    /** Sets the site address as if it had been typed. */
    @TestOnly
    internal fun setSiteUrlForTest(value: String) {
        urlField.text = value
    }

    /** The author as it currently stands, so the copyright step can build its fallback notice. */
    internal fun currentAuthor(): String = authorField.text.trim()
}
