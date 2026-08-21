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
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplateError
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * First step of the site creation wizard: the purely technical layout of the new site.
 *
 * The name asked for here is the *logical* name of the site, and it is also the directory the site is
 * created in — location plus name form the site root. Everything the site is called towards its readers is
 * asked for later, in [MkDocsSiteInfoStep]; this step decides only where files end up.
 *
 * The output directory follows the resulting root path, because which build system surrounds the site
 * decides where its output belongs — until the user edits the field, from which point their value wins.
 *
 * @param project the project the site is created in
 * @param initialDirectory the directory the wizard started from, prefilled into the location field
 */
class MkDocsLayoutStep(
    project: Project,
    private val initialDirectory: String,
) : MkDocsValidatingStep {

    companion object {

        /** Characters a directory name cannot carry on the supported platforms. */
        private val ILLEGAL_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")

        /**
         * Returns the site root for a site named [name] below [baseDirectory].
         *
         * The name becomes a directory name, with characters no file system accepts replaced by `_`. An
         * empty name leaves the base directory itself — the field must not show a dangling separator while
         * the user has not typed anything yet.
         *
         * @param baseDirectory the location the site is created in
         * @param name the name as typed so far
         */
        @JvmStatic
        fun locationFor(baseDirectory: String, name: String): String {
            val directoryName = name.trim().replace(ILLEGAL_NAME_CHARACTERS, "_").trim()
            return if (directoryName.isEmpty()) baseDirectory else "$baseDirectory/$directoryName"
        }

        /**
         * Returns the output directory suggested for a site created at [location].
         *
         * A location that is no usable path yet keeps the MkDocs default — there is nothing to inspect.
         *
         * @param location the site root as it stands so far
         */
        @JvmStatic
        fun siteDirFor(location: String): String {
            val path = runCatching { Path.of(location.trim()) }.getOrNull()
                ?: return MkDocsSiteTemplate.DEFAULT_SITE_DIR
            if (!path.isAbsolute) return MkDocsSiteTemplate.DEFAULT_SITE_DIR
            return MkDocsLayout.detectSiteDir(path)
        }
    }

    /** Logical name of the site, which is also the name of the directory it is created in. */
    var name: String = ""

    /** Directory the site directory is created in. */
    var location: String = initialDirectory

    /** Name of the directory holding the documentation sources. */
    var docsDirName: String = MkDocsSiteTemplate.DEFAULT_DOCS_DIR

    /** Name of the directory holding the asset files. */
    var assetsDirName: String = MkDocsSiteTemplate.DEFAULT_ASSETS_DIR

    /** Name of the directory holding the style sheets. */
    var stylesheetsDirName: String = MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR

    /** Directory `mkdocs build` writes the rendered site to. */
    var siteDirName: String = MkDocsSiteTemplate.DEFAULT_SITE_DIR

    /** `true` once the user edited the output directory, which stops it from following the site root. */
    private var siteDirEditedByUser = false

    /** Guards the output directory field against mistaking the plugin's own writes for user input. */
    private var updatingSiteDir = false

    /** `true` once every field and the panel exist, so the field listeners may touch them. */
    private var uiReady = false

    /**
     * Called after every change to the input, so the wizard can enable or disable its buttons.
     *
     * Set by [MkDocsCreateSiteWizard]; the step itself has no access to them.
     */
    var onInputChanged: (() -> Unit)? = null

    private val nameField = JBTextField().apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateSiteDir()
                refreshStatus()
            }
        })
    }

    private val locationField = TextFieldWithBrowseButton().apply {
        text = FileUtil.toSystemDependentName(initialDirectory)
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(MkDocsBundle.message("create.site.field.location.title")),
        )
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateSiteDir()
                refreshStatus()
            }
        })
    }

    private val docsDirField = JBTextField(MkDocsSiteTemplate.DEFAULT_DOCS_DIR).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshStatus()
        })
    }

    private val assetsDirField = JBTextField(MkDocsSiteTemplate.DEFAULT_ASSETS_DIR).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshStatus()
        })
    }

    private val stylesheetsDirField = JBTextField(MkDocsSiteTemplate.DEFAULT_STYLESHEETS_DIR).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshStatus()
        })
    }

    private val siteDirField = JBTextField(siteDirFor(initialDirectory)).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!updatingSiteDir) siteDirEditedByUser = true
                refreshStatus()
            }
        })
    }

    private val statusLabel = JBLabel().apply { isVisible = false }

    private val panel: DialogPanel = panel {
        row(MkDocsBundle.message("create.site.field.name")) {
            cell(nameField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.location")) {
            cell(locationField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.docsDir")) {
            cell(docsDirField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.assetsDir")) {
            cell(assetsDirField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.stylesheetsDir")) {
            cell(stylesheetsDirField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.siteDir")) {
            cell(siteDirField).align(AlignX.FILL)
        }
        row {
            comment(MkDocsBundle.message("create.site.hint"))
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
     * Throwing here is how a wizard step blocks *Next* and *Finish*: the platform catches the exception and
     * shows its message instead of moving on.
     *
     * @param finishChosen `true` if the user pressed *Finish* rather than *Next*
     * @throws CommitStepException if the input cannot produce a site
     */
    @Throws(CommitStepException::class)
    override fun _commit(finishChosen: Boolean) {
        readInput()
        validate()?.let { error ->
            throw CommitStepException(messageFor(error))
        }
    }

    /**
     * Renders [error] for the user, passing the resulting site root so a path problem names the path it
     * means.
     *
     * @param error the reason the input was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String =
        MkDocsBundle.message(error.messageKey, rootPathText())

    /**
     * No icon: [com.intellij.ide.wizard.AbstractWizard] renders it as a strip down the left edge of the
     * dialog, stretching whatever it is given over the full height.
     */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = nameField

    /**
     * Points the output directory at what the build system around the site root expects.
     *
     * Does nothing once the user edited the field themselves — from then on their value wins.
     */
    private fun updateSiteDir() {
        if (!uiReady || siteDirEditedByUser) return
        val suggestion = siteDirFor(rootPathText())
        if (siteDirField.text == suggestion) return
        updatingSiteDir = true
        try {
            siteDirField.text = suggestion
        } finally {
            updatingSiteDir = false
        }
    }

    /**
     * Updates the line below the fields while the user types.
     *
     * Two things are worth showing there: that the target directory already holds an MkDocs configuration
     * file, which stops the creation, and that it merely is not empty, which does not. The remaining errors
     * — a missing name, an unusable directory name — are obvious from the field itself and would only
     * flicker while typing, so they are left to the commit hook.
     */
    private fun refreshStatus() {
        // The field listeners fire while the fields are still being constructed, before the panel exists.
        if (!uiReady) return
        val template = buildTemplate()
        val error = template?.validate()
        val warning = if (error == null) template?.warn() else null

        when {
            error == MkDocsSiteTemplateError.SITE_EXISTS -> {
                statusLabel.icon = AllIcons.General.Error
                statusLabel.text = messageFor(error)
                statusLabel.isVisible = true
            }

            warning != null -> {
                statusLabel.icon = AllIcons.General.Warning
                statusLabel.text = MkDocsBundle.message(warning.messageKey)
                statusLabel.isVisible = true
            }

            else -> {
                statusLabel.text = ""
                statusLabel.icon = null
                statusLabel.isVisible = false
            }
        }

        onInputChanged?.invoke()
    }

    /** Copies the current field contents into the properties of this step. */
    private fun readInput() {
        name = nameField.text.trim()
        location = locationField.text.trim()
        docsDirName = docsDirField.text.trim()
        assetsDirName = assetsDirField.text.trim()
        stylesheetsDirName = stylesheetsDirField.text.trim()
        siteDirName = siteDirField.text.trim()
    }

    /** The site root as text: the location the name is appended to. */
    private fun rootPathText(): String = locationFor(locationField.text.trim(), nameField.text)

    /**
     * The site root, or `null` if the entered location is no usable path at all.
     *
     * The path does not have to exist — it usually does not, because the name was appended to the location.
     */
    fun rootPath(): Path? = runCatching { Path.of(rootPathText()) }.getOrNull()

    /**
     * Builds the template from the current input, or `null` if the location is not a usable path at all.
     *
     * `site_name` is filled with the technical name so the template can be validated on its own; the wizard
     * replaces it with what the user enters in [MkDocsSiteInfoStep].
     */
    fun buildTemplate(): MkDocsSiteTemplate? {
        readInput()
        val path = rootPath() ?: return null
        return MkDocsSiteTemplate(
            rootPath = path,
            siteName = name,
            docsDirName = docsDirName,
            assetsDirName = assetsDirName,
            stylesheetsDirName = stylesheetsDirName,
            siteDirName = siteDirName,
        )
    }

    /**
     * Returns why the current input cannot be used, or `null` if it is fine.
     *
     * The missing name is reported first and separately: for this step the name is a directory name, not the
     * site name, so the message of [MkDocsSiteTemplateError.BLANK_SITE_NAME] would point at the wrong field.
     * A location that is no usable path at all is reported as an invalid root — from the user's point of
     * view that is exactly what it is.
     */
    override fun validate(): MkDocsSiteTemplateError? {
        if (nameField.text.isBlank()) return MkDocsSiteTemplateError.BLANK_NAME
        val template = buildTemplate() ?: return MkDocsSiteTemplateError.INVALID_ROOT
        return template.validate()
    }

    /** Sets the name as if it had been typed. */
    @TestOnly
    internal fun setNameForTest(value: String) {
        nameField.text = value
    }

    /** Sets the location as if it had been typed. */
    @TestOnly
    internal fun setLocationForTest(value: String) {
        locationField.text = value
    }

    /** Sets the documentation directory as if it had been typed. */
    @TestOnly
    internal fun setDocsDirNameForTest(value: String) {
        docsDirField.text = value
    }

    /** Sets the assets directory as if it had been typed. */
    @TestOnly
    internal fun setAssetsDirNameForTest(value: String) {
        assetsDirField.text = value
    }

    /** Sets the stylesheets directory as if it had been typed. */
    @TestOnly
    internal fun setStylesheetsDirNameForTest(value: String) {
        stylesheetsDirField.text = value
    }

    /** Sets the output directory as if it had been typed. */
    @TestOnly
    internal fun setSiteDirNameForTest(value: String) {
        siteDirField.text = value
    }
}
