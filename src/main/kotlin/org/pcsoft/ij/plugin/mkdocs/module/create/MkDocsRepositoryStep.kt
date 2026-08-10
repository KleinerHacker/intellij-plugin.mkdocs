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

import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.CommitStepException
import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsScmService
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsScmUrl
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Third step of the site creation wizard: where the sources of the documentation live.
 *
 * Both keys are optional. When the project is under version control the fields start out filled with what
 * the repository says, and an entry that no longer matches it is reported as a warning — a `repo_url`
 * pointing somewhere else is not wrong, a site may well document a different repository, but it is worth
 * noticing before the site is created.
 *
 * @param project the project the site is created in
 * @param directory the directory the wizard started from, used to find the repository
 */
class MkDocsRepositoryStep(project: Project, directory: VirtualFile?) : Step {

    /** Value written to `repo_name`. */
    var repoName: String = ""

    /** Value written to `repo_url`. */
    var repoUrl: String = ""

    /** The address of the repository the site is created in, or `null` if there is none. */
    private val detectedUrl: String? = MkDocsScmService.getInstance(project).repositoryUrl(directory)

    /** The name of that repository, or `null` if there is none. */
    private val detectedName: String? = MkDocsScmService.getInstance(project).repositoryName(directory)

    /** `true` once the user edited the name, which stops it from following the address. */
    private var nameEditedByUser = false

    /** Guards the name field against mistaking the plugin's own writes for user input. */
    private var updatingName = false

    /** `true` once every field and the panel exist, so the field listeners may touch them. */
    private var uiReady = false

    /**
     * Called after every change to the input, so the wizard can enable or disable its buttons.
     *
     * Set by [MkDocsCreateSiteWizard]; the step itself has no access to them.
     */
    var onInputChanged: (() -> Unit)? = null

    private val nameField = JBTextField(detectedName.orEmpty()).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!updatingName) nameEditedByUser = true
                refreshStatus()
            }
        })
    }

    private val urlField = JBTextField(detectedUrl.orEmpty()).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateName()
                refreshStatus()
            }
        })
    }

    private val statusLabel = JBLabel().apply { isVisible = false }

    private val panel: DialogPanel = panel {
        row(MkDocsBundle.message("create.site.field.repoName")) {
            cell(nameField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.repoUrl")) {
            cell(urlField).align(AlignX.FILL)
        }
        row {
            comment(MkDocsBundle.message("create.site.repo.hint"))
        }
        row {
            cell(statusLabel).align(AlignX.FILL)
        }
    }.apply { border = JBUI.Borders.empty(8) }

    init {
        uiReady = true
        refreshStatus()
    }

    override fun _init() = refreshStatus()

    /**
     * Writes the entered values back into the properties this step exposes and refuses invalid input.
     *
     * The mismatch with the local repository is deliberately not checked here: it is a warning, and a
     * warning must not stop the wizard.
     *
     * @param finishChosen `true` if the user pressed *Finish* rather than *Next*
     * @throws CommitStepException if the input cannot produce a site
     */
    @Throws(CommitStepException::class)
    override fun _commit(finishChosen: Boolean) {
        readInput()
        validate()?.let { error ->
            throw CommitStepException(MkDocsBundle.message(error.messageKey, repoUrl))
        }
    }

    /** No icon, for the same reason as in [MkDocsLayoutStep]. */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = nameField

    /**
     * Lets the name follow the address, the way the address itself follows the repository.
     *
     * Does nothing once the user edited the name themselves.
     */
    private fun updateName() {
        if (!uiReady || nameEditedByUser) return
        val suggestion = MkDocsScmUrl.toRepositoryName(urlField.text).orEmpty()
        if (nameField.text == suggestion) return
        updatingName = true
        try {
            nameField.text = suggestion
        } finally {
            updatingName = false
        }
    }

    /**
     * Updates the line below the fields while the user types.
     *
     * Only the mismatch with the local repository is shown here — an unusable address is reported by the
     * commit hook, because it would otherwise flicker with every keystroke.
     */
    private fun refreshStatus() {
        if (!uiReady) return
        val warning = mismatchWarning()
        if (warning == null) {
            statusLabel.text = ""
            statusLabel.icon = null
            statusLabel.isVisible = false
        } else {
            statusLabel.icon = AllIcons.General.Warning
            statusLabel.text = warning
            statusLabel.isVisible = true
        }
        onInputChanged?.invoke()
    }

    /**
     * Returns the message telling the user that their entry does not match the local repository.
     *
     * Only meaningful when a repository was detected at all. The address is compared through
     * [MkDocsScmUrl.isSameRepository], so the SSH form of an address counts as equal to its `https` form.
     */
    fun mismatchWarning(): String? {
        val enteredUrl = urlField.text.trim()
        val enteredName = nameField.text.trim()
        val url = detectedUrl
        val name = detectedName

        if (url != null && enteredUrl.isNotEmpty() && !MkDocsScmUrl.isSameRepository(enteredUrl, url)) {
            return MkDocsBundle.message("create.site.warning.repoUrl", url)
        }
        if (name != null && enteredName.isNotEmpty() && !enteredName.equals(name, ignoreCase = true)) {
            return MkDocsBundle.message("create.site.warning.repoName", name)
        }
        return null
    }

    /** Copies the current field contents into the properties of this step. */
    private fun readInput() {
        repoName = nameField.text.trim()
        repoUrl = urlField.text.trim()
    }

    /** Returns why the current input cannot be used, or `null` if it is fine. */
    fun validate(): MkDocsSiteTemplateError? =
        if (MkDocsSiteTemplate.isUsableUrl(urlField.text)) null else MkDocsSiteTemplateError.INVALID_REPO_URL

    /**
     * Applies what was entered here to [template].
     *
     * @param template the template collected so far
     */
    fun applyTo(template: MkDocsSiteTemplate): MkDocsSiteTemplate {
        readInput()
        return template.copy(repoName = repoName, repoUrl = repoUrl)
    }

    /** Sets the repository name as if it had been typed. */
    @TestOnly
    internal fun setRepoNameForTest(value: String) {
        nameField.text = value
    }

    /** Sets the repository address as if it had been typed. */
    @TestOnly
    internal fun setRepoUrlForTest(value: String) {
        urlField.text = value
    }
}
