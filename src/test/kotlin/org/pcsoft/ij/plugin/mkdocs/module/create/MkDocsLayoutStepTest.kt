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

package org.pcsoft.ij.plugin.mkdocs.module.create

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers how the name entered in the first step becomes the directory the site is created in.
 */
class MkDocsLayoutStepTest {

    /**
     * Use case: the user types a name. It is appended to the location, so the site gets a directory of its
     * own without anyone having to type a path.
     */
    @Test
    fun `appends the name to the base directory`() {
        assertEquals(
            "/home/chris/projects/handbook",
            MkDocsLayoutStep.locationFor("/home/chris/projects", "handbook"),
        )
    }

    /**
     * Use case: nothing has been typed yet, or the name was deleted again. The path must fall back to the
     * plain base directory instead of showing a dangling separator.
     */
    @Test
    fun `keeps the base directory while no name is entered`() {
        assertEquals("/home/chris/projects", MkDocsLayoutStep.locationFor("/home/chris/projects", ""))
        assertEquals("/home/chris/projects", MkDocsLayoutStep.locationFor("/home/chris/projects", "   "))
    }

    /**
     * Use case: a name reading like a sentence — spaces are fine in a directory name and must survive,
     * because the directory is meant to be recognisable as the site.
     */
    @Test
    fun `keeps spaces inside the name`() {
        assertEquals("/projects/My Documentation", MkDocsLayoutStep.locationFor("/projects", "My Documentation"))
    }

    /**
     * Use case: a name carrying characters no file system accepts, or a path separator that would let the
     * directory escape the base directory. Those are replaced instead of being passed through.
     */
    @Test
    fun `replaces characters a directory name cannot carry`() {
        assertEquals("/projects/a_b", MkDocsLayoutStep.locationFor("/projects", "a/b"))
        assertEquals("/projects/a_b", MkDocsLayoutStep.locationFor("/projects", "a\\b"))
        assertEquals("/projects/what_", MkDocsLayoutStep.locationFor("/projects", "what?"))
        assertEquals("/projects/C__docs", MkDocsLayoutStep.locationFor("/projects", "C:/docs"))
    }

    /**
     * Use case: the name is padded with spaces from typing or pasting. They belong to neither the directory
     * name nor the path.
     */
    @Test
    fun `trims the name before appending it`() {
        assertEquals("/projects/handbook", MkDocsLayoutStep.locationFor("/projects", "  handbook  "))
    }
}
