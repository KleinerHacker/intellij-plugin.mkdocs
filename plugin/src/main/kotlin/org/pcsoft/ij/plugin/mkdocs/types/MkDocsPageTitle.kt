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

/**
 * Reads the title a Markdown page presents itself with.
 *
 * MkDocs labels a page with the title given in `nav`; without one it falls back to the first heading of the
 * page and finally to the file name. This object answers the second and third step of that chain.
 *
 * The analysis is plain text, deliberately without a Markdown parser: the plugin has to work in every
 * IntelliJ-platform IDE, and the Markdown plugin is not available in all of them. Nothing here touches the
 * virtual file system or PSI, so no read action is needed and the whole logic stays testable on its own.
 */
object MkDocsPageTitle {

    /**
     * How many lines of a page are inspected before the search is given up.
     *
     * A title belongs to the top of a page. Reading on would only cost time on generated files that carry
     * thousands of lines without ever holding a heading.
     */
    const val MAX_SCANNED_LINES: Int = 500

    /** The byte order mark, which would otherwise hide the first `---` or `#` of a page. */
    private const val BYTE_ORDER_MARK = "\uFEFF"

    /** A level one ATX heading: up to three leading spaces, exactly one `#`, then the text. */
    private val ATX_HEADING = Regex("""^ {0,3}#(?!#)[ \t]*(.*)$""")

    /** The optional closing `#` sequence of an ATX heading, which is decoration rather than text. */
    private val ATX_CLOSING = Regex("""\s+#+\s*$""")

    /** The underline turning the line above it into a level one Setext heading. */
    private val SETEXT_UNDERLINE = Regex("""^ {0,3}=+\s*$""")

    /** Opening or closing marker of a fenced code block. */
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")

    /** A top level `title` key inside the YAML front matter of a page. */
    private val FRONT_MATTER_TITLE = Regex("""^title:\s*(.+)$""")

    /** The `attr_list` suffix a heading may carry, for example `{ #custom-id }`. */
    private val ATTR_LIST = Regex("""\s*\{[^}]*}\s*$""")

    /** An inline image, reduced to its alternative text. */
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")

    /** An inline link, reduced to its text. */
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")

    /** An inline code span, reduced to its content. */
    private val CODE_SPAN = Regex("""`([^`]*)`""")

    /** Emphasis markers, from the longest to the shortest so `***x***` is not mistaken for `*` plus `**`. */
    private val EMPHASIS = listOf(
        Regex("""\*\*\*(.+?)\*\*\*"""),
        Regex("""___(.+?)___"""),
        Regex("""\*\*(.+?)\*\*"""),
        Regex("""__(.+?)__"""),
        Regex("""\*(.+?)\*"""),
        Regex("""_(.+?)_"""),
    )

    /** Marks the beginning of the YAML front matter of a page. */
    private const val FRONT_MATTER_OPEN = "---"

    /** The two spellings MkDocs accepts for the end of the front matter. */
    private val FRONT_MATTER_CLOSE = setOf("---", "...")

    /**
     * Returns the title [text] announces, or `null` if it announces none.
     *
     * Three sources are consulted in the order MkDocs itself uses: a `title` entry in the YAML front matter,
     * the first level one heading of the page — written either with a leading `#` or underlined with `=` —
     * and nothing beyond that. A `#` inside a fenced code block is code, not a heading, and is skipped.
     *
     * @param text the content of a Markdown page
     * @return the title without its Markdown decoration, or `null` if there is none
     */
    fun extract(text: CharSequence): String? {
        val lines = text.lineSequence().take(MAX_SCANNED_LINES).toMutableList()
        if (lines.isEmpty()) return null
        lines[0] = lines[0].removePrefix(BYTE_ORDER_MARK)

        val frontMatter = frontMatterRange(lines) ?: return titleFromBody(lines, 0)
        return titleFromFrontMatter(lines, frontMatter) ?: titleFromBody(lines, frontMatter.last + 1)
    }

    /**
     * Returns the title to use for a page that announces none of its own.
     *
     * The bare file name without its extension, as written. MkDocs additionally turns separators into spaces
     * and capitalises the result; that is deliberately not reproduced here, because the file name is what the
     * user sees in the project view and recognises the entry by.
     *
     * @param fileName the file name of the page; a path is accepted and reduced to its last segment
     * @return the file name without its extension, blank only if [fileName] is blank
     */
    fun fallback(fileName: String): String {
        val bare = fileName.substringAfterLast('/').substringAfterLast('\\')
        return bare.substringBeforeLast('.').takeIf { it.isNotEmpty() } ?: bare
    }

    /**
     * Returns the line range the YAML front matter of the page occupies, or `null` if it has none.
     *
     * The range covers the opening and the closing marker. An unterminated block is not front matter — the
     * `---` opening it is then a thematic break like any other.
     *
     * @param lines the lines of the page
     */
    private fun frontMatterRange(lines: List<String>): IntRange? {
        val start = lines.indexOfFirst { it.isNotBlank() }
        if (start < 0 || lines[start].trim() != FRONT_MATTER_OPEN) return null

        for (index in start + 1 until lines.size) {
            if (lines[index].trim() in FRONT_MATTER_CLOSE) return start..index
        }
        return null
    }

    /**
     * Returns the value of the `title` key inside the front matter, or `null` if there is none.
     *
     * Only a key at the very beginning of a line counts, so a `title` nested inside another mapping is not
     * mistaken for the title of the page.
     *
     * @param lines the lines of the page
     * @param range the line range the front matter occupies
     */
    private fun titleFromFrontMatter(lines: List<String>, range: IntRange): String? {
        for (index in range.first + 1 until range.last) {
            val value = FRONT_MATTER_TITLE.matchEntire(lines[index])?.groupValues?.get(1) ?: continue
            return unquote(value.trim()).takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * Returns the first level one heading found from [from] onwards, or `null` if there is none.
     *
     * @param lines the lines of the page
     * @param from the first line to inspect
     */
    private fun titleFromBody(lines: List<String>, from: Int): String? {
        var fenceMarker: Char? = null
        var fenceLength = 0
        var previous: String? = null

        for (index in from until lines.size) {
            val line = lines[index]
            val marker = FENCE.find(line)?.groupValues?.get(1)

            if (fenceMarker != null) {
                if (marker != null && marker[0] == fenceMarker && marker.length >= fenceLength) {
                    fenceMarker = null
                    fenceLength = 0
                }
                previous = null
                continue
            }

            if (marker != null) {
                fenceMarker = marker[0]
                fenceLength = marker.length
                previous = null
                continue
            }

            val heading = ATX_HEADING.matchEntire(line)?.groupValues?.get(1)
            if (heading != null) {
                clean(heading.replace(ATX_CLOSING, ""))?.let { return it }
                previous = null
                continue
            }

            val underlined = previous
            if (underlined != null && SETEXT_UNDERLINE.matches(line)) {
                clean(underlined)?.let { return it }
                previous = null
                continue
            }

            previous = line.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Strips the Markdown decoration off [raw] and returns what is left, or `null` if nothing is.
     *
     * @param raw the text of a heading as it stands in the file
     */
    private fun clean(raw: String): String? {
        var value = ATTR_LIST.replace(raw.trim(), "")
        value = IMAGE.replace(value, "$1")
        value = LINK.replace(value, "$1")
        value = CODE_SPAN.replace(value, "$1")
        EMPHASIS.forEach { value = it.replace(value, "$1") }
        return value.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Removes a pair of surrounding quotes from [value], if it carries one.
     *
     * @param value a scalar value out of the front matter
     */
    private fun unquote(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        if ((first == '"' || first == '\'') && value.last() == first) {
            return value.substring(1, value.length - 1)
        }
        return value
    }
}
