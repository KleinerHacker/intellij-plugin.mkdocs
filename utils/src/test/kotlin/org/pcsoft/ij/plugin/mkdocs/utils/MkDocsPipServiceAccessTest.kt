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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers where the pip service may start a process and where it may not. The platform reports a process
 * waited for under a read action as an error, and every place this plugin asks about an installation from —
 * a completion popup, an inlay hint, an annotator — runs under one, so the rule is pinned here rather than
 * left to each caller.
 */
class MkDocsPipServiceAccessTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            service<MkDocsPipService>().invalidate()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a completion popup or an annotator asking where a distribution is installed. No process may
     * be started there, so the answer is nothing rather than an error in the log — and nothing is remembered
     * either, because the question was never actually put.
     */
    fun `test asks pip for nothing under a read action`() {
        val distribution = "mkdocs-not-installed-${System.nanoTime()}"

        val found = runReadActionBlocking { service<MkDocsPipService>().location(distribution) }

        assertNull(found)
        assertFalse(service<MkDocsPipService>().isKnown(distribution))
    }

    /**
     * Use case: the same question once the answer is known. A cached answer costs nothing, so it is handed
     * out under a read action as well — that is what makes the icons appear after the warm-up.
     */
    fun `test hands out a known answer under a read action`() {
        val distribution = "mkdocs-known-${System.nanoTime()}"
        service<MkDocsPipService>().overrideLocation(distribution, "/opt/packages")

        assertTrue(service<MkDocsPipService>().isKnown(distribution))
        assertEquals("/opt/packages", runReadActionBlocking { service<MkDocsPipService>().location(distribution) })
    }

    /**
     * Use case: the way out for a caller that may not ask. The question is put on another thread and the
     * caller is told once the answer is there — which is what the highlighting is restarted from.
     */
    fun `test fetches the answer on a thread that may wait for it`() {
        val distribution = "mkdocs-not-installed-${System.nanoTime()}"
        val answered = CountDownLatch(1)

        service<MkDocsPipService>().prefetch(distribution) { answered.countDown() }

        assertTrue(answered.await(30, TimeUnit.SECONDS))
        assertTrue(service<MkDocsPipService>().isKnown(distribution))
    }

    /**
     * Use case: a highlighting pass running again and again. Nothing is fetched a second time once the
     * answer is known, so the passes cannot pile up processes.
     */
    fun `test fetches nothing for an answer that is known`() {
        val distribution = "mkdocs-known-${System.nanoTime()}"
        service<MkDocsPipService>().overrideLocation(distribution, "")
        val answered = CountDownLatch(1)

        service<MkDocsPipService>().prefetch(distribution) { answered.countDown() }

        assertFalse(answered.await(1, TimeUnit.SECONDS))
    }
}
