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

package org.pcsoft.ij.plugin.mkdocs.module.facet.material

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsConfig

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the persistent state of the Angular Material facet, which is what carries the detected theme name
 * through an IDE restart.
 */
class MkDocsMaterialFacetConfigurationTest {

    /**
     * Use case: the facet is created by the Project Structure dialog rather than by the detection, so nobody
     * fills the state. The theme it stands for is the Material theme either way, which is why the default
     * has to be exactly the name MkDocs knows it under — an empty default would be written into `mkdocs.yml`
     * as an empty theme.
     */
    @Test
    fun `starts with the material theme`() {
        val configuration = MkDocsMaterialFacetConfiguration()

        assertEquals(MkDocsConfig.THEME_MATERIAL, configuration.themeName)
    }

    /**
     * Use case: the detection fills the state, the module store writes it into the `.iml` and reads it back
     * on the next IDE start. The theme name has to survive that round trip unchanged, so the facet tab shows
     * what the site declares before the first detection run of the new session.
     */
    @Test
    fun `survives a serialization round trip`() {
        val original = MkDocsMaterialFacetConfiguration().apply { themeName = "Material" }

        val element = XmlSerializer.serialize(original.state)
        val restored = MkDocsMaterialFacetConfiguration().apply {
            loadState(XmlSerializer.deserialize(element, MkDocsMaterialFacetConfiguration.State::class.java))
        }

        assertEquals(
            "the name is remembered as written, so the tab can show what the site actually declares",
            "Material",
            restored.themeName,
        )
    }

    /**
     * Use case: `loadState` is called on an already populated configuration when the project is reloaded.
     * The incoming state must replace the previous value instead of being merged with it.
     */
    @Test
    fun `loading a state overwrites the previous value`() {
        val configuration = MkDocsMaterialFacetConfiguration().apply { themeName = "readthedocs" }

        configuration.loadState(MkDocsMaterialFacetConfiguration.State().apply { themeName = "material" })

        assertEquals("material", configuration.themeName)
    }
}
