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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers which paths the listener answers to. Everything read out of an installation is thrown away when it
 * does, so a filter that is too wide is not a cosmetic matter: a write below any unrelated package would make
 * the whole `RECORD` be read again, which is the cost this feature was made slow by.
 */
class MkDocsMaterialVfsListenerTest {

    /**
     * Use case: pip installing or upgrading the theme. It writes the metadata directory of this distribution,
     * and that is the write everything read out of the installation has to be dropped for.
     */
    @Test
    fun `answers to the metadata of this distribution`() {
        assertTrue(
            MkDocsMaterialVfsListener.isDistributionPath(
                "/home/user/.venv/lib/python3.12/site-packages/mkdocs_material-9.5.0.dist-info/RECORD",
            )
        )
    }

    /**
     * Use case: an icon file of the installed theme being written, which is what an upgrade of the package
     * does thousands of times. The icons are read from exactly there.
     */
    @Test
    fun `answers to the icon sets of the package`() {
        assertTrue(
            MkDocsMaterialVfsListener.isDistributionPath(
                "/home/user/.venv/lib/python3.12/site-packages/material/templates/.icons/material/check.svg",
            )
        )
    }

    /**
     * Use case: the same path as the IDE reports it on Windows, with backslashes. The paths of the VFS are
     * written with slashes, but nothing may depend on that here.
     */
    @Test
    fun `answers to a path written with backslashes`() {
        assertTrue(
            MkDocsMaterialVfsListener.isDistributionPath(
                """C:\Users\dev\.venv\Lib\site-packages\mkdocs_material-9.5.0.dist-info\METADATA""",
            )
        )
    }

    /**
     * Use case: any other package of the same environment. Installing it changes nothing about the theme, and
     * dropping what was read about the theme for it is what made every popup afterwards pay for a re-read.
     */
    @Test
    fun `ignores another package of the same environment`() {
        assertFalse(
            MkDocsMaterialVfsListener.isDistributionPath(
                "/home/user/.venv/lib/python3.12/site-packages/mkdocs-1.6.0.dist-info/RECORD",
            )
        )
    }

    /**
     * Use case: the plain `site-packages` directory itself, which is what a refresh of an environment reports.
     * It says nothing about this distribution, and it was the path the listener answered to before.
     */
    @Test
    fun `ignores the environment directory itself`() {
        assertFalse(
            MkDocsMaterialVfsListener.isDistributionPath(
                "/home/user/.venv/lib/python3.12/site-packages",
            )
        )
    }

    /**
     * Use case: a file of the project being edited. Nothing of the project is an installed package, and the
     * listener runs on every write the IDE sees.
     */
    @Test
    fun `ignores a file of the project`() {
        assertFalse(MkDocsMaterialVfsListener.isDistributionPath("/home/user/project/docs/mkdocs.yml"))
    }
}
