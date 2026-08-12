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

package org.pcsoft.ij.plugin.mkdocs.module.facet

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the persistent state of the MkDocs facet, which is what makes a detected site survive an IDE
 * restart.
 */
class MkDocsFacetConfigurationTest {

    /**
     * Use case: a freshly created facet has no site information yet, so every property must start out empty
     * rather than `null` — the `.iml` serializer would otherwise drop the attributes.
     */
    @Test
    fun `starts with an empty state`() {
        val configuration = MkDocsFacetConfiguration()

        assertEquals("", configuration.siteName)
        assertEquals("", configuration.configFilePath)
        assertFalse(configuration.ownsModule)
        assertEquals("", configuration.ownerModuleName)
        assertEquals(
            "MkDocs has no key for the assets directory, so the convention is the starting point",
            "assets",
            configuration.assetsDirName,
        )
        assertEquals(
            "MkDocs names the individual style sheets, never their directory, so the convention starts here",
            "stylesheets",
            configuration.stylesheetsDirName,
        )
    }

    /**
     * Use case: the detector fills the configuration, the module store writes it to the `.iml` and reads it
     * back on the next IDE start. Values must survive that round trip unchanged.
     */
    @Test
    fun `survives a serialization round trip`() {
        val original = MkDocsFacetConfiguration().apply {
            siteName = "My Documentation"
            configFilePath = "mkdocs.yml"
            ownsModule = true
            ownerModuleName = "app"
            assetsDirName = "media"
            stylesheetsDirName = "css"
        }

        val element = XmlSerializer.serialize(original.state)
        val restored = MkDocsFacetConfiguration().apply {
            loadState(XmlSerializer.deserialize(element, MkDocsFacetConfiguration.State::class.java))
        }

        assertEquals("My Documentation", restored.siteName)
        assertEquals("mkdocs.yml", restored.configFilePath)
        assertTrue(restored.ownsModule)
        assertEquals(
            "the former owner must survive the restart, the exclusion is reverted with it",
            "app",
            restored.ownerModuleName,
        )
        assertEquals(
            "the assets directory lives in no file MkDocs reads, so only the facet can carry it",
            "media",
            restored.assetsDirName,
        )
        assertEquals(
            "the stylesheets directory lives in no file MkDocs reads either",
            "css",
            restored.stylesheetsDirName,
        )
    }

    /**
     * Use case: `loadState` is called on an already populated configuration when the project is reloaded.
     * The incoming state must replace the old values completely.
     */
    @Test
    fun `loading a state overwrites previous values`() {
        val configuration = MkDocsFacetConfiguration().apply {
            siteName = "Old"
            configFilePath = "mkdocs.yaml"
            ownsModule = true
            ownerModuleName = "app"
        }

        configuration.loadState(MkDocsFacetConfiguration.State().apply {
            siteName = "New"
            configFilePath = "mkdocs.yml"
            ownsModule = false
            ownerModuleName = ""
        })

        assertEquals("New", configuration.siteName)
        assertEquals("mkdocs.yml", configuration.configFilePath)
        assertFalse(configuration.ownsModule)
        assertEquals("", configuration.ownerModuleName)
    }
}
