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

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialSettings
import org.pcsoft.ij.plugin.mkdocs.material.data.flag

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the features page: what it gives back, how it keeps a selection consistent, and what it does with a
 * flag it has never heard of.
 */
class MkDocsMaterialFeaturesPageTest : BasePlatformTestCase() {

    /**
     * Use case: a site listing two flags is opened and applied untouched. The two flags have to come back
     * unchanged, in whatever order — `theme.features` is a set here.
     */
    fun `test gives back unchanged what it was filled with`() {
        val page = MkDocsMaterialFeaturesPage()
        val settings = MkDocsMaterialSettings(features = setOf("navigation.tabs", "toc.follow"))

        page.reset(settings)

        assertEquals(settings, page.applyTo(settings))
        assertFalse(page.isModified(settings))
    }

    /**
     * Use case: the user ticks a flag. It has to appear in the snapshot, and the page has to report itself as
     * modified so the dialog offers to apply.
     */
    fun `test carries a ticked flag into the snapshot`() {
        val page = MkDocsMaterialFeaturesPage()
        page.reset(MkDocsMaterialSettings.EMPTY)

        page.setSelectedForTest(flag("navigation.tabs"), true)

        assertTrue(page.isModified(MkDocsMaterialSettings.EMPTY))
        assertEquals(setOf("navigation.tabs"), page.applyTo(MkDocsMaterialSettings.EMPTY).features)
    }

    /**
     * Use case: the user ticks a flag that cannot be combined with another one. The other one has to become
     * unavailable, with a tooltip naming the blocker — a site declaring both renders neither as intended.
     */
    fun `test disables a flag conflicting with the current selection`() {
        val page = MkDocsMaterialFeaturesPage()
        page.reset(MkDocsMaterialSettings.EMPTY)

        page.setSelectedForTest(flag("navigation.expand"), true)

        val blocked = page.checkBoxForTest(flag("navigation.prune"))
        assertFalse("the conflicting flag must be unavailable", blocked.isEnabled)
        assertTrue("the tooltip names the blocker", blocked.toolTipText!!.contains("navigation.expand"))

        page.setSelectedForTest(flag("navigation.expand"), false)
        assertTrue("and is available again once the blocker is gone", blocked.isEnabled)
    }

    /**
     * Use case: a flag depends on another one. It stays unavailable until its prerequisite is ticked, and it
     * is unticked again when the prerequisite goes — a flag left ticked and disabled could never be undone.
     */
    fun `test ties a flag to its prerequisite`() {
        val page = MkDocsMaterialFeaturesPage()
        page.reset(MkDocsMaterialSettings.EMPTY)

        val dependent = page.checkBoxForTest(flag("navigation.instant.progress"))
        assertFalse("unavailable without its prerequisite", dependent.isEnabled)
        assertTrue(dependent.toolTipText!!.contains("navigation.instant"))

        page.setSelectedForTest(flag("navigation.instant"), true)
        assertTrue(dependent.isEnabled)
        page.setSelectedForTest(flag("navigation.instant.progress"), true)

        page.setSelectedForTest(flag("navigation.instant"), false)
        assertFalse("the dependent goes with its prerequisite", dependent.isSelected)
        assertEquals(emptySet<String>(), page.applyTo(MkDocsMaterialSettings.EMPTY).features)
    }

    /**
     * Use case: the site declares a flag a newer version of the theme brought, which this plugin does not
     * know. It is not shown, and applying the page must not drop it.
     */
    fun `test keeps a flag it does not know`() {
        val page = MkDocsMaterialFeaturesPage()
        val settings = MkDocsMaterialSettings(features = setOf("navigation.tabs", "future.flag"))
        page.reset(settings)

        page.setSelectedForTest(flag("toc.follow"), true)

        assertEquals(
            setOf("navigation.tabs", "toc.follow", "future.flag"),
            page.applyTo(settings).features,
        )
    }

    /**
     * Use case: the file itself declares two flags that contradict each other. The page shows both as ticked
     * and neither as blocked — sorting that out is the author's decision, and a page must not quietly untick
     * what the file states.
     */
    fun `test leaves a contradiction of the file as it is`() {
        val page = MkDocsMaterialFeaturesPage()
        val settings = MkDocsMaterialSettings(features = setOf("navigation.expand", "navigation.prune"))

        page.reset(settings)

        assertTrue(page.checkBoxForTest(flag("navigation.expand")).isSelected)
        assertTrue(page.checkBoxForTest(flag("navigation.prune")).isSelected)
        assertEquals(settings, page.applyTo(settings))
    }
}
