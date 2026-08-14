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

package org.pcsoft.ij.plugin.mkdocs.material.override

import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the descriptions of the overridable templates: the paths they are written to, the scaffolds they
 * are created with, and that every one of them has a label.
 */
class MkDocsMaterialOverrideTest {

    /**
     * Use case: an override only overrides while it lies at exactly the path the original lies at inside the
     * theme. A typo there produces a site that builds and ignores the file, so the paths are pinned.
     */
    @Test
    fun `the paths are the ones the theme reads`() {
        assertEquals("main.html", MkDocsMaterialOverride.MAIN.path)
        assertEquals("partials/header.html", MkDocsMaterialOverride.HEADER.path)
        assertEquals("partials/footer.html", MkDocsMaterialOverride.FOOTER.path)
        assertEquals("partials/nav.html", MkDocsMaterialOverride.NAV.path)
        assertEquals("partials/copyright.html", MkDocsMaterialOverride.COPYRIGHT.path)
        assertEquals("partials/logo.html", MkDocsMaterialOverride.LOGO.path)
    }

    /**
     * Use case: the file name is what the action creates inside the override directory, and it must not carry
     * the directories in front of it.
     */
    @Test
    fun `the file name drops the directories`() {
        assertEquals("header.html", MkDocsMaterialOverride.HEADER.fileName)
        assertEquals("main.html", MkDocsMaterialOverride.MAIN.fileName)
    }

    /**
     * Use case: the scaffold of `main.html`. The base template is extended and the block is opened with
     * `{{ super() }}` in it — leaving that out is what silently drops a part of every page.
     */
    @Test
    fun `the base template is extended and keeps what the theme rendered`() {
        val scaffold = MkDocsMaterialOverride.MAIN.scaffold

        assertTrue(scaffold.contains("{% extends \"base.html\" %}"))
        assertTrue(scaffold.contains("{{ super() }}"))
        assertTrue(scaffold.contains("{% endblock %}"))
    }

    /**
     * Use case: the scaffold of a partial. MkDocs includes the file of `custom_dir` *instead of* the original,
     * so extending it would be wrong — and an empty file renders a page with a hole in it. The scaffold says
     * as much rather than pretending either.
     */
    @Test
    fun `a partial says that it replaces the original`() {
        val scaffold = MkDocsMaterialOverride.FOOTER.scaffold

        assertTrue(scaffold.contains("partials/footer.html"))
        assertTrue(scaffold.contains("instead of its own"))
        assertTrue(!scaffold.contains("{% extends"))
    }

    /**
     * Use case: every template is offered in a dialog, and an entry without a label would be shown as an
     * empty check box.
     */
    @Test
    fun `every template carries a label`() {
        MkDocsMaterialOverride.entries.forEach {
            assertTrue("no label for ${it.path}", MkDocsBundle.message(it.titleKey).isNotBlank())
        }
    }
}
