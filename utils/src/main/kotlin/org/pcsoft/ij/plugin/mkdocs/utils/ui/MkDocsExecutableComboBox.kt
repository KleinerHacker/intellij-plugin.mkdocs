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

package org.pcsoft.ij.plugin.mkdocs.utils.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsTool
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsToolInfo
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsToolService
import java.awt.BorderLayout
import java.text.MessageFormat
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The field naming one of the three programs an MkDocs site is built with.
 *
 * The sibling of [MkDocsInstallationComboBox], and deliberately built the same way: a fixed list on top, one
 * entry standing for what the search found and one for a program of one's own, the field below it enabled
 * only for the second, and one line stating what is actually in use. What a user learned on the settings page
 * of a feature holds here as well.
 *
 * What is different is what is being named. A feature is a directory, and a directory that exists is as far
 * as a check can get. A program is run, so the automatic entry does not merely name a path but the version
 * that path answered with — which is the difference between "there is a file called mkdocs" and "MkDocs 1.6.1
 * is installed". While a program of one's own is typed only the file system is asked, because a field may not
 * start a process per keystroke; that path is run when the page is opened, applied, or the search is asked to
 * run again through [reloadCandidates].
 *
 * The texts and what counts as a usable file are handed in: this class lives in the shared project and knows
 * neither the wording of a page nor which program it stands for beyond [tool].
 *
 * @param project the project the settings belong to
 * @param tool the program this field names
 * @param chooserTitle the title of the file chooser
 * @param texts what the entries of the list and the line below the field read as
 * @param progressTitle what the progress of the search reads as
 * @param validator what is wrong with a program chosen by hand, or `null` if nothing is
 */
class MkDocsExecutableComboBox(
    private val project: Project,
    private val tool: MkDocsTool,
    chooserTitle: String,
    private val texts: Texts,
    private val progressTitle: String,
    private val validator: (String) -> String?,
) : JPanel(BorderLayout()) {

    /** What the search found, or `null` if it found nothing — what the automatic entry stands for. */
    private var detected: MkDocsToolInfo? = null

    /** The fixed list: what was found, and the entry standing for a program of one's own. */
    private val comboBox = ComboBox(DefaultComboBoxModel(arrayOf(AUTOMATIC, CUSTOM))).apply {
        isEditable = false
        renderer = object : SimpleListCellRenderer<String>() {
            override fun customize(
                list: JList<out String>,
                value: String?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = if (value == CUSTOM) texts.custom else texts.automaticFor(detected)
            }
        }
    }

    /** The program chosen by hand, enabled only while the list stands on the entry for it. */
    private val customPath = TextFieldWithBrowseButton().apply {
        isEnabled = false
        border = JBUI.Borders.emptyTop(4)
        addActionListener {
            val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(chooserTitle)
            FileChooser.chooseFile(descriptor, project, null)?.let { text = it.presentableUrl }
        }
    }

    /** The line stating which program is run, or what is wrong with the chosen one. */
    private val message = JBLabel().apply {
        border = JBUI.Borders.emptyTop(4)
    }

    init {
        add(comboBox, BorderLayout.NORTH)
        add(customPath, BorderLayout.CENTER)
        add(message, BorderLayout.SOUTH)
        comboBox.addActionListener {
            customPath.isEnabled = isCustom()
            if (!isCustom()) customPath.text = detected?.executable.orEmpty()
            update()
        }
        customPath.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = update()
            override fun removeUpdate(event: DocumentEvent) = update()
            override fun changedUpdate(event: DocumentEvent) = update()
        })
        update()
    }

    /**
     * The program that was chosen, or an empty string for the automatic answer.
     *
     * Setting it puts the list on the matching entry, and the field below always shows the program that is in
     * effect, disabled though it may be — a settings page being opened has to show what it stands on rather
     * than an empty field next to a chosen entry.
     */
    var path: String
        get() = if (isCustom()) customPath.text.trim() else ""
        set(value) {
            val wanted = value.trim()
            comboBox.selectedItem = if (wanted.isEmpty()) AUTOMATIC else CUSTOM
            customPath.isEnabled = isCustom()
            customPath.text = wanted.ifEmpty { detected?.executable.orEmpty() }
            update()
        }

    /**
     * What is wrong with the program that was chosen, or `null` if nothing is.
     *
     * What a settings page refuses to apply on. Only a program chosen by hand is judged: what the search
     * found answered `--version` as the program it was looked for as.
     */
    val errorText: String?
        get() = if (isCustom()) validator(customPath.text.trim()) else null

    /**
     * Runs the search again, keeping what was chosen.
     *
     * The search starts a process, so it must not run on the EDT — a modal progress carries it, which is what
     * a settings page being opened or applied can afford.
     */
    fun reloadCandidates() {
        val current = path
        detected = runWithModalProgressBlocking(project, progressTitle) {
            service<MkDocsToolService>().detectAutomatic(tool, project)
        }
        path = current
    }

    /**
     * Returns whether the list stands on the entry for a program of one's own.
     */
    private fun isCustom(): Boolean = comboBox.selectedItem == CUSTOM

    /**
     * Writes the line below the fields: what is wrong, or which program is run.
     */
    private fun update() {
        val error = errorText
        if (error != null) {
            message.foreground = JBColor.RED
            message.text = error
            return
        }
        message.foreground = UIUtil.getContextHelpForeground()
        message.text = texts.inUseFor(path.takeIf { it.isNotEmpty() }, detected)
        comboBox.repaint()
    }

    /**
     * The texts of one program, in the wording of the bundle of the page showing it.
     *
     * @property automatic the automatic entry, with `{0}` for the program and `{1}` for its version
     * @property automaticNone the automatic entry when nothing was found
     * @property custom the entry standing for a program of one's own
     * @property inUse the line below the fields, with `{0}` for the program that is run
     * @property inUseNone the line below the fields when no program is run at all
     */
    data class Texts(
        val automatic: String,
        val automaticNone: String,
        val custom: String,
        val inUse: String,
        val inUseNone: String,
    ) {

        /**
         * Returns what the automatic entry reads as.
         *
         * The version is part of it rather than an addition to it: what makes a find one is that the program
         * answered as itself, and the version is that answer.
         *
         * @param detected what the search found, or `null` if it found nothing
         */
        fun automaticFor(detected: MkDocsToolInfo?): String =
            detected?.let { MessageFormat.format(automatic, it.executable, it.version) } ?: automaticNone

        /**
         * Returns what the line below the fields reads as.
         *
         * A configured program wins over the one that was found, which is the rule [MkDocsToolService]
         * follows, and the line has to report the same winner.
         *
         * @param configured the program of the settings, if one is configured
         * @param detected what the search found, or `null` if it found nothing
         */
        fun inUseFor(configured: String?, detected: MkDocsToolInfo?): String =
            (configured ?: detected?.executable)?.let { MessageFormat.format(inUse, it) } ?: inUseNone
    }

    private companion object {

        /** The item of the entry standing for what the search found. */
        private const val AUTOMATIC = ""

        /** The item of the entry standing for a program of one's own; no path can carry this name. */
        private const val CUSTOM = " custom"
    }
}
