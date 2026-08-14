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
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.TestOnly
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureFlag
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialFeatureGroup
import javax.swing.JCheckBox

/**
 * The page holding `theme.features`, one tick per flag, grouped by the part of the page a flag changes.
 *
 * Two rules keep the selection consistent, and both only ever *prevent* a tick — neither silently changes what
 * the site declares:
 *
 * * a flag whose [MkDocsMaterialFeatureFlag.conflictsWith] names something currently ticked is disabled, with
 *   a tooltip naming the blocker. A flag that is itself ticked stays enabled and stays ticked, even when the
 *   file declares two flags that contradict each other — untangling that is the author's decision, not this
 *   page's;
 * * a flag whose [MkDocsMaterialFeatureFlag.requires] is unmet is disabled with a tooltip naming the
 *   prerequisite, rather than ticking the prerequisite behind the user's back. The one place the page does act
 *   on its own is the reverse direction: unticking a prerequisite unticks what depends on it, because the
 *   dependent would otherwise be left ticked and disabled with no way back.
 *
 * A flag the file declares that this plugin does not know — a newer version of the theme brings new ones — is
 * not shown here and survives untouched: [applyTo] adds it back to whatever the ticks say.
 */
class MkDocsMaterialFeaturesPage : MkDocsMaterialPageBase(ID, "material.page.features.title") {

    companion object {

        /** The identifier of this page. */
        const val ID: String = "material.features"
    }

    private val checkBoxes: Map<MkDocsMaterialFeatureFlag, JCheckBox> =
        MkDocsMaterialFeatureFlag.entries.associateWith { flag ->
            JCheckBox(flag.id).apply {
                addActionListener {
                    enforceRequirements()
                    updateAvailability()
                    fireChanged()
                }
            }
        }

    /** The flags of the file this plugin does not know, kept so applying does not drop them. */
    private var unknown: Set<String> = emptySet()

    override fun createContent(): DialogPanel = panel {
        for (group in MkDocsMaterialFeatureGroup.entries) {
            val flags = MkDocsMaterialFeatureFlag.entries.filter { it.group == group }
            if (flags.isEmpty()) continue
            group(MkDocsBundle.messageOrDefault(group.titleKey, group.name) ?: group.name) {
                for (flag in flags) {
                    row { cell(checkBoxes.getValue(flag)) }
                    row {
                        comment(
                            MkDocsBundle.messageOrDefault(flag.descriptionKey, flag.descriptionKey)
                                ?: flag.descriptionKey
                        )
                    }
                }
            }
        }
        row {
            comment(MkDocsBundle.message("material.page.features.hint"))
        }
    }.also { updateAvailability() }

    override fun reset(settings: MkDocsMaterialSettings) {
        unknown = settings.features.filter { MkDocsMaterialFeatureFlag.byId(it) == null }.toSet()
        checkBoxes.forEach { (flag, box) -> box.isSelected = flag.id in settings.features }
        updateAvailability()
    }

    override fun applyTo(settings: MkDocsMaterialSettings): MkDocsMaterialSettings =
        settings.copy(features = selectedFlags() + unknown)

    /** The identifiers currently ticked, without the unknown flags of the file. */
    fun selectedFlags(): Set<String> =
        checkBoxes.filterValues { it.isSelected }.keys.map { it.id }.toSet()

    /**
     * Unticks every flag whose prerequisite is no longer ticked, until nothing changes any more.
     *
     * A chain is possible — a flag requiring a flag that requires a third — so one pass is not enough.
     */
    private fun enforceRequirements() {
        do {
            var changed = false
            checkBoxes.forEach { (flag, box) ->
                if (box.isSelected && flag.requires.any { !isSelected(it) }) {
                    box.isSelected = false
                    changed = true
                }
            }
        } while (changed)
    }

    /**
     * Enables or disables every box in line with what is currently ticked, and explains each refusal.
     */
    private fun updateAvailability() {
        checkBoxes.forEach { (flag, box) ->
            if (box.isSelected) {
                box.isEnabled = true
                box.toolTipText = insidersHint(flag)
                return@forEach
            }
            val blocker = flag.conflicts().firstOrNull { isSelected(it.id) }
            val missing = flag.requires.firstOrNull { !isSelected(it) }
            box.isEnabled = blocker == null && missing == null
            box.toolTipText = when {
                blocker != null -> MkDocsBundle.message("material.page.features.conflict", blocker.id)
                missing != null -> MkDocsBundle.message("material.page.features.requires", missing)
                else -> insidersHint(flag)
            }
        }
    }

    /** The note that [flag] only works with the Insiders edition of the theme, or `null` if it works anywhere. */
    private fun insidersHint(flag: MkDocsMaterialFeatureFlag): String? =
        if (flag.insiders) MkDocsBundle.message("material.page.features.insiders") else null

    /** Tells whether the flag written as [id] is currently ticked. */
    private fun isSelected(id: String): Boolean =
        MkDocsMaterialFeatureFlag.byId(id)?.let { checkBoxes[it]?.isSelected } == true

    /**
     * Ticks or unticks [flag] as if the user had clicked its box.
     *
     * @param flag the flag to switch
     * @param selected `true` to switch it on
     */
    @TestOnly
    internal fun setSelectedForTest(flag: MkDocsMaterialFeatureFlag, selected: Boolean) {
        val box = checkBoxes.getValue(flag)
        if (box.isSelected == selected) return
        box.doClick()
    }

    /** The box of [flag], so a test can ask whether it is enabled and why not. */
    @TestOnly
    internal fun checkBoxForTest(flag: MkDocsMaterialFeatureFlag): JCheckBox = checkBoxes.getValue(flag)
}
