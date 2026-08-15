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

package org.pcsoft.ij.plugin.mkdocs.material.data

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * The unit tests around the theme description construct [MkDocsMaterialDataService] directly, which proves
 * that the resources parse but says nothing about the service being reachable the way the plugin reaches it.
 * That is what this test covers: `@Service(Service.Level.APP)` alone has to make it resolvable, without an
 * entry in `plugin.xml`, and the instance the platform hands out has to be the shared one.
 */
class MkDocsMaterialDataServiceIT : BasePlatformTestCase() {

    /**
     * Use case: every caller in the plugin asks for the description with `service<MkDocsMaterialDataService>()`.
     * A service that is not registered would fail there at runtime rather than at build time.
     */
    fun `test the service is registered and reads its resources`() {
        val data = service<MkDocsMaterialDataService>()

        assertNotEmpty(data.extensions.all)
        assertNotEmpty(data.featureFlags.all)
        assertNotEmpty(data.extraKeys.all)
        assertNotEmpty(data.fonts.all)
        assertNotEmpty(data.colors.all)
    }

    /**
     * Use case: the resources are parsed on first access and then kept. Handing out a fresh instance per call
     * would re-read five files every time completion asks a question.
     */
    fun `test the service is a shared instance`() {
        val first = service<MkDocsMaterialDataService>()
        val second = service<MkDocsMaterialDataService>()

        assertSame(first, second)
        assertSame(first.colors, second.colors)
    }

    /**
     * Use case: the reserved `extra` keys belong to *mike* and to the I18N support. Describing them here
     * would make the generated schema fight with the features that own them.
     */
    fun `test the reserved extra keys stay out of the described ones`() {
        val extraKeys = service<MkDocsMaterialDataService>().extraKeys

        assertEquals(setOf("version", "alternate"), extraKeys.reserved)
        extraKeys.reserved.forEach { assertNull(it, extraKeys.byName(it)) }
    }
}
