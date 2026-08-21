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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.openapi.util.ScalableIcon
import org.junit.Assert.*
import org.junit.Test
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsIconLoaderTest {

    /**
     * A loaded icon reports the size it was asked for and keeps it when it is scaled by one, which is what
     * the places rendering it rely on — the file behind it is drawn on a 48 unit canvas.
     */
    @Test
    fun `loaded icon implements ScalableIcon`() {
        val icon = MkDocsIconLoader.Logo
        assertTrue("Icon must implement ScalableIcon", icon is ScalableIcon)
        assertEquals(16, icon.iconWidth)
        assertEquals(16, icon.iconHeight)

        val scalable = icon as ScalableIcon
        val scaled = scalable.scale(1.0f)
        assertEquals(16, scaled.iconWidth)
        assertEquals(16, scaled.iconHeight)
        assertTrue(scaled is ScalableIcon)
    }

    /**
     * Scaling a loaded icon by a factor other than one answers with an icon of the multiplied size, so a
     * place asking for a bigger drawing gets one instead of the canvas size of the file.
     */
    @Test
    fun `scaling a loaded icon multiplies its size`() {
        val scaled = (MkDocsIconLoader.Logo as ScalableIcon).scale(2.0f)

        assertEquals(32, scaled.iconWidth)
        assertEquals(32, scaled.iconHeight)
        assertTrue(scaled is ScalableIcon)
    }

    /**
     * An icon that does not come off the class path — a drawing out of the user's environment — is brought to
     * the size of the place painting it, whatever size it was drawn at.
     */
    @Test
    fun `fixSize brings a foreign icon to the requested size`() {
        val fixed = MkDocsIconLoader.fixSize(SquareIcon(64), 12)

        assertEquals(12, fixed.iconWidth)
        assertEquals(12, fixed.iconHeight)
        assertTrue(fixed is ScalableIcon)
    }

    /**
     * A plain icon of a known edge length, standing for a drawing that does not come off the class path.
     *
     * @property edge the edge length in pixels
     */
    private class SquareIcon(private val edge: Int) : Icon {

        override fun paintIcon(component: Component?, graphics: Graphics?, x: Int, y: Int) = Unit

        override fun getIconWidth(): Int = edge

        override fun getIconHeight(): Int = edge
    }
}
