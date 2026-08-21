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

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * The frame every Material settings page is shown in.
 *
 * The same page appears in two very different places — as a step of the site creation wizard, and as a tab of
 * the Project Structure dialog — and in neither of them does the surrounding dialog say who the page belongs
 * to. The header row does: the Material icon and the name of the theme above the content, so a user meeting
 * the page in either host recognises what is being configured.
 *
 * The content is scrollable. The features page lists over thirty flags with a description each, which no
 * dialog height accommodates, and a wizard step cannot resize itself past the pages next to it.
 *
 * @param content the page itself
 */
class MkDocsMaterialPagePanel(content: JComponent) : JPanel(BorderLayout()) {

    init {
        border = JBUI.Borders.empty(8)
        add(header(), BorderLayout.NORTH)
        add(
            JBScrollPane(content).apply {
                border = JBUI.Borders.emptyTop(8)
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            },
            BorderLayout.CENTER,
        )
    }

    /** The row naming the theme the page configures. */
    private fun header(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
        isOpaque = false
        add(
            JBLabel(
                MkDocsMaterialBundle.message("material.page.header"),
                MkDocsMaterialIcons.Feature,
                JBLabel.LEFT
            ).apply {
                font = JBFont.label().asBold()
            }
        )
    }
}
