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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import java.io.File

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers what an icon of an installed theme looks like once it has been rendered: its size, and the colour it
 * is drawn in. The drawings carry no colour of their own, and a black glyph is what a dark IDE cannot show —
 * so the colour is asserted on the painted pixels rather than trusted.
 */
class MkDocsMaterialIconRendererTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            MkDocsMaterialIconRenderer.invalidate()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: an icon of the sets is painted in a completion popup. It has to arrive at the size the place
     * asked for, whatever canvas the SVG of the theme was drawn on.
     */
    fun `test renders an icon at the size that was asked for`() {
        val icon = MkDocsMaterialIconRenderer.render(svgFile(), 12)

        assertEquals(12, icon.iconWidth)
        assertEquals(12, icon.iconHeight)
    }

    /**
     * Use case: the same icon in a dark IDE. Every pixel of the glyph has to carry the colour the IDE writes
     * its text in — a glyph left in the black of the SVG would be invisible there.
     */
    fun `test paints the glyph in the foreground colour of the IDE`() {
        val icon = MkDocsMaterialIconRenderer.render(svgFile(), 16)
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }

        val foreground = UIUtil.getLabelForeground().rgb and 0x00FFFFFF
        val painted = (0 until image.width)
            .flatMap { x -> (0 until image.height).map { y -> image.getRGB(x, y) } }
            .filter { (it ushr 24) != 0 }
        assertFalse("the icon painted nothing at all", painted.isEmpty())
        assertTrue(
            "the glyph was not painted in the foreground colour of the IDE",
            painted.all { (it and 0x00FFFFFF) == foreground },
        )
    }

    /**
     * Writes an SVG of a filled square and returns it.
     *
     * A square rather than a real glyph: every pixel of it is part of the drawing, which is what lets the
     * colour be asserted without knowing where the theme put its strokes.
     */
    private fun svgFile(): VirtualFile {
        val directory = FileUtil.createTempDirectory("mkdocs-icon", null, true)
        val file = File(directory, "check.svg")
        file.writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>"""
        )
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            ?: error("cannot reach the icon at ${file.path}")
    }
}
