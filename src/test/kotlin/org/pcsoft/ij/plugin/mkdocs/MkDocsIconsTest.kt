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

package org.pcsoft.ij.plugin.mkdocs

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.Icon

/**
 * Developer tests for [MkDocsIcons].
 *
 * The icon files are all drawn on a 48x48 canvas, so an icon that is not brought to its target size on
 * loading comes out four times too large in the tree. These tests pin the size of every registered icon.
 */
class MkDocsIconsTest : BasePlatformTestCase() {

    /**
     * Verifies that every icon rendered in place of a regular node icon is 16x16, the size the project view,
     * the navigation tree and the facet all render.
     */
    fun testRegularIconsAreSixteenPixels() {
        val regular = mapOf(
            "MkDocs" to MkDocsIcons.MkDocs,
            "Badge" to MkDocsIcons.Badge,
            "DocsBadge" to MkDocsIcons.DocsBadge,
            "AssetsBadge" to MkDocsIcons.AssetsBadge,
            "MarkdownFile" to MkDocsIcons.MarkdownFile,
            "NavSection" to MkDocsIcons.NavSection,
            "RequirementsFile" to MkDocsIcons.RequirementsFile,
            "ConfigFile" to MkDocsIcons.ConfigFile,
            "StylesheetsBadge" to MkDocsIcons.StylesheetsBadge,
            "StylesheetFile" to MkDocsIcons.StylesheetFile,
        )
        regular.forEach { (name, icon) -> assertSquareSize(name, icon, 16) }
    }

    /**
     * Verifies that the four markers overlaid onto a folder icon are 8x8, so they take up a quarter of the
     * icon they are laid over instead of covering it completely.
     */
    fun testOverlayIconsAreEightPixels() {
        val overlays = mapOf(
            "BadgeOverlay" to MkDocsIcons.BadgeOverlay,
            "DocsBadgeOverlay" to MkDocsIcons.DocsBadgeOverlay,
            "AssetsBadgeOverlay" to MkDocsIcons.AssetsBadgeOverlay,
            "StylesheetsBadgeOverlay" to MkDocsIcons.StylesheetsBadgeOverlay,
        )
        overlays.forEach { (name, icon) -> assertSquareSize(name, icon, 8) }
    }

    /**
     * Verifies that the icon of the *Site Page* tool window is 20x20, which is the size the tool window
     * stripe of the current user interface renders.
     */
    fun testToolWindowIconIsTwentyPixels() {
        assertSquareSize("SitePageToolWindow", MkDocsIcons.SitePageToolWindow, 20)
    }

    private fun assertSquareSize(name: String, icon: Icon, expected: Int) {
        assertEquals("width of $name", expected, icon.iconWidth)
        assertEquals("height of $name", expected, icon.iconHeight)
    }
}
