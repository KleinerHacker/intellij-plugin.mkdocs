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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.MkDocsIcons

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the gutter icons next to the path values of a configuration file: that a value pointing somewhere
 * gets one, that a value pointing nowhere gets none, and that the two directory keys are told apart by their
 * own badges.
 */
class MkDocsPathLineMarkerProviderTest : BasePlatformTestCase() {

    /**
     * Use case: the `docs_dir` value of a site. A folder icon would say nothing next to it, so the marker
     * carries the badge of the documentation directory and opens that directory.
     */
    fun `test marks docs dir with its own badge`() {
        site()
        val marker = singleMarker("site_name: Handbook\ndocs_dir: docs\n")

        assertSame(MkDocsIcons.DocsBadge, marker.icon)
        assertEquals("docs", targetNameOf(marker))
    }

    /**
     * Use case: the `site_dir` value of a site that has been built once. It gets a badge of its own, so the
     * two directory keys of the file cannot be confused at a glance.
     */
    fun `test marks site dir with its own badge`() {
        site()
        myFixture.addFileToProject("site/index.html", "<html></html>")
        val marker = singleMarker("site_name: Handbook\nsite_dir: site\n")

        assertSame(MkDocsIcons.SiteDirBadge, marker.icon)
        assertEquals("site", targetNameOf(marker))
    }

    /**
     * Use case: a page of the navigation. Clicking the marker opens the page, which is the whole point of
     * drawing it next to a navigation entry.
     */
    fun `test marks a nav entry with the page as its target`() {
        site()
        val marker = singleMarker("nav:\n  - Home: index.md\n")

        assertNotNull("the marker has to be drawn with an icon", marker.icon)
        assertEquals("index.md", targetNameOf(marker))
    }

    /**
     * Use case: a style sheet listed in `extra_css`. It is a file the built site loads and it opens from the
     * gutter like any other target.
     */
    fun `test marks an entry of extra css`() {
        site()
        myFixture.addFileToProject("docs/stylesheets/extra.css", "body { color: red; }\n")
        val marker = singleMarker("extra_css:\n  - stylesheets/extra.css\n")

        assertEquals("extra.css", targetNameOf(marker))
    }

    /**
     * Use case: the logo of the theme, an ordinary image file. The marker leaves the icon to the platform and
     * opens the image.
     */
    fun `test marks the logo of the theme`() {
        site()
        myFixture.addFileToProject("docs/img/logo.png", "")
        val marker = singleMarker("theme:\n  name: material\n  logo: img/logo.png\n")

        assertEquals("logo.png", targetNameOf(marker))
    }

    /**
     * Use case: a navigation entry whose page was renamed away. The absence of the marker is what tells the
     * reader that the entry leads nowhere, so no icon may be drawn.
     */
    fun `test draws no marker for a page pointing nowhere`() {
        site()
        myFixture.configureByText("mkdocs.yml", "nav:\n  - missing.md\n")

        assertEmpty(markers())
    }

    /**
     * Use case: a site that has never been built. `site_dir` is not marked red — but there is nothing to open
     * either, so it gets no marker.
     */
    fun `test draws no marker for a missing site dir`() {
        site()
        myFixture.configureByText("mkdocs.yml", "site_name: Handbook\nsite_dir: site\n")

        assertEmpty(markers())
    }

    /**
     * Use case: an ordinary text value such as the site name, and an external navigation target. Neither is a
     * path, so neither may be decorated.
     */
    fun `test draws no marker for values that are no paths`() {
        site()
        myFixture.configureByText(
            "mkdocs.yml",
            "site_name: Handbook\nnav:\n  - Upstream: https://www.mkdocs.org/\n",
        )

        assertEmpty(markers())
    }

    /**
     * Use case: a navigation holding two pages. Each value gets exactly one marker — every leaf of a scalar
     * contributing its own icon would stack them on the same line.
     */
    fun `test draws exactly one marker per value`() {
        site()
        myFixture.addFileToProject("docs/guide/tuning.md", "# Tuning\n")
        myFixture.configureByText("mkdocs.yml", "nav:\n  - Home: index.md\n  - Tuning: guide/tuning.md\n")

        assertEquals(2, markers().size)
    }

    /** Creates the documentation directory of the site the configuration file is written into. */
    private fun site(): PsiFile = myFixture.addFileToProject("docs/index.md", "# Handbook\n")

    /** Configures a configuration file with [text] and returns the single marker of the plugin in it. */
    private fun singleMarker(text: String): GutterMark {
        myFixture.configureByText("mkdocs.yml", text)
        val markers = markers()

        assertEquals("markers of the plugin", 1, markers.size)
        return markers.first()
    }

    /** Returns the markers the plugin contributed to the configured file. */
    private fun markers(): List<GutterMark> =
        myFixture.findAllGutters().filter { it.tooltipText?.contains("Open the") == true }

    /** Returns the name of the file or directory [marker] navigates to. */
    private fun targetNameOf(marker: GutterMark): String? {
        val info = (marker as LineMarkerInfo.LineMarkerGutterIconRenderer<*>).lineMarkerInfo
        val targets = (info as RelatedItemLineMarkerInfo<*>).createGotoRelatedItems()

        assertEquals("targets of the marker", 1, targets.size)
        return (targets.first().element as? PsiFileSystemItem)?.name
    }
}
