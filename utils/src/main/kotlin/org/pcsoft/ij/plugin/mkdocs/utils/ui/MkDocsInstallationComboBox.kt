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
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsInstallationLocator
import java.awt.BorderLayout
import java.io.File
import java.text.MessageFormat
import javax.swing.DefaultComboBoxModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The one field every settings page of a feature needs: where that feature is installed.
 *
 * The choice is made in two steps, because the two cases behind it are different ones. The fixed list on top
 * holds what [MkDocsInstallationLocator] found — the installations pip reports, the first of them as the
 * automatic entry naming it — plus one entry standing for a path of one's own. A directory pip reported
 * appears exactly once: as that automatic entry, never a second time below it. Only the last entry unlocks
 * the field below the list, where a directory is typed or picked;
 * as long as one of the found installations is chosen there is nothing to type, and a disabled field showing
 * the chosen directory says so more clearly than an editable one that must not be touched.
 *
 * Below both, one line states what is going on: which directory is actually being read, or — in red — what
 * is wrong with the one that was chosen. Naming the found installation is not the same statement as using
 * it, so what is in use is said outright rather than left to be inferred.
 *
 * What counts as a usable directory is not decided here: this class lives in the shared project and knows no
 * feature, so the caller hands in [validator], which answers with the message of its own resource bundle or
 * with `null` for a directory that is fine. The remaining texts are handed in for the same reason.
 *
 * @param project the project the settings belong to
 * @param distribution the name of the distribution as pip knows it, for example `mkdocs-material`
 * @param chooserTitle the title of the directory chooser
 * @param texts what the entries of the list and the line below the field read as
 * @param progressTitle what the progress of the search reads as
 * @param validator what is wrong with a directory chosen by hand, or `null` if nothing is
 */
class MkDocsInstallationComboBox(
    private val project: Project,
    private val distribution: String,
    private val chooserTitle: String,
    private val texts: Texts,
    private val progressTitle: String,
    private val validator: (String) -> String?,
) : JPanel(BorderLayout()) {

    /** The installations that were found, the first of which the automatic entry stands for. */
    private var detected: List<String> = emptyList()

    /** The fixed list of what was found, plus the entry standing for a path of one's own. */
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
                text = when (value) {
                    CUSTOM -> texts.custom
                    AUTOMATIC, null -> automaticText()
                    else -> value
                }
            }
        }
    }

    /** The directory chosen by hand, enabled only while the list stands on the entry for it. */
    private val customPath = TextFieldWithBrowseButton().apply {
        isEnabled = false
        border = JBUI.Borders.emptyTop(4)
        addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(chooserTitle)
            FileChooser.chooseFile(descriptor, project, null)?.let { text = it.presentableUrl }
        }
    }

    /** The line stating which directory is read, or what is wrong with the chosen one. */
    private val message = JBLabel().apply {
        border = JBUI.Borders.emptyTop(4)
    }

    init {
        add(comboBox, BorderLayout.NORTH)
        add(customPath, BorderLayout.CENTER)
        add(message, BorderLayout.SOUTH)
        comboBox.addActionListener {
            customPath.isEnabled = isCustom()
            if (!isCustom()) customPath.text = selectedInstallation()
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
     * The directory that was chosen, or an empty string for the automatic answer.
     *
     * Setting it puts the list on the matching entry: nothing on the automatic one, a further installation
     * that was found on that one, anything else on the entry for a path of one's own. The field below always
     * shows the directory that is in effect, disabled though it may be — a settings page being opened has to
     * show what it stands on, not an empty field next to a chosen entry.
     */
    var path: String
        get() = if (isCustom()) customPath.text.trim() else selectedInstallation()
        set(value) {
            val wanted = value.trim()
            comboBox.selectedItem = when {
                wanted.isEmpty() -> AUTOMATIC
                wanted in alternatives() -> wanted
                else -> CUSTOM
            }
            customPath.isEnabled = isCustom()
            customPath.text = if (wanted.isEmpty()) detected.firstOrNull().orEmpty() else wanted
            update()
        }

    /**
     * What is wrong with the directory that was chosen, or `null` if nothing is.
     *
     * What a settings page refuses to apply on. Only a path chosen by hand is judged: an installation out of
     * the list was found by looking for exactly what makes it one.
     */
    val errorText: String?
        get() = if (isCustom()) validator(customPath.text.trim()) else null

    /**
     * Fills the list with the installations that can be found, keeping what was chosen.
     *
     * The search asks pip, which starts a process, so it must not run on the EDT — a modal progress carries
     * it, which is what a settings page being opened or applied can afford.
     */
    fun reloadCandidates() {
        val current = path
        detected = runWithModalProgressBlocking(project, progressTitle) {
            MkDocsInstallationLocator.detectLocations(distribution)
        }
        comboBox.model = DefaultComboBoxModel((listOf(AUTOMATIC) + alternatives() + CUSTOM).toTypedArray())
        path = current
    }

    /**
     * Returns the found installations that get an entry of their own.
     *
     * Every one but the first: that one is what the automatic entry stands for and names, and listing it
     * again below itself would offer the same directory twice under two names.
     */
    private fun alternatives(): List<String> = detected.drop(1)

    /**
     * Returns whether the list stands on the entry for a path of one's own.
     */
    private fun isCustom(): Boolean = comboBox.selectedItem == CUSTOM

    /**
     * Returns the installation the list stands on, empty for the automatic entry.
     */
    private fun selectedInstallation(): String =
        (comboBox.selectedItem as? String)?.takeIf { it != AUTOMATIC && it != CUSTOM }.orEmpty()

    /**
     * Returns what the automatic entry reads as: the directory that was found, or that there is none.
     */
    private fun automaticText(): String = texts.automaticFor(detected.firstOrNull())

    /**
     * Writes the line below the fields: what is wrong, or which directory is read.
     *
     * Whether a configured directory exists is the only thing decided here; which of the two wins is the
     * rule of [Texts.inUseFor], and it is the rule the locator follows.
     */
    private fun update() {
        val error = errorText
        if (error != null) {
            message.foreground = JBColor.RED
            message.text = error
            return
        }
        message.foreground = UIUtil.getContextHelpForeground()
        val chosen = path
        message.text = texts.inUseFor(chosen.takeIf { it.isNotEmpty() && File(it).isDirectory }, detected.firstOrNull())
    }

    /**
     * The texts of one feature, in the wording of its own resource bundle.
     *
     * @property automatic the automatic entry naming what was found, with `{0}` for the directory
     * @property automaticNone the automatic entry when nothing was found
     * @property custom the entry standing for a path of one's own
     * @property inUse the line below the fields, with `{0}` for the directory that is read
     * @property inUseNone the line below the fields when nothing is read at all
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
         * @param detected the directory that was found, or `null` if there is none
         */
        fun automaticFor(detected: String?): String =
            detected?.let { MessageFormat.format(automatic, it) } ?: automaticNone

        /**
         * Returns what the line below the fields reads as.
         *
         * A configured directory wins over the one that was found, which is the rule the locator follows —
         * and a configured directory that does not exist is not one, so the caller hands `null` for it.
         *
         * @param configured the directory of the settings, if there is one and it exists
         * @param detected the directory that was found, or `null` if there is none
         */
        fun inUseFor(configured: String?, detected: String?): String =
            (configured ?: detected)?.let { MessageFormat.format(inUse, it) } ?: inUseNone
    }

    private companion object {

        /** The item of the entry standing for the installation pip reports. */
        private const val AUTOMATIC = ""

        /** The item of the entry standing for a path of one's own; no directory can carry this name. */
        private const val CUSTOM = " custom"
    }
}
