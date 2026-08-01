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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Checks the shipped artifact rather than a single unit: the plugin descriptor must be on the runtime
 * classpath and declare the plugin ID the Marketplace release is published under.
 */
class PluginDescriptorIT {

    @Test
    fun `plugin descriptor is on the classpath and declares the expected id`() {
        val descriptor = javaClass.getResource("/META-INF/plugin.xml")
        assertNotNull("META-INF/plugin.xml is missing from the runtime classpath", descriptor)

        val content = descriptor!!.readText()
        assertTrue(
            "plugin.xml does not declare the expected plugin id",
            content.contains("<id>org.pcsoft.ij.plugin.mkdocs</id>")
        )
    }
}
