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
import org.pcsoft.ij.plugin.mkdocs.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsLayout
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplate
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsSiteTemplateError
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * First step of the site creation wizard: where the site goes and how it is named.
 *
 * The location follows the site name the way the new project dialog does it: the name is appended to the
 * directory the wizard started from, so typing a name is enough. As soon as the user edits the location
 * themselves the appending stops — from then on the field is theirs. The output directory follows the
 * location by the same rule, because which build system surrounds the site decides where its output belongs.
 *
 * @param project the project the site is created in
 * @param initialDirectory the directory the wizard started from, used as the base of the location
 */
class MkDocsSiteStep(
    project: Project,
    private val initialDirectory: String,
) : Step {

    companion object {

        /** Characters a directory name cannot carry on the supported platforms. */
        private val ILLEGAL_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")

        /**
         * Returns the location a site with [siteName] gets below [baseDirectory].
         *
         * The site name becomes a directory name, with characters no file system accepts replaced by `_`.
         * An empty name leaves the base directory itself — the field must not show a dangling separator
         * while the user has not typed anything yet.
         *
         * @param baseDirectory the directory the wizard started from
         * @param siteName the site name as typed so far
         */
        @JvmStatic
        fun locationFor(baseDirectory: String, siteName: String): String {
            val directoryName = siteName.trim().replace(ILLEGAL_NAME_CHARACTERS, "_").trim()
            return if (directoryName.isEmpty()) baseDirectory else "$baseDirectory/$directoryName"
        }

        /**
         * Returns the output directory suggested for a site created at [location].
         *
         * A location that is no usable path yet keeps the MkDocs default — there is nothing to inspect.
         *
         * @param location the location as typed so far
         */
        @JvmStatic
        fun siteDirFor(location: String): String {
            val path = runCatching { Path.of(location.trim()) }.getOrNull()
                ?: return MkDocsProject.DEFAULT_SITE_DIR
            if (!path.isAbsolute) return MkDocsProject.DEFAULT_SITE_DIR
            return MkDocsLayout.detectSiteDir(path)
        }
    }

    /** Value written to `site_name`. */
    var siteName: String = ""

    /** Name of the directory holding the documentation sources. */
    var docsDirName: String = MkDocsProject.DEFAULT_DOCS_DIR

    /** Name of the directory holding the asset files. */
    var assetsDirName: String = MkDocsProject.DEFAULT_ASSETS_DIR

    /** Directory `mkdocs build` writes the rendered site to. */
    var siteDirName: String = MkDocsProject.DEFAULT_SITE_DIR

    /** `true` once the user edited the location, which stops it from following the site name. */
    private var locationEditedByUser = false

    /** `true` once the user edited the output directory, which stops it from following the location. */
    private var siteDirEditedByUser = false

    /** Guards the location field against mistaking the plugin's own writes for user input. */
    private var updatingLocation = false

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

    private val locationField = TextFieldWithBrowseButton().apply {
        text = FileUtil.toSystemDependentName(initialDirectory)
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(MkDocsBundle.message("create.site.field.location.title")),
        )
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!updatingLocation) locationEditedByUser = true
                updateSiteDir()
                refreshStatus()
            }
        })
    }

    private val siteNameField = JBTextField().apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateLocation(text)
                refreshStatus()
            }
        })
    }

    private val docsDirField = JBTextField(MkDocsProject.DEFAULT_DOCS_DIR).apply {
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshStatus()
        })
    }

    private val assetsDirField = JBTextField(MkDocsProject.DEFAULT_ASSETS_DIR).apply {
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
        row(MkDocsBundle.message("create.site.field.location")) {
            cell(locationField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.siteName")) {
            cell(siteNameField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.docsDir")) {
            cell(docsDirField).align(AlignX.FILL)
        }
        row(MkDocsBundle.message("create.site.field.assetsDir")) {
            cell(assetsDirField).align(AlignX.FILL)
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
     * Renders [error] for the user, passing the entered location so a path problem names the path it means.
     *
     * @param error the reason the input was refused
     */
    fun messageFor(error: MkDocsSiteTemplateError): String =
        MkDocsBundle.message(error.messageKey, locationField.text.trim())

    /**
     * No icon: [com.intellij.ide.wizard.AbstractWizard] renders it as a strip down the left edge of the
     * dialog, stretching whatever it is given over the full height.
     */
    override fun getIcon(): Icon? = null

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = siteNameField

    /**
     * Appends [name] to the directory the wizard started from.
     *
     * Does nothing once the user edited the location themselves — their path wins, exactly as in the new
     * project dialog.
     *
     * @param name the site name as typed so far
     */
    private fun updateLocation(name: String) {
        if (locationEditedByUser) return
        updatingLocation = true
        try {
            locationField.text = FileUtil.toSystemDependentName(locationFor(initialDirectory, name))
        } finally {
            updatingLocation = false
        }
    }

    /**
     * Points the output directory at what the build system around the location expects.
     *
     * Does nothing once the user edited the field themselves — from then on their value wins, the same way
     * the location stops following the site name.
     */
    private fun updateSiteDir() {
        if (!uiReady || siteDirEditedByUser) return
        val suggestion = siteDirFor(locationField.text)
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
        siteName = siteNameField.text.trim()
        docsDirName = docsDirField.text.trim()
        assetsDirName = assetsDirField.text.trim()
        siteDirName = siteDirField.text.trim()
    }

    /**
     * Builds the template from the current input, or `null` if the location is not a usable path at all.
     *
     * The path does not have to exist — it usually does not, because the site name was appended to it.
     */
    fun buildTemplate(): MkDocsSiteTemplate? {
        readInput()
        val path = runCatching { Path.of(locationField.text.trim()) }.getOrNull() ?: return null
        return MkDocsSiteTemplate(
            rootPath = path,
            siteName = siteName,
            docsDirName = docsDirName,
            assetsDirName = assetsDirName,
            siteDirName = siteDirName,
        )
    }

    /**
     * Returns why the current input cannot be used, or `null` if it is fine.
     *
     * A location that is no usable path at all is reported as an invalid root — from the user's point of view
     * that is exactly what it is. Written out rather than as an elvis chain on purpose: `validate` returning
     * `null` *is* the success case, and folding it together with a missing template turns every valid input
     * into an error.
     */
    fun validate(): MkDocsSiteTemplateError? {
        val template = buildTemplate() ?: return MkDocsSiteTemplateError.INVALID_ROOT
        return template.validate()
    }

    /** Sets the site name as if it had been typed. */
    @TestOnly
    internal fun setSiteNameForTest(name: String) {
        siteNameField.text = name
    }

    /** Sets the documentation directory as if it had been typed. */
    @TestOnly
    internal fun setDocsDirNameForTest(name: String) {
        docsDirField.text = name
    }

    /** Sets the assets directory as if it had been typed. */
    @TestOnly
    internal fun setAssetsDirNameForTest(name: String) {
        assetsDirField.text = name
    }

    /** Sets the output directory as if it had been typed. */
    @TestOnly
    internal fun setSiteDirNameForTest(name: String) {
        siteDirField.text = name
    }
}
