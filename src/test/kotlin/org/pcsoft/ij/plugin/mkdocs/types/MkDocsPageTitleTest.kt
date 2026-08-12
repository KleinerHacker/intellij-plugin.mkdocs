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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the title a node of the navigation tree carries when `nav` gives none: the first level one heading
 * of the page, and the file name as the last resort.
 */
class MkDocsPageTitleTest {

    /**
     * Use case: the ordinary page. Its first line is an ATX heading, and that heading is what the tree node
     * has to be labelled with.
     */
    @Test
    fun `takes the ATX heading of the first line`() {
        assertEquals("Getting started", MkDocsPageTitle.extract("# Getting started\n\nSome text.\n"))
    }

    /**
     * Use case: a page opening with a paragraph or a banner before its heading. The heading still counts,
     * wherever in the page it stands.
     */
    @Test
    fun `finds an ATX heading below leading text`() {
        val text = """
            Some introduction without a heading.

            # Installation

            More text.
        """.trimIndent()
        assertEquals("Installation", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: a heading indented by up to three spaces is still a heading for Markdown, so it has to be one
     * here as well. Four spaces make it an indented code block, which is not a heading.
     */
    @Test
    fun `accepts up to three leading spaces`() {
        assertEquals("Indented", MkDocsPageTitle.extract("   # Indented\n"))
        assertNull(MkDocsPageTitle.extract("    # Code block, not a heading\n"))
    }

    /**
     * Use case: a heading written in the closed ATX style. The trailing `#` characters are decoration and
     * must not end up in the label.
     */
    @Test
    fun `drops the closing hash sequence`() {
        assertEquals("Closed", MkDocsPageTitle.extract("# Closed #\n"))
        assertEquals("Closed", MkDocsPageTitle.extract("# Closed ###\n"))
    }

    /**
     * Use case: a page whose first heading is a level two or three heading. MkDocs derives the page title
     * from the level one heading only, so such a page announces no title at all.
     */
    @Test
    fun `ignores headings below level one`() {
        assertNull(MkDocsPageTitle.extract("## Section\n\n### Subsection\n"))
    }

    /**
     * Use case: a page written in the Setext style, where the heading is underlined instead of prefixed.
     * Python-Markdown — and therefore MkDocs — accepts it, so the tree has to as well.
     */
    @Test
    fun `takes a Setext heading underlined with equals signs`() {
        assertEquals("Underlined", MkDocsPageTitle.extract("Underlined\n==========\n\nText.\n"))
    }

    /**
     * Use case: a Setext heading underlined with dashes is a level two heading, not a level one heading, and
     * must not be mistaken for the page title.
     */
    @Test
    fun `ignores a Setext heading underlined with dashes`() {
        assertNull(MkDocsPageTitle.extract("Underlined\n----------\n\nText.\n"))
    }

    /**
     * Use case: a page documenting Markdown itself, showing a `#` heading inside a fenced code block. That
     * `#` is sample code and must not become the title of the page.
     */
    @Test
    fun `skips headings inside a backtick fence`() {
        val text = """
            ```markdown
            # Not the title
            ```

            # The real title
        """.trimIndent()
        assertEquals("The real title", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: the same, written with the tilde fence Python-Markdown also accepts.
     */
    @Test
    fun `skips headings inside a tilde fence`() {
        val text = """
            ~~~
            # Not the title
            ~~~

            # The real title
        """.trimIndent()
        assertEquals("The real title", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: a page carrying YAML front matter without a `title` key. The front matter is metadata, so a
     * `#` inside it is no heading; the heading below it is the title.
     */
    @Test
    fun `skips the front matter and takes the heading below it`() {
        val text = """
            ---
            tags:
              - guide
            ---

            # Below the front matter
        """.trimIndent()
        assertEquals("Below the front matter", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: a page setting its title explicitly in the front matter. MkDocs prefers that value over the
     * heading of the page, and so does the tree.
     */
    @Test
    fun `prefers the title of the front matter over the heading`() {
        val text = """
            ---
            title: From the front matter
            ---

            # From the heading
        """.trimIndent()
        assertEquals("From the front matter", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: the front matter title is quoted, which YAML allows and which must not leak into the label.
     */
    @Test
    fun `unquotes the title of the front matter`() {
        assertEquals("Quoted", MkDocsPageTitle.extract("---\ntitle: \"Quoted\"\n---\n"))
        assertEquals("Quoted", MkDocsPageTitle.extract("---\ntitle: 'Quoted'\n---\n"))
    }

    /**
     * Use case: a `title` nested inside another mapping of the front matter belongs to that mapping, not to
     * the page, and must not be taken as the page title.
     */
    @Test
    fun `ignores an indented title key inside the front matter`() {
        val text = """
            ---
            extra:
              title: Nested
            ---

            # The real title
        """.trimIndent()
        assertEquals("The real title", MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: a page opening with a thematic break rather than front matter. Without a closing marker there
     * is no front matter, so the heading below the break is the title.
     */
    @Test
    fun `treats an unterminated front matter as ordinary content`() {
        assertEquals("Still a title", MkDocsPageTitle.extract("---\n# Still a title\n"))
    }

    /**
     * Use case: a heading carrying inline formatting. The label of a tree node is plain text, so emphasis,
     * code spans, links and images have to be reduced to the text they show.
     */
    @Test
    fun `strips inline markdown from the heading`() {
        assertEquals("Bold", MkDocsPageTitle.extract("# **Bold**\n"))
        assertEquals("Italic", MkDocsPageTitle.extract("# _Italic_\n"))
        assertEquals("Both", MkDocsPageTitle.extract("# ***Both***\n"))
        assertEquals("mkdocs.yml", MkDocsPageTitle.extract("# `mkdocs.yml`\n"))
        assertEquals("Homepage", MkDocsPageTitle.extract("# [Homepage](https://example.org)\n"))
        assertEquals("Logo", MkDocsPageTitle.extract("# ![Logo](logo.png)\n"))
    }

    /**
     * Use case: a heading with an `attr_list` suffix, which Material for MkDocs uses to pin a custom anchor.
     * The suffix is configuration, not text.
     */
    @Test
    fun `strips an attr_list suffix from the heading`() {
        assertEquals("Custom anchor", MkDocsPageTitle.extract("# Custom anchor { #custom }\n"))
    }

    /**
     * Use case: a page written on Windows. Carriage returns must not end up in the label or break the
     * detection of the underline of a Setext heading.
     */
    @Test
    fun `handles carriage return line endings`() {
        assertEquals("Windows", MkDocsPageTitle.extract("# Windows\r\n\r\nText.\r\n"))
        assertEquals("Windows", MkDocsPageTitle.extract("Windows\r\n=======\r\n"))
    }

    /**
     * Use case: a file saved with a byte order mark. The mark sits in front of the first `#` and would
     * otherwise hide the heading.
     */
    @Test
    fun `handles a leading byte order mark`() {
        assertEquals("With BOM", MkDocsPageTitle.extract("\uFEFF# With BOM\n"))
    }

    /**
     * Use case: a page without any heading, and the degenerate cases of an empty or blank file. All of them
     * announce no title and have to fall back to the file name.
     */
    @Test
    fun `returns null without a heading`() {
        assertNull(MkDocsPageTitle.extract(""))
        assertNull(MkDocsPageTitle.extract("   \n\n\t\n"))
        assertNull(MkDocsPageTitle.extract("Just a paragraph.\n\nAnd another one.\n"))
        assertNull(MkDocsPageTitle.extract("#\n"))
        assertNull(MkDocsPageTitle.extract("#    \n"))
    }

    /**
     * Use case: a generated page of thousands of lines whose heading, if any, sits far below the top. The
     * scan has to stop rather than read the whole file.
     */
    @Test
    fun `stops scanning after the line limit`() {
        val text = buildString {
            repeat(MkDocsPageTitle.MAX_SCANNED_LINES + 10) { appendLine("filler") }
            appendLine("# Too late")
        }
        assertNull(MkDocsPageTitle.extract(text))
    }

    /**
     * Use case: the last resort of the label chain. The file name without its extension is what the user sees
     * in the project view, so it is what the tree node shows.
     */
    @Test
    fun `falls back to the file name without its extension`() {
        assertEquals("install", MkDocsPageTitle.fallback("install.md"))
        assertEquals("release.notes", MkDocsPageTitle.fallback("release.notes.md"))
        assertEquals("README", MkDocsPageTitle.fallback("README"))
    }

    /**
     * Use case: a `nav` entry whose target cannot be resolved to a file. The label is then derived from the
     * path as written, which carries directories the label must not show.
     */
    @Test
    fun `reduces a path to its last segment`() {
        assertEquals("install", MkDocsPageTitle.fallback("guide/install.md"))
        assertEquals("install", MkDocsPageTitle.fallback("guide\\install.md"))
    }

    /**
     * Use case: a file that is nothing but an extension. Stripping it would leave an empty label, so the name
     * is kept as it stands.
     */
    @Test
    fun `keeps a name that is only an extension`() {
        assertEquals(".md", MkDocsPageTitle.fallback(".md"))
        assertEquals("", MkDocsPageTitle.fallback(""))
    }
}
