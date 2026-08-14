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

package org.pcsoft.ij.plugin.mkdocs.material.override

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Asks which templates of the theme are to be overridden, and where they go.
 *
 * The directory is asked for rather than fixed, because a site may already have one under a name of its own —
 * the action fills in what `theme.custom_dir` says, and only writes the key when the file does not carry it
 * yet.
 *
 * @param project the project the site belongs to
 * @param directoryName the name the override directory should carry
 */
class MkDocsMaterialCreateOverrideDialog(
    project: Project,
    directoryName: String,
) : DialogWrapper(project) {

    private val directory = JBTextField(directoryName)

    private val boxes = MkDocsMaterialOverride.entries.associateWith {
        JCheckBox(MkDocsBundle.message(it.titleKey))
    }

    init {
        title = MkDocsBundle.message("material.override.title")
        boxes[MkDocsMaterialOverride.MAIN]?.isSelected = true
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(MkDocsBundle.message("material.override.field.directory")) {
            cell(directory).align(Align.FILL)
        }
        boxes.forEach { (override, box) ->
            row {
                cell(box)
                comment(override.path)
            }
        }
        row {
            comment(MkDocsBundle.message("material.override.hint"))
        }
    }

    override fun doValidate(): ValidationInfo? {
        val name = directoryName()
        if (name.isEmpty() || '/' in name || '\\' in name) {
            return ValidationInfo(MkDocsBundle.message("material.override.error.directory"), directory)
        }
        if (selectedOverrides().isEmpty()) {
            return ValidationInfo(MkDocsBundle.message("material.override.error.empty"))
        }
        return null
    }

    /**
     * Returns the name of the override directory as it was entered.
     */
    fun directoryName(): String = directory.text.trim()

    /**
     * Returns the templates that were ticked, in declaration order.
     */
    fun selectedOverrides(): List<MkDocsMaterialOverride> =
        boxes.filterValues { it.isSelected }.keys.toList()

    /**
     * Ticks [override], so a test can drive the dialog without a screen.
     *
     * @param override the template to tick
     * @param value `true` to tick it
     */
    @TestOnly
    internal fun setSelected(override: MkDocsMaterialOverride, value: Boolean) {
        boxes[override]?.isSelected = value
    }
}
