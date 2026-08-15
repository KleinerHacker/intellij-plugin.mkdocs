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
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import javax.swing.table.AbstractTableModel

/**
 * The page listing the Markdown extensions, what each of them is worth to this site, and whether it is on.
 *
 * Nothing is required by the theme as such — it renders a plain site without a single extension. An extension
 * becomes *required* only once something asks for it, which here means a flag ticked on the features page: the
 * status column is computed against the current selection of that page, so ticking `content.code.annotate`
 * turns `pymdownx.superfences` from a recommendation into a requirement while both pages are open.
 *
 * Enabling happens in the row itself, through the tick in the last column, rather than through a button next
 * to the table — the state is a yes or no per row, and a checkbox says that with no explanation needed.
 *
 * An extension the file configures with options of its own is kept: this model only ever sees the identifier,
 * and [MkDocsMaterialSettings.extensions] carries it back untouched.
 *
 * @param features the identifiers currently ticked on the features page
 */
class MkDocsMaterialExtensionsPage(
    private val features: () -> Set<String> = { emptySet() },
) : MkDocsMaterialPageBase(ID, "material.page.extensions.title") {

    companion object {

        /** The identifier of this page. */
        const val ID: String = "material.extensions"

        /** The column holding the identifier of the extension. */
        private const val COLUMN_ID: Int = 0

        /** The column holding what the extension is worth to this site. */
        private const val COLUMN_STATUS: Int = 1

        /** The column holding whether the extension is listed in the configuration file. */
        private const val COLUMN_ENABLED: Int = 2
    }

    /**
     * What an extension is worth to the site as it is currently configured.
     *
     * @property titleKey the bundle key of the label shown in the status column
     */
    enum class Status(val titleKey: String) {

        /** Something in the configuration forces the extension; without it that something does not render. */
        REQUIRED("material.page.extensions.status.required"),

        /** Nothing forces the extension, but the theme builds on it wherever it is there. */
        RECOMMENDED("material.page.extensions.status.recommended"),

        /** Neither forced nor recommended — the extension merely widens what an author can write. */
        OPTIONAL("material.page.extensions.status.off"),
    }

    /** The extensions the site lists, as identifiers, including the ones this plugin does not know. */
    private var enabled: MutableSet<String> = mutableSetOf()

    private val model = ExtensionTableModel()

    private val table = JBTable(model).apply {
        setShowGrid(false)
        rowSelectionAllowed = true
        preferredScrollableViewportSize = JBUI.size(560, 320)
    }

    override fun createContent(): DialogPanel = panel {
        row {
            cell(JBScrollPane(table)).align(Align.FILL)
        }.resizableRow()
        row {
            comment(MkDocsBundle.message("material.page.extensions.hint"))
        }
    }

    override fun reset(settings: MkDocsMaterialSettings) {
        enabled = settings.extensions.toMutableSet()
        model.fireTableDataChanged()
    }

    override fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings =
        settings.copy(extensions = enabled.toSet())

    /**
     * Tells the page that the feature selection changed, so the status column follows it.
     */
    fun refresh() {
        model.fireTableDataChanged()
    }

    /**
     * The extensions the current feature selection forces.
     *
     * Icon shorthands are not counted here: whether the pages of the site write any is a question about their
     * content, which the annotator answers on the file itself and a settings page cannot.
     */
    fun requiredExtensions(): Set<MkDocsMarkdownExtension> =
        MkDocsMarkdownExtension.requiredBy(features(), false)

    /**
     * What [extension] is worth to the site as it is currently configured.
     *
     * @param extension the extension to judge
     */
    fun statusOf(extension: MkDocsMarkdownExtension): Status = when {
        extension in requiredExtensions() -> Status.REQUIRED
        extension.level == MkDocsMarkdownExtension.Level.RECOMMENDED -> Status.RECOMMENDED
        else -> Status.OPTIONAL
    }

    /**
     * Tells whether [extension] is currently listed.
     *
     * @param extension the extension to ask about
     */
    fun isEnabled(extension: MkDocsMarkdownExtension): Boolean = extension.id in enabled

    /**
     * Adds [extension] to the list of the site, or takes it out again.
     *
     * @param extension the extension to switch
     * @param value `true` to list it
     */
    fun setEnabled(extension: MkDocsMarkdownExtension, value: Boolean) {
        val changed = if (value) enabled.add(extension.id) else enabled.remove(extension.id)
        if (!changed) return
        model.fireTableDataChanged()
        fireChanged()
    }

    /** The table of the page, so a test can check what the columns say. */
    @TestOnly
    internal fun tableForTest(): JBTable = table

    /**
     * The rows of the table: one per extension the plugin knows, in declaration order.
     */
    private inner class ExtensionTableModel : AbstractTableModel() {

        override fun getRowCount(): Int = MkDocsMarkdownExtension.entries.size

        override fun getColumnCount(): Int = 3

        override fun getColumnName(column: Int): String = when (column) {
            COLUMN_ID -> MkDocsBundle.message("material.page.extensions.column.extension")
            COLUMN_STATUS -> MkDocsBundle.message("material.page.extensions.column.status")
            else -> MkDocsBundle.message("material.page.extensions.column.enabled")
        }

        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == COLUMN_ENABLED) Boolean::class.javaObjectType else String::class.java

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == COLUMN_ENABLED

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val extension = MkDocsMarkdownExtension.entries[rowIndex]
            return when (columnIndex) {
                COLUMN_ID -> extension.id
                COLUMN_STATUS -> statusOf(extension).let {
                    MkDocsBundle.messageOrDefault(it.titleKey, it.name) ?: it.name
                }

                else -> isEnabled(extension)
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex != COLUMN_ENABLED) return
            setEnabled(MkDocsMarkdownExtension.entries[rowIndex], aValue == true)
        }
    }
}
