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

package org.pcsoft.ij.plugin.mkdocs.types

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers turning whatever a repository was cloned from into the link and the name MkDocs shows.
 */
class MkDocsScmUrlTest {

    /**
     * Use case: the project was cloned over HTTPS, which is already a link a reader can follow — only the
     * `.git` suffix Git appends has no business in a browser address.
     */
    @Test
    fun `keeps an https remote and drops the git suffix`() {
        assertEquals(
            "https://github.com/acme/machine",
            MkDocsScmUrl.toWebUrl("https://github.com/acme/machine.git"),
        )
        assertEquals(
            "https://github.com/acme/machine",
            MkDocsScmUrl.toWebUrl("https://github.com/acme/machine"),
        )
    }

    /**
     * Use case: the project was cloned over SSH in the short form Git uses by default. That address opens
     * nothing in a browser, so it has to be rewritten before it can become `repo_url`.
     */
    @Test
    fun `rewrites the short ssh form to a web address`() {
        assertEquals(
            "https://github.com/acme/machine",
            MkDocsScmUrl.toWebUrl("git@github.com:acme/machine.git"),
        )
    }

    /**
     * Use case: the same clone, written with an explicit `ssh://` scheme — the other spelling Git accepts.
     */
    @Test
    fun `rewrites the explicit ssh form to a web address`() {
        assertEquals(
            "https://gitlab.com/group/project",
            MkDocsScmUrl.toWebUrl("ssh://git@gitlab.com/group/project.git"),
        )
    }

    /**
     * Use case: an address carrying credentials, as an automated checkout leaves behind. Publishing that in
     * the configuration file would leak them, so the user part is dropped.
     */
    @Test
    fun `drops credentials from the address`() {
        assertEquals(
            "https://github.com/acme/machine",
            MkDocsScmUrl.toWebUrl("https://token@github.com/acme/machine.git"),
        )
    }

    /**
     * Use case: a remote pointing at a directory or a mount rather than a hoster. There is no web address
     * for it, and inventing one would produce a broken link on every page.
     */
    @Test
    fun `reports no web address for a remote that has none`() {
        assertNull(MkDocsScmUrl.toWebUrl("file:///srv/git/machine.git"))
        assertNull(MkDocsScmUrl.toWebUrl(""))
        assertNull(MkDocsScmUrl.toWebUrl("   "))
    }

    /**
     * Use case: the name shown next to the repository link. Every hoster names a repository by its owner and
     * its own name, which is what a reader expects to see.
     */
    @Test
    fun `derives the repository name from the last two segments`() {
        assertEquals("acme/machine", MkDocsScmUrl.toRepositoryName("https://github.com/acme/machine.git"))
        assertEquals("acme/machine", MkDocsScmUrl.toRepositoryName("git@github.com:acme/machine.git"))
        assertEquals(
            "group/project",
            MkDocsScmUrl.toRepositoryName("https://gitlab.com/company/group/project.git"),
        )
    }

    /**
     * Use case: a self-hosted repository sitting directly below the host. There is only one segment to take,
     * and that segment is the name.
     */
    @Test
    fun `derives the repository name from a single segment`() {
        assertEquals("machine", MkDocsScmUrl.toRepositoryName("https://git.example.org/machine.git"))
    }

    /**
     * Use case: the user pasted the browser address of the very repository the project was cloned from over
     * SSH. Both forms name the same repository, so no mismatch may be reported.
     */
    @Test
    fun `treats the ssh and the https form of one repository as equal`() {
        assertTrue(
            MkDocsScmUrl.isSameRepository(
                "https://github.com/acme/machine",
                "git@github.com:acme/machine.git",
            ),
        )
        assertTrue(
            MkDocsScmUrl.isSameRepository(
                "https://github.com/acme/machine/",
                "https://github.com/acme/machine.git",
            ),
        )
    }

    /**
     * Use case: the user pointed the site at a different repository. That is allowed, but it has to be
     * recognised as different so the wizard can say so.
     */
    @Test
    fun `reports two different repositories as different`() {
        assertFalse(
            MkDocsScmUrl.isSameRepository(
                "https://github.com/acme/machine",
                "https://github.com/acme/other",
            ),
        )
        assertFalse(MkDocsScmUrl.isSameRepository("", "https://github.com/acme/machine"))
    }
}
