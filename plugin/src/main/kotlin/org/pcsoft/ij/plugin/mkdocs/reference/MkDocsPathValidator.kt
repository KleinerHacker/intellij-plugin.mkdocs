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

package org.pcsoft.ij.plugin.mkdocs.reference

import com.intellij.openapi.util.TextRange

/**
 * What can be wrong with a path written into an MkDocs configuration file.
 *
 * The distinction the entries carry is not "bad" against "worse" but "broken here" against "broken
 * elsewhere". A configuration file travels: it is written on one machine, built on the continuous
 * integration of another and checked out by whoever contributes a page. A path the current operating system
 * refuses is an error, a path only some other operating system refuses is a warning — reported, because it
 * will break the build of somebody else, but not marked as broken on a machine where it works.
 */
enum class MkDocsPathProblem {

    /** A character the file system refuses, one of `<>:"|?*`. */
    FORBIDDEN_CHARACTER,

    /** A control character, which no file system accepts in a name. */
    CONTROL_CHARACTER,

    /** Two separators without a name between them. */
    EMPTY_SEGMENT,

    /** A segment ending in a dot or a space, which Windows silently strips. */
    TRAILING_DOT_OR_SPACE,

    /** A path starting at the root of the file system rather than at the base directory. */
    ABSOLUTE_PATH,

    /** A path starting with a Windows drive letter. */
    DRIVE_LETTER,

    /** More `..` segments than the path has descended, which leaves the base directory. */
    PARENT_ESCAPE,

    /** A backslash used to separate segments, which only Windows reads as a separator. */
    BACKSLASH_SEPARATOR,

    /** A name Windows reserves for a device, such as `con` or `lpt1`. */
    RESERVED_NAME,

    /** A character outside ASCII, which survives a checkout only when every machine agrees on the encoding. */
    NON_ASCII,

    /** A space inside a segment, which the built site has to escape in every address pointing at it. */
    SPACE_IN_SEGMENT,

    /** A path or a segment long enough to run into the limits of a file system. */
    TOO_LONG;

    /**
     * Returns `true` if this problem makes the path unusable on the operating system the IDE runs on.
     *
     * Five of the entries are broken everywhere and four only on Windows; the remaining three work on Windows
     * and are merely awkward elsewhere, so they never rise beyond a warning.
     *
     * @param windows `true` if the current operating system is Windows
     */
    fun isError(windows: Boolean): Boolean = when (this) {
        CONTROL_CHARACTER, EMPTY_SEGMENT, ABSOLUTE_PATH, DRIVE_LETTER, PARENT_ESCAPE -> true
        FORBIDDEN_CHARACTER, TRAILING_DOT_OR_SPACE, RESERVED_NAME, TOO_LONG -> windows
        BACKSLASH_SEPARATOR, NON_ASCII, SPACE_IN_SEGMENT -> false
    }
}

/**
 * One thing found wrong with a path value.
 *
 * @property problem what is wrong
 * @property range where it is wrong, relative to the start of the checked path
 * @property text the offending fragment, shown in the message
 */
data class MkDocsPathFinding(
    val problem: MkDocsPathProblem,
    val range: TextRange,
    val text: String,
)

/**
 * Checks the characters and the structure of a path written into an MkDocs configuration file.
 *
 * This is deliberately about the path as text, not about the file it names: whether the target exists is
 * answered by the references, which resolve it. What is answered here is whether the value can name a file at
 * all — and whether it can still do so on the next machine the site is built on.
 *
 * The check is a pure function of the path string, so it needs neither PSI nor a read action.
 */
object MkDocsPathValidator {

    /**
     * The length a path may reach before it is reported.
     *
     * Windows caps a full path at 260 characters unless long paths are switched on, and the path checked here
     * is only the part below the base directory — the checkout directory in front of it counts as well. The
     * limit therefore sits well below the cap.
     */
    const val MAX_PATH_LENGTH: Int = 200

    /** The length a single segment may reach, which is what common file systems allow for one name. */
    const val MAX_SEGMENT_LENGTH: Int = 255

    /** The characters Windows refuses inside a name. */
    private const val WINDOWS_FORBIDDEN_CHARACTERS = "<>:\"|?*"

    /** The names Windows reserves for devices, whatever extension is appended to them. */
    private val RESERVED_NAMES: Set<String> = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        for (index in 1..9) {
            add("COM$index")
            add("LPT$index")
        }
    }

    /** A drive letter followed by its colon at the very start of a path. */
    private val DRIVE_LETTER = Regex("^[A-Za-z]:")

    /**
     * The problems that only say the path does not stay below the directory it is resolved against.
     *
     * They are about the place a path leads to, not about the path being spelled in a way a file system
     * refuses — which is why they are the ones dropped for a value that is allowed to lead elsewhere.
     */
    private val LEAVING_PROBLEMS = setOf(
        MkDocsPathProblem.ABSOLUTE_PATH,
        MkDocsPathProblem.DRIVE_LETTER,
        MkDocsPathProblem.PARENT_ESCAPE,
    )

    /**
     * Checks [path] and returns everything found wrong with it, in the order it appears in the text.
     *
     * Which operating system the IDE runs on plays no part here: a finding is reported wherever it is found,
     * and only how loudly it is reported depends on the platform — see [MkDocsPathProblem.isError].
     *
     * Most values of a configuration file have to stay below the directory MkDocs resolves them against — a
     * page outside the documentation directory is not part of the site and would never be rendered. One does
     * not: `site_dir` names where the build *writes*, and writing next to the checkout, above it or onto an
     * entirely different volume is a perfectly ordinary setup. For such a value [mayLeaveBaseDirectory] drops
     * the three findings that say nothing but "this leads out of the base directory"; everything else — the
     * characters, the segments, the reserved names, the length — is checked exactly as before, because a name
     * the file system refuses stays refused wherever the directory lies.
     *
     * @param path the path as written, without the quotes of a quoted scalar
     * @param mayLeaveBaseDirectory `true` if the value is allowed to point outside the directory it is
     *        resolved against, which suppresses [MkDocsPathProblem.ABSOLUTE_PATH],
     *        [MkDocsPathProblem.DRIVE_LETTER] and [MkDocsPathProblem.PARENT_ESCAPE]
     * @return the findings, ordered by their position, empty if nothing is wrong
     */
    fun validate(path: String, mayLeaveBaseDirectory: Boolean = false): List<MkDocsPathFinding> {
        if (path.isEmpty()) return emptyList()

        val findings = mutableListOf<MkDocsPathFinding>()
        checkCharacters(path, findings)
        checkPrefix(path, findings)
        checkSegments(path, findings)
        if (path.length > MAX_PATH_LENGTH) {
            findings += MkDocsPathFinding(MkDocsPathProblem.TOO_LONG, TextRange(0, path.length), path)
        }
        return findings
            .filter { !mayLeaveBaseDirectory || it.problem !in LEAVING_PROBLEMS }
            .sortedWith(compareBy({ it.range.startOffset }, { it.problem.ordinal }))
    }

    /**
     * Collects everything that can be seen from a single character of [path].
     *
     * @param path the path to check
     * @param findings the list the findings are added to
     */
    private fun checkCharacters(path: String, findings: MutableList<MkDocsPathFinding>) {
        val driveColon = if (DRIVE_LETTER.containsMatchIn(path)) 1 else -1
        var nonAsciiStart = -1
        for (index in path.indices) {
            val character = path[index]
            when {
                character.isISOControl() ->
                    findings += finding(MkDocsPathProblem.CONTROL_CHARACTER, path, index, index + 1)

                character == '\\' ->
                    findings += finding(MkDocsPathProblem.BACKSLASH_SEPARATOR, path, index, index + 1)

                index != driveColon && character in WINDOWS_FORBIDDEN_CHARACTERS ->
                    findings += finding(MkDocsPathProblem.FORBIDDEN_CHARACTER, path, index, index + 1)
            }

            // Runs of non ASCII characters are reported as one finding: a word written in a national alphabet
            // is one decision of the author, not one per letter.
            if (character.code > MAX_ASCII) {
                if (nonAsciiStart < 0) nonAsciiStart = index
            } else if (nonAsciiStart >= 0) {
                findings += finding(MkDocsPathProblem.NON_ASCII, path, nonAsciiStart, index)
                nonAsciiStart = -1
            }
        }
        if (nonAsciiStart >= 0) {
            findings += finding(MkDocsPathProblem.NON_ASCII, path, nonAsciiStart, path.length)
        }
    }

    /**
     * Checks how [path] starts, which is what decides whether it stays inside the site at all.
     *
     * @param path the path to check
     * @param findings the list the findings are added to
     */
    private fun checkPrefix(path: String, findings: MutableList<MkDocsPathFinding>) {
        if (DRIVE_LETTER.containsMatchIn(path)) {
            findings += finding(MkDocsPathProblem.DRIVE_LETTER, path, 0, 2)
            return
        }
        if (path.startsWith('/') || path.startsWith('\\')) {
            findings += finding(MkDocsPathProblem.ABSOLUTE_PATH, path, 0, 1)
        }
    }

    /**
     * Checks the segments of [path] one by one and follows how deep they descend.
     *
     * @param path the path to check
     * @param findings the list the findings are added to
     */
    private fun checkSegments(path: String, findings: MutableList<MkDocsPathFinding>) {
        val segments = splitSegments(path)
        var depth = 0
        for ((position, segment) in segments.withIndex()) {
            val start = segment.first
            val text = segment.second
            val end = start + text.length

            if (text.isEmpty()) {
                // A leading separator is already reported as an absolute path and a trailing one is how a
                // directory may be written, so only a gap between two names is an empty segment.
                if (position > 0 && position < segments.lastIndex) {
                    findings += finding(MkDocsPathProblem.EMPTY_SEGMENT, path, start, end)
                }
                continue
            }

            when (text) {
                "." -> Unit
                ".." -> {
                    depth--
                    if (depth < 0) {
                        findings += finding(MkDocsPathProblem.PARENT_ESCAPE, path, start, end)
                        depth = 0
                    }
                }

                else -> {
                    depth++
                    checkSegment(path, start, end, text, findings)
                }
            }
        }
    }

    /**
     * Checks a single named segment of [path].
     *
     * @param path the path the segment belongs to
     * @param start the offset the segment starts at
     * @param end the offset the segment ends at
     * @param text the segment itself
     * @param findings the list the findings are added to
     */
    private fun checkSegment(
        path: String,
        start: Int,
        end: Int,
        text: String,
        findings: MutableList<MkDocsPathFinding>,
    ) {
        if (text.last() == '.' || text.last() == ' ') {
            findings += finding(MkDocsPathProblem.TRAILING_DOT_OR_SPACE, path, end - 1, end)
        }
        if (text.contains(' ')) {
            findings += finding(MkDocsPathProblem.SPACE_IN_SEGMENT, path, start, end)
        }
        if (text.length > MAX_SEGMENT_LENGTH) {
            findings += finding(MkDocsPathProblem.TOO_LONG, path, start, end)
        }
        if (text.substringBefore('.').uppercase() in RESERVED_NAMES) {
            findings += finding(MkDocsPathProblem.RESERVED_NAME, path, start, end)
        }
    }

    /**
     * Splits [path] into its segments, keeping the offset each of them starts at.
     *
     * Both separators are accepted here. A backslash is reported separately, but a value written with them
     * still has to be read as a path — otherwise every following check would see one endless segment.
     *
     * @param path the path to split
     * @return the offset and the text of every segment, in order
     */
    private fun splitSegments(path: String): List<Pair<Int, String>> {
        val segments = mutableListOf<Pair<Int, String>>()
        var start = 0
        for (index in path.indices) {
            if (path[index] == '/' || path[index] == '\\') {
                segments += start to path.substring(start, index)
                start = index + 1
            }
        }
        segments += start to path.substring(start)
        return segments
    }

    /**
     * Builds a finding covering the text of [path] between [start] and [end].
     *
     * @param problem what is wrong
     * @param path the checked path
     * @param start the offset the finding starts at
     * @param end the offset the finding ends at
     */
    private fun finding(problem: MkDocsPathProblem, path: String, start: Int, end: Int): MkDocsPathFinding =
        MkDocsPathFinding(problem, TextRange(start, end), path.substring(start, end))

    /** The highest character code still inside ASCII. */
    private const val MAX_ASCII = 127
}
