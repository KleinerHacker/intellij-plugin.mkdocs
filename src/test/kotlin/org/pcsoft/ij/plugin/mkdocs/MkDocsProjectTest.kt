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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 */
class MkDocsProjectTest {

    @Test
    fun `recognises both accepted config file names`() {
        assertTrue(MkDocsProject.isConfigFile("mkdocs.yml"))
        assertTrue(MkDocsProject.isConfigFile("mkdocs.yaml"))
    }

    @Test
    fun `recognition is case-insensitive`() {
        assertTrue(MkDocsProject.isConfigFile("MkDocs.YML"))
    }

    @Test
    fun `rejects unrelated file names`() {
        assertFalse(MkDocsProject.isConfigFile("mkdocs.json"))
        assertFalse(MkDocsProject.isConfigFile("docs.yml"))
        assertFalse(MkDocsProject.isConfigFile(""))
        // A path is not a bare file name — callers must strip the directory part themselves.
        assertFalse(MkDocsProject.isConfigFile("docs/mkdocs.yml"))
    }
}
