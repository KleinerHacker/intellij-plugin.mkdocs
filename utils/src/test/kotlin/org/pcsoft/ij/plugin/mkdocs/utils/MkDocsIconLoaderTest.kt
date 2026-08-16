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

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsIconLoaderTest {

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
}
