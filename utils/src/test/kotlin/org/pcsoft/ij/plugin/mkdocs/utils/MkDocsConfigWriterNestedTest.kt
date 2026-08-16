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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the nested key, mapping and sequence operations of [MkDocsConfigWriter], the API the Material
 * settings pages write their configuration through. Every test asserts the resulting file text in full: the
 * point of these operations is not only that the value arrives, but that everything around it — position,
 * indentation and above all the comments the author wrote — comes out unchanged.
 */
class MkDocsConfigWriterNestedTest : BasePlatformTestCase() {

    /**
     * Use case: a Material setting deep in the theme is written into a file knowing neither `theme` nor
     * `palette`. Both levels have to be created as block mappings, each indented one level deeper.
     */
    fun `test creates a nested path from nothing`() {
        val file = configFile("create/mkdocs.yml", "site_name: Handbook\n")

        assertTrue(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.palette.primary", "indigo") })

        assertEquals(
            "site_name: Handbook\ntheme:\n  palette:\n    primary: indigo\n",
            text(file),
        )
    }

    /**
     * Use case: a setting the file already carries is changed. The key keeps its position, and only the value
     * node behind it is exchanged.
     */
    fun `test sets an existing nested scalar in place`() {
        val file = configFile(
            "existing/mkdocs.yml",
            "site_name: Handbook\ntheme:\n  name: material\n  language: en\nsite_url: https://x.y\n",
        )

        assertTrue(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.language", "de") })

        assertEquals(
            "site_name: Handbook\ntheme:\n  name: material\n  language: de\nsite_url: https://x.y\n",
            text(file),
        )
        assertEquals("de", readNested(file, "theme.language"))
    }

    /**
     * Use case: the site writes its theme in the shorthand MkDocs allows — `theme: material`. There is no
     * mapping to put `language` into, and turning the scalar into one behind the user's back would decide
     * something the caller has to decide. The write is refused and the file stays as it is.
     */
    fun `test refuses a path blocked by a scalar`() {
        val file = configFile("blocked/mkdocs.yml", "site_name: Handbook\ntheme: material\n")

        assertFalse(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.language", "de") })

        assertEquals("site_name: Handbook\ntheme: material\n", text(file))
    }

    /**
     * Use case: the shorthand has to grow a second setting. The scalar is moved below the key as `name`, and
     * a comment written behind it stays with the value it describes.
     */
    fun `test promotes a scalar to a mapping`() {
        val file = configFile("promote/mkdocs.yml", "site_name: Handbook\ntheme: material  # the theme\n")

        assertTrue(write { MkDocsConfigWriter.promoteScalarToMapping(project, file, "theme", "name") })

        assertEquals(
            "site_name: Handbook\ntheme:\n  name: material  # the theme\n",
            text(file),
        )
    }

    /**
     * Use case: the first style sheet is added to a site that never listed one. The sequence itself has to be
     * created below the key.
     */
    fun `test creates a sequence from nothing`() {
        val file = configFile("sequence-new/mkdocs.yml", "site_name: Handbook\n")

        assertTrue(write { MkDocsConfigWriter.addScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, "extra.css") })

        assertEquals("site_name: Handbook\nextra_css:\n  - extra.css\n", text(file))
        assertEquals(listOf("extra.css"), readSequence(file, MkDocsConfig.KEY_EXTRA_CSS))
    }

    /**
     * Use case: a second style sheet joins an existing list. The list must not be rewritten — the entry is
     * appended as one line, and a comment between the existing entries survives it. Adding the same entry
     * twice does nothing.
     */
    fun `test appends to an existing sequence`() {
        val file = configFile(
            "sequence-append/mkdocs.yml",
            "extra_css:\n  - a.css\n  # the important one\n  - b.css\n",
        )

        assertTrue(write { MkDocsConfigWriter.addScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, "c.css") })
        assertTrue(write { MkDocsConfigWriter.addScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, "a.css") })

        assertEquals(
            "extra_css:\n  - a.css\n  # the important one\n  - b.css\n  - c.css\n",
            text(file),
        )
    }

    /**
     * Use case: a style sheet is taken out of the list. Only its line goes; the entries around it keep their
     * text.
     */
    fun `test removes a scalar item`() {
        val file = configFile("sequence-remove/mkdocs.yml", "extra_css:\n  - a.css\n  - b.css\n  - c.css\n")

        assertTrue(write { MkDocsConfigWriter.removeScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, "b.css") })
        assertFalse(write { MkDocsConfigWriter.removeScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, "z.css") })

        assertEquals("extra_css:\n  - a.css\n  - c.css\n", text(file))
    }

    /**
     * Use case: an entry of a list is renamed rather than removed and added again, so it keeps its position.
     */
    fun `test sets a scalar item by index`() {
        val file = configFile("sequence-set/mkdocs.yml", "extra_css:\n  - a.css\n  - b.css\n")

        write { MkDocsConfigWriter.setScalarItem(project, file, MkDocsConfig.KEY_EXTRA_CSS, 1, "other.css") }

        assertEquals("extra_css:\n  - a.css\n  - other.css\n", text(file))
    }

    /**
     * Use case: the social links of the Material theme — a sequence of mappings. Entries are appended, read
     * back, changed key by key, counted and removed again, all without rewriting the entries next to them.
     */
    fun `test writes mapping items`() {
        val file = configFile("mapping-items/mkdocs.yml", "site_name: Handbook\n")

        val first = write {
            MkDocsConfigWriter.addMappingItem(
                project,
                file,
                "extra.social",
                listOf("icon" to "fontawesome/brands/github", "link" to "https://github.com/x"),
            )
        }
        val second = write {
            MkDocsConfigWriter.addMappingItem(
                project,
                file,
                "extra.social",
                listOf("icon" to "fontawesome/brands/mastodon"),
            )
        }

        assertEquals(0, first)
        assertEquals(1, second)
        assertEquals(
            "site_name: Handbook\n" +
                    "extra:\n" +
                    "  social:\n" +
                    "    - icon: fontawesome/brands/github\n" +
                    "      link: 'https://github.com/x'\n" +
                    "    - icon: fontawesome/brands/mastodon\n",
            text(file),
        )
        assertEquals(2, itemCount(file, "extra.social"))

        assertTrue(write {
            MkDocsConfigWriter.setItemScalar(
                project,
                file,
                "extra.social",
                1,
                "icon",
                "octicons/mark"
            )
        })
        assertTrue(write { MkDocsConfigWriter.setItemScalar(project, file, "extra.social", 1, "name", "Mastodon") })

        assertEquals(
            "site_name: Handbook\n" +
                    "extra:\n" +
                    "  social:\n" +
                    "    - icon: fontawesome/brands/github\n" +
                    "      link: 'https://github.com/x'\n" +
                    "    - icon: octicons/mark\n" +
                    "      name: Mastodon\n",
            text(file),
        )
        assertEquals(
            listOf(
                mapOf("icon" to "fontawesome/brands/github", "link" to "https://github.com/x"),
                mapOf("icon" to "octicons/mark", "name" to "Mastodon"),
            ),
            readMappingSequence(file, "extra.social"),
        )

        write { MkDocsConfigWriter.removeItem(project, file, "extra.social", 1) }

        assertEquals(1, itemCount(file, "extra.social"))
    }

    /**
     * Use case: the last setting of a nested block is removed. A `palette` holding nothing is no longer a
     * palette, and an empty `theme` above it is no longer a theme, so both go — but the top level mapping of
     * the file is never pruned.
     */
    fun `test prunes mappings left empty`() {
        val file = configFile(
            "prune/mkdocs.yml",
            "site_name: Handbook\ntheme:\n  palette:\n    primary: indigo\n",
        )

        write { MkDocsConfigWriter.removeNestedKey(project, file, "theme.palette.primary") }

        assertEquals("site_name: Handbook\n", text(file))
    }

    /**
     * Use case: the settings page writes into a file its author commented. A comment above a key, a comment on
     * a sibling key and above all a comment trailing the very key being changed have to survive the write —
     * the value node is the only thing that may be touched.
     */
    fun `test keeps every comment around a changed key`() {
        val file = configFile(
            "comments/mkdocs.yml",
            "site_name: Handbook  # the title\n" +
                    "theme:\n" +
                    "  # how the site looks\n" +
                    "  name: material  # do not change lightly\n" +
                    "  language: en  # the default\n",
        )

        assertTrue(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.name", "readthedocs") })

        assertEquals(
            "site_name: Handbook  # the title\n" +
                    "theme:\n" +
                    "  # how the site looks\n" +
                    "  name: readthedocs  # do not change lightly\n" +
                    "  language: en  # the default\n",
            text(file),
        )
    }

    /**
     * Use case: a key is added below a block that already exists. The new line has to carry the indentation of
     * the block it joins, not the column a generated fragment would start at.
     */
    fun `test indents a key added two levels deep`() {
        val file = configFile("indent/mkdocs.yml", "site_name: Handbook\ntheme:\n  name: material\n")

        assertTrue(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.palette.primary", "indigo") })
        assertTrue(write { MkDocsConfigWriter.setNestedBoolean(project, file, "theme.palette.toggle", true) })

        assertEquals(
            "site_name: Handbook\ntheme:\n  name: material\n  palette:\n    primary: indigo\n    toggle: true\n",
            text(file),
        )
    }

    /**
     * Use case: a key written without a value — `theme:` and nothing below it. There is no mapping to put a
     * child into, so one has to be materialized below the key.
     */
    fun `test fills a key written without a value`() {
        val file = configFile("empty-key/mkdocs.yml", "site_name: Handbook\ntheme:\nsite_url: https://x.y\n")

        assertTrue(write { MkDocsConfigWriter.setNestedScalar(project, file, "theme.name", "material") })

        assertEquals(
            "site_name: Handbook\ntheme:\n  name: material\nsite_url: https://x.y\n",
            text(file),
        )
    }

    /**
     * Use case: a settings page applies several changes at once. They must reach the file as one batch — the
     * document may not be written through in between, otherwise one *Apply* turns into a whole row of them.
     */
    fun `test commits a batch of changes once`() {
        val file = configFile("batch/mkdocs.yml", "site_name: Handbook\n")
        var unsavedDuringBatch = false

        write {
            MkDocsConfigWriter.edit(project, file) {
                setNestedScalar("theme.name", "material")
                setNestedScalar("theme.language", "de")
                setNestedBoolean("theme.palette.toggle", false)
                addScalarItem(MkDocsConfig.KEY_EXTRA_CSS, "extra.css")
                unsavedDuringBatch = FileDocumentManager.getInstance()
                    .getDocument(file)
                    ?.let { FileDocumentManager.getInstance().isDocumentUnsaved(it) } == true
            }
        }

        assertTrue("the batch must not be written through before it is complete", unsavedDuringBatch)
        assertEquals(
            "site_name: Handbook\n" +
                    "theme:\n" +
                    "  name: material\n" +
                    "  language: de\n" +
                    "  palette:\n" +
                    "    toggle: false\n" +
                    "extra_css:\n" +
                    "  - extra.css\n",
            text(file),
        )
    }

    private fun configFile(path: String, text: String): VirtualFile =
        myFixture.addFileToProject(path, text).virtualFile

    private fun <T> write(action: () -> T): T =
        WriteCommandAction.writeCommandAction(project).compute<T, RuntimeException> { action() }

    private fun text(file: VirtualFile): String =
        runReadActionBlocking { MkDocsConfig.yamlFileOf(project, file)!!.text }

    private fun readNested(file: VirtualFile, path: String): String? =
        runReadActionBlocking { MkDocsConfig.readNestedScalar(project, file, path) }

    private fun readSequence(file: VirtualFile, path: String): List<String> =
        runReadActionBlocking { MkDocsConfig.readScalarSequence(project, file, path) }

    private fun readMappingSequence(file: VirtualFile, path: String): List<Map<String, String>> =
        runReadActionBlocking { MkDocsConfig.readMappingSequence(project, file, path) }

    private fun itemCount(file: VirtualFile, path: String): Int =
        runReadActionBlocking { MkDocsConfigWriter.itemCount(project, file, path) }
}
