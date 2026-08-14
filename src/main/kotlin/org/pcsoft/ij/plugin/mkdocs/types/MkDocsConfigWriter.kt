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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsSiteCreationService

/**
 * Writes the configuration keys the IDE side of the plugin has to change back into an MkDocs configuration
 * file.
 *
 * Writing is deliberately surgical: a change never rewrites a whole mapping or a whole sequence, it touches
 * exactly the one node that has to change and leaves everything around it — the arrangement of the keys, the
 * comments between and behind them, the indentation the author picked — byte for byte where it was. That is
 * the reason a value is replaced through [YAMLKeyValue.setValue] instead of replacing the key-value itself
 * (which would take a trailing same-line comment with it), and the reason new lines are built with their
 * final indentation and inserted into the document instead of being grafted in as freshly generated PSI.
 *
 * The counterpart reading these keys is [MkDocsConfig].
 *
 * Every function here must be called inside a write action — the caller owns it, because a change to the
 * configuration usually belongs into the same undoable command as whatever triggered it. Every single
 * function commits the document on its own; a caller changing several keys at once should use [edit], which
 * commits exactly once for the whole batch.
 */
object MkDocsConfigWriter {

    /** The key naming the theme below [MkDocsConfig.KEY_THEME]. */
    private const val KEY_NAME: String = "name"

    /** The number of spaces one nesting level adds, matching what MkDocs configuration files use. */
    private const val INDENT_SIZE: Int = 2

    /**
     * Sets the theme of the site described by [configFile] to [name].
     *
     * The three shapes a configuration file can be in are all handled: an existing `theme` mapping only has
     * its `name` replaced or added, so settings the author put next to it survive; a `theme` written as a
     * plain scalar is promoted to a mapping, because a scalar has nowhere to keep the name; and a file
     * without a theme at all gets the key appended to the end of its top level mapping.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param name the name of the theme to configure, for example [MkDocsConfig.THEME_MATERIAL]
     */
    fun setThemeName(project: Project, configFile: VirtualFile, name: String) {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val theme = keyValueAt(yamlFile, listOf(MkDocsConfig.KEY_THEME))
        if (theme != null && theme.value is YAMLScalar) {
            promoteScalarToMappingIn(project, configFile, MkDocsConfig.KEY_THEME, KEY_NAME)
        }
        setNestedScalarIn(project, configFile, "${MkDocsConfig.KEY_THEME}.$KEY_NAME", name)
        commit(project, configFile)
    }

    /**
     * Removes the theme of the site described by [configFile], so MkDocs falls back to its built-in one.
     *
     * Only the theme *name* is taken out. A `theme` mapping carrying settings next to the name — a palette,
     * a font, a list of features — keeps those: they are the user's work, they were not written by this
     * plugin, and dropping them because a facet was unchecked would destroy far more than was asked for. The
     * `theme` key itself is removed only when the name was the last thing left in it, which is the case a
     * plugin-written theme block is in.
     *
     * A file without a theme is left untouched.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     */
    fun removeTheme(project: Project, configFile: VirtualFile) {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val theme = keyValueAt(yamlFile, listOf(MkDocsConfig.KEY_THEME)) ?: return
        val themeMapping = theme.value as? YAMLMapping
        val hasName = themeMapping?.keyValues?.any { it.keyText.trim() == KEY_NAME } == true

        if (hasName && themeMapping.keyValues.size > 1) {
            removeNestedKeyIn(project, configFile, "${MkDocsConfig.KEY_THEME}.$KEY_NAME")
        } else {
            removeNestedKeyIn(project, configFile, MkDocsConfig.KEY_THEME)
        }
        commit(project, configFile)
    }

    /**
     * Sets the top level key [key] of [configFile] to the scalar [value].
     *
     * A delegate to [setNestedScalar] with a path of a single segment, kept because a top level key is what
     * most callers write and naming the operation after that reads better at the call site.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param key the top level key to write
     * @param value the value to write, as the user entered it
     */
    fun setScalarKey(project: Project, configFile: VirtualFile, key: String, value: String) {
        setNestedScalar(project, configFile, key, value)
    }

    /**
     * Removes the top level key [key] from [configFile].
     *
     * Used when a value returns to what MkDocs assumes anyway: a configuration file repeating a default tells
     * the reader nothing. A file not carrying the key is left untouched.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param key the top level key to remove
     */
    fun removeKey(project: Project, configFile: VirtualFile, key: String) {
        removeNestedKey(project, configFile, key)
    }

    /**
     * Sets the key addressed by the dotted [path] of [configFile] to the scalar [value].
     *
     * Every level the path names but the file does not have yet is created as a block mapping, so writing
     * `theme.palette.primary` into a file knowing neither `theme` nor `palette` produces the whole block. An
     * existing key keeps its position in the file and any comment trailing it — only its value node is
     * exchanged.
     *
     * The value is quoted the same way the site creation quotes it, so a value carrying a colon or a leading
     * indicator character cannot turn the file into something YAML reads differently.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the key, for example `theme.language`
     * @param value the value to write, as the user entered it
     * @return `false` if a segment of the path exists but is not a mapping — a `theme` written as a plain
     *         scalar blocks `theme.language`, and silently throwing that scalar away is not this function's
     *         decision to make
     */
    fun setNestedScalar(project: Project, configFile: VirtualFile, path: String, value: String): Boolean {
        val written = setNestedScalarIn(project, configFile, path, value)
        commit(project, configFile)
        return written
    }

    /**
     * Sets the key addressed by the dotted [path] of [configFile] to the boolean [value].
     *
     * Behaves like [setNestedScalar] in every respect but the quoting: a boolean is written unquoted, because
     * `true` in quotes is a string to MkDocs and would silently be read as "set".
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the key
     * @param value the value to write
     * @return `false` if a segment of the path exists but is not a mapping
     */
    fun setNestedBoolean(project: Project, configFile: VirtualFile, path: String, value: Boolean): Boolean {
        val written = setNestedBooleanIn(project, configFile, path, value)
        commit(project, configFile)
        return written
    }

    /**
     * Removes the key addressed by the dotted [path] from [configFile].
     *
     * A mapping left empty by the removal is taken out as well, up the path: dropping `theme.palette.primary`
     * from a palette holding nothing else removes `palette`, and if that was all `theme` had, `theme` goes
     * too. The top level is never removed — a configuration file always keeps its mapping.
     *
     * A file not carrying the key is left untouched.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the key to remove
     */
    fun removeNestedKey(project: Project, configFile: VirtualFile, path: String) {
        removeNestedKeyIn(project, configFile, path)
        commit(project, configFile)
    }

    /**
     * Turns the scalar at [path] into a mapping holding that scalar under [key].
     *
     * The way out of the shape MkDocs allows as a shorthand: `theme: material` says everything a themed site
     * needs until a second theme setting has to go next to it, and this turns it into `theme:` with
     * `name: material` below without losing the value or a comment behind it.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the key holding the scalar
     * @param key the key the previous scalar is moved under
     * @return `false` if the path does not exist or does not hold a scalar
     */
    fun promoteScalarToMapping(project: Project, configFile: VirtualFile, path: String, key: String): Boolean {
        val promoted = promoteScalarToMappingIn(project, configFile, path, key)
        commit(project, configFile)
        return promoted
    }

    /**
     * Adds [value] to the scalar sequence at [path] of [configFile].
     *
     * The sequence — and every mapping level above it — is created when it is missing. An existing sequence
     * is not rewritten: the new entry is appended as one line at the indentation the entries already have, so
     * comments between the existing entries survive. A value already in the sequence is not added twice.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence, for example `extra_css`
     * @param value the entry to add, as the user entered it
     * @return `false` if a segment of the path is blocked by a non-mapping, if the key holds something other
     *         than a sequence, or if the sequence is written in flow style, which cannot be appended to line
     *         by line
     */
    fun addScalarItem(project: Project, configFile: VirtualFile, path: String, value: String): Boolean {
        val added = addScalarItemIn(project, configFile, path, value)
        commit(project, configFile)
        return added
    }

    /**
     * Removes [value] from the scalar sequence at [path] of [configFile].
     *
     * Only the line carrying the entry is taken out; the rest of the sequence keeps its text.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param value the entry to remove
     * @return `false` if there is no such sequence or it does not hold the value
     */
    fun removeScalarItem(project: Project, configFile: VirtualFile, path: String, value: String): Boolean {
        val removed = removeScalarItemIn(project, configFile, path, value)
        commit(project, configFile)
        return removed
    }

    /**
     * Sets the entry [index] of the scalar sequence at [path] of [configFile] to [value].
     *
     * Only the value node of that one entry is exchanged, so a comment behind it stays with it.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry
     * @param value the value to write, as the user entered it
     */
    fun setScalarItem(project: Project, configFile: VirtualFile, path: String, index: Int, value: String) {
        setScalarItemIn(project, configFile, path, index, value)
        commit(project, configFile)
    }

    /**
     * Appends a mapping entry built from [entries] to the sequence at [path] of [configFile].
     *
     * The shape `extra.social` and `theme.palette` are written in: a sequence whose entries are mappings. The
     * sequence and the levels above it are created when they are missing.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param entries the keys and values of the new entry, in the order they are written
     * @return the zero based position the entry was appended at, or `-1` if it could not be written
     */
    fun addMappingItem(
        project: Project,
        configFile: VirtualFile,
        path: String,
        entries: List<Pair<String, String>>,
    ): Int {
        val index = addMappingItemIn(project, configFile, path, entries)
        commit(project, configFile)
        return index
    }

    /**
     * Sets [key] of the mapping entry [index] of the sequence at [path] of [configFile] to [value].
     *
     * A key the entry does not have yet is appended to it; an existing one keeps its position and only has
     * its value exchanged.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry
     * @param key the key inside the entry
     * @param value the value to write, as the user entered it
     * @return `false` if there is no such entry or the entry is not a mapping
     */
    fun setItemScalar(
        project: Project,
        configFile: VirtualFile,
        path: String,
        index: Int,
        key: String,
        value: String,
    ): Boolean {
        val written = setItemScalarIn(project, configFile, path, index, key, value)
        commit(project, configFile)
        return written
    }

    /**
     * Removes the entry [index] from the sequence at [path] of [configFile].
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry to remove
     */
    fun removeItem(project: Project, configFile: VirtualFile, path: String, index: Int) {
        removeItemIn(project, configFile, path, index)
        commit(project, configFile)
    }

    /**
     * Returns how many entries the sequence at [path] of [configFile] holds.
     *
     * The caller must hold a read action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @return the number of entries, or `0` if there is no sequence at the path
     */
    fun itemCount(project: Project, configFile: VirtualFile, path: String): Int {
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return 0
        return sequenceAt(yamlFile, splitPath(path))?.items?.size ?: 0
    }

    /**
     * Runs [block] against [configFile] and commits the result exactly once.
     *
     * The way a settings page writes: changing fifteen keys through fifteen separate calls would commit and
     * save the document fifteen times, which is fifteen undo steps' worth of churn for one *Apply*. Inside
     * the block the same operations are available without repeating the project and the file.
     *
     * The caller must hold a write action.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param block the changes to apply
     */
    fun edit(project: Project, configFile: VirtualFile, block: MkDocsConfigEditScope.() -> Unit) {
        MkDocsConfigEditScope(project, configFile).block()
        commit(project, configFile)
    }

    // ---------------------------------------------------------------------------------------------------
    // Uncommitted operations — shared by the public functions above and by MkDocsConfigEditScope
    // ---------------------------------------------------------------------------------------------------

    /** Backs [setNestedScalar] without committing the document. */
    internal fun setNestedScalarIn(project: Project, configFile: VirtualFile, path: String, value: String): Boolean =
        setRawIn(project, configFile, path, MkDocsSiteCreationService.yamlScalar(value))

    /** Backs [setNestedBoolean] without committing the document. */
    internal fun setNestedBooleanIn(project: Project, configFile: VirtualFile, path: String, value: Boolean): Boolean =
        setRawIn(project, configFile, path, value.toString())

    /** Backs [removeNestedKey] without committing the document. */
    internal fun removeNestedKeyIn(project: Project, configFile: VirtualFile, path: String) {
        val document = syncedDocument(project, configFile) ?: return
        val segments = splitPath(path)
        if (segments.isEmpty()) return
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val keyValue = keyValueAt(yamlFile, segments) ?: return
        deleteLines(project, document, keyValue)

        var parents = segments.dropLast(1)
        while (parents.isNotEmpty()) {
            val file = MkDocsConfig.yamlFileOf(project, configFile) ?: return
            val parent = keyValueAt(file, parents) ?: return
            val value = parent.value
            val isEmpty = value == null || (value is YAMLMapping && value.keyValues.isEmpty())
            if (!isEmpty) return
            deleteLines(project, document, parent)
            parents = parents.dropLast(1)
        }
    }

    /** Backs [promoteScalarToMapping] without committing the document. */
    internal fun promoteScalarToMappingIn(
        project: Project,
        configFile: VirtualFile,
        path: String,
        key: String,
    ): Boolean {
        val document = syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false
        val keyValue = keyValueAt(yamlFile, splitPath(path)) ?: return false
        val scalar = keyValue.value as? YAMLScalar ?: return false

        val indent = columnOf(document, keyValue.textRange.startOffset) + INDENT_SIZE
        var start = scalar.textRange.startOffset
        while (start > 0 && document.charsSequence[start - 1] == ' ') {
            start--
        }
        document.replaceString(start, scalar.textRange.endOffset, "\n${" ".repeat(indent)}$key: ${scalar.text}")
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return true
    }

    /** Backs [addScalarItem] without committing the document. */
    internal fun addScalarItemIn(project: Project, configFile: VirtualFile, path: String, value: String): Boolean {
        val segments = splitPath(path)
        if (segments.isEmpty()) return false
        val document = syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false
        val raw = MkDocsSiteCreationService.yamlScalar(value)

        val keyValue = keyValueAt(yamlFile, segments)
            ?: return insertInto(project, configFile, segments.dropLast(1), listOf("${segments.last()}:", "  - $raw"))
        val sequence = keyValue.value
        return when {
            sequence is YAMLSequence -> {
                val present = sequence.items.any { (it.value as? YAMLScalar)?.textValue?.trim() == value }
                present || appendToSequence(project, document, sequence, listOf("- $raw"))
            }

            sequence == null -> {
                val indent = columnOf(document, keyValue.textRange.startOffset) + INDENT_SIZE
                insertLines(project, document, keyValue, listOf("${" ".repeat(indent)}- $raw"))
                true
            }

            else -> false
        }
    }

    /** Backs [removeScalarItem] without committing the document. */
    internal fun removeScalarItemIn(project: Project, configFile: VirtualFile, path: String, value: String): Boolean {
        val document = syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false
        val sequence = sequenceAt(yamlFile, splitPath(path)) ?: return false
        val item = sequence.items.firstOrNull { (it.value as? YAMLScalar)?.textValue?.trim() == value } ?: return false
        deleteLines(project, document, item)
        return true
    }

    /** Backs [setScalarItem] without committing the document. */
    internal fun setScalarItemIn(
        project: Project,
        configFile: VirtualFile,
        path: String,
        index: Int,
        value: String,
    ) {
        val document = syncedDocument(project, configFile) ?: return
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val sequence = sequenceAt(yamlFile, splitPath(path)) ?: return
        val item = sequence.items.getOrNull(index) ?: return
        val current = item.value ?: return
        document.replaceString(
            current.textRange.startOffset,
            current.textRange.endOffset,
            MkDocsSiteCreationService.yamlScalar(value),
        )
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /** Backs [addMappingItem] without committing the document. */
    internal fun addMappingItemIn(
        project: Project,
        configFile: VirtualFile,
        path: String,
        entries: List<Pair<String, String>>,
    ): Int {
        val segments = splitPath(path)
        if (segments.isEmpty() || entries.isEmpty()) return -1
        val document = syncedDocument(project, configFile) ?: return -1
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return -1
        val lines = entries.mapIndexed { position, (key, value) ->
            val prefix = if (position == 0) "- " else "  "
            "$prefix$key: ${MkDocsSiteCreationService.yamlScalar(value)}"
        }

        val keyValue = keyValueAt(yamlFile, segments)
        if (keyValue == null) {
            val body = listOf("${segments.last()}:") + lines.map { "  $it" }
            return if (insertInto(project, configFile, segments.dropLast(1), body)) 0 else -1
        }
        val sequence = keyValue.value
        return when {
            sequence is YAMLSequence -> {
                val index = sequence.items.size
                if (appendToSequence(project, document, sequence, lines)) index else -1
            }

            sequence == null -> {
                val indent = columnOf(document, keyValue.textRange.startOffset) + INDENT_SIZE
                insertLines(project, document, keyValue, lines.map { "${" ".repeat(indent)}$it" })
                0
            }

            else -> -1
        }
    }

    /** Backs [setItemScalar] without committing the document. */
    internal fun setItemScalarIn(
        project: Project,
        configFile: VirtualFile,
        path: String,
        index: Int,
        key: String,
        value: String,
    ): Boolean {
        val document = syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false
        val sequence = sequenceAt(yamlFile, splitPath(path)) ?: return false
        val mapping = sequence.items.getOrNull(index)?.value as? YAMLMapping ?: return false
        val raw = MkDocsSiteCreationService.yamlScalar(value)

        val existing = mapping.keyValues.firstOrNull { it.keyText.trim() == key }
        if (existing != null) {
            val newValue = generateValue(project, raw) ?: return false
            existing.setValue(newValue)
            return true
        }
        val anchor = mapping.keyValues.lastOrNull() ?: return false
        val indent = columnOf(document, mapping.keyValues.first().textRange.startOffset)
        insertLines(project, document, anchor, listOf("${" ".repeat(indent)}$key: $raw"))
        return true
    }

    /** Backs [removeItem] without committing the document. */
    internal fun removeItemIn(project: Project, configFile: VirtualFile, path: String, index: Int) {
        val document = syncedDocument(project, configFile) ?: return
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val sequence = sequenceAt(yamlFile, splitPath(path)) ?: return
        val item = sequence.items.getOrNull(index) ?: return
        deleteLines(project, document, item)
    }

    // ---------------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------------

    /**
     * Writes [raw] — already rendered as YAML — as the value of the key addressed by [path].
     *
     * @return `false` if a segment of the path is blocked by a non-mapping value
     */
    private fun setRawIn(project: Project, configFile: VirtualFile, path: String, raw: String): Boolean {
        val segments = splitPath(path)
        if (segments.isEmpty()) return false
        syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false

        val existing = keyValueAt(yamlFile, segments)
        if (existing != null) {
            val newValue = generateValue(project, raw) ?: return false
            existing.setValue(newValue)
            return true
        }
        return insertInto(project, configFile, segments.dropLast(1), listOf("${segments.last()}: $raw"))
    }

    /**
     * Inserts [leafLines] into the mapping addressed by [segments], creating every missing level on the way.
     *
     * The lines are built with their final indentation and inserted as text rather than generated as PSI and
     * grafted in: a generated fragment always starts at column zero, and grafting it deeper than one level
     * produces indentation YAML reads as a different structure.
     *
     * @param project the project [configFile] belongs to
     * @param configFile the configuration file to change
     * @param segments the path of the mapping the lines go into, empty for the top level
     * @param leafLines the lines to insert, indented relative to each other, without leading indentation
     * @return `false` if a segment of the path is blocked by a non-mapping value
     */
    private fun insertInto(
        project: Project,
        configFile: VirtualFile,
        segments: List<String>,
        leafLines: List<String>,
    ): Boolean {
        val document = syncedDocument(project, configFile) ?: return false
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return false

        val topLevel = MkDocsConfig.topLevelMapping(yamlFile)
        if (topLevel == null) {
            // Nothing to put the key into: the file is empty, or it holds nothing but comments. Appending the
            // lines to the document rather than building PSI around them keeps those comments where they are.
            val separator = if (document.textLength == 0 || document.text.endsWith("\n")) "" else "\n"
            document.insertString(document.textLength, separator + chain(segments, leafLines, 0).joinToString("\n") + "\n")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            return true
        }

        var current: YAMLMapping = topLevel
        var index = 0
        while (index < segments.size) {
            val keyValue = current.keyValues.firstOrNull { it.keyText.trim() == segments[index] } ?: break
            val value = keyValue.value
            when {
                value is YAMLMapping -> {
                    current = value
                    index++
                }

                value == null -> {
                    val indent = columnOf(document, keyValue.textRange.startOffset) + INDENT_SIZE
                    val lines = chain(segments.subList(index + 1, segments.size), leafLines, indent)
                    insertLines(project, document, keyValue, lines)
                    return true
                }

                else -> return false
            }
        }

        val indent = current.keyValues.firstOrNull()
            ?.let { columnOf(document, it.textRange.startOffset) }
            ?: 0
        insertLines(project, document, current, chain(segments.subList(index, segments.size), leafLines, indent))
        return true
    }

    /**
     * Renders [segments] as a chain of block mapping keys, with [leafLines] below the innermost one.
     *
     * @param segments the keys to open, from the outermost to the innermost
     * @param leafLines the content of the innermost level
     * @param indent the column the outermost key starts at
     * @return the lines to insert, each carrying its full indentation
     */
    private fun chain(segments: List<String>, leafLines: List<String>, indent: Int): List<String> {
        val lines = mutableListOf<String>()
        var column = indent
        for (segment in segments) {
            lines += "${" ".repeat(column)}$segment:"
            column += INDENT_SIZE
        }
        leafLines.forEach { lines += "${" ".repeat(column)}$it" }
        return lines
    }

    /**
     * Appends [lines] as a new entry of [sequence], at the indentation the existing entries have.
     *
     * @return `false` if the sequence is empty or written in flow style, neither of which can be appended to
     *         by adding a line
     */
    private fun appendToSequence(
        project: Project,
        document: Document,
        sequence: YAMLSequence,
        lines: List<String>,
    ): Boolean {
        if (sequence.text.trimStart().startsWith("[")) return false
        val last = sequence.items.lastOrNull() ?: return false
        val indent = columnOf(document, last.textRange.startOffset)
        insertLines(project, document, last, lines.map { "${" ".repeat(indent)}$it" })
        return true
    }

    /**
     * Inserts [lines] as whole lines behind the last line [anchor] occupies.
     *
     * @param project the project the document belongs to
     * @param document the document to change
     * @param anchor the element the lines are placed behind
     * @param lines the lines to insert, each carrying its full indentation
     */
    private fun insertLines(project: Project, document: Document, anchor: PsiElement, lines: List<String>) {
        if (lines.isEmpty()) return
        val reference = trimmedEndOf(document, anchor.textRange.endOffset)
        val offset = document.getLineEndOffset(document.getLineNumber(reference))
        document.insertString(offset, "\n" + lines.joinToString("\n"))
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /**
     * Deletes every line [element] occupies, including the line break ending the last of them.
     *
     * Deleting by line rather than by PSI keeps the file free of the blank line a PSI removal leaves behind,
     * and it takes a comment trailing the removed key with it — that comment described what is gone.
     *
     * @param project the project the document belongs to
     * @param document the document to change
     * @param element the element to remove
     */
    private fun deleteLines(project: Project, document: Document, element: PsiElement) {
        val range = element.textRange
        val from = document.getLineStartOffset(document.getLineNumber(range.startOffset))
        val endLine = document.getLineNumber(trimmedEndOf(document, range.endOffset))
        val to = minOf(document.getLineEndOffset(endLine) + 1, document.textLength)
        if (to <= from) return
        document.deleteString(from, to)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /**
     * Returns [offset] moved back over the white space in front of it.
     *
     * The text range of a YAML element can end on the line break behind it, and a line number taken from that
     * offset would already name the following line.
     *
     * @param document the document [offset] points into
     * @param offset the offset to normalize
     */
    private fun trimmedEndOf(document: Document, offset: Int): Int {
        var result = offset.coerceIn(0, document.textLength)
        while (result > 0 && document.charsSequence[result - 1].isWhitespace()) {
            result--
        }
        return result
    }

    /**
     * Returns the column [offset] sits in, which is the indentation of an element starting there.
     *
     * @param document the document [offset] points into
     * @param offset the offset to measure
     */
    private fun columnOf(document: Document, offset: Int): Int {
        val safe = offset.coerceIn(0, document.textLength)
        return safe - document.getLineStartOffset(document.getLineNumber(safe))
    }

    /**
     * Returns the key-value addressed by [segments], or `null` if the path does not exist or is blocked by a
     * value that is not a mapping.
     *
     * @param yamlFile the file to look in
     * @param segments the path, from the top level down
     */
    private fun keyValueAt(yamlFile: YAMLFile, segments: List<String>): YAMLKeyValue? {
        if (segments.isEmpty()) return null
        var current: YAMLMapping = MkDocsConfig.topLevelMapping(yamlFile) ?: return null
        for ((index, segment) in segments.withIndex()) {
            val keyValue = current.keyValues.firstOrNull { it.keyText.trim() == segment } ?: return null
            if (index == segments.lastIndex) return keyValue
            current = keyValue.value as? YAMLMapping ?: return null
        }
        return null
    }

    /**
     * Returns the sequence addressed by [segments], or `null` if there is none.
     *
     * @param yamlFile the file to look in
     * @param segments the path, from the top level down
     */
    private fun sequenceAt(yamlFile: YAMLFile, segments: List<String>): YAMLSequence? =
        keyValueAt(yamlFile, segments)?.value as? YAMLSequence

    /**
     * Builds a value node out of [raw], which must already be valid YAML for the right hand side of a key.
     *
     * @param project the project the node is generated for
     * @param raw the rendered value
     */
    private fun generateValue(project: Project, raw: String): YAMLValue? =
        YAMLElementGenerator.getInstance(project)
            .createDummyYamlWithText("k: $raw")
            .documents
            .firstNotNullOfOrNull { (it.topLevelValue as? YAMLMapping)?.keyValues?.firstOrNull()?.value }

    /**
     * Splits a dotted [path] into its segments, dropping the empty ones a stray dot would produce.
     *
     * @param path the dotted path
     */
    private fun splitPath(path: String): List<String> =
        path.split('.').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Returns the document of [configFile] with the PSI and the document brought in sync.
     *
     * Everything below works with offsets taken from the PSI, so a change still sitting in the PSI has to be
     * flushed into the document before those offsets mean anything.
     *
     * @param project the project [configFile] belongs to
     * @param configFile the configuration file to change
     */
    private fun syncedDocument(project: Project, configFile: VirtualFile): Document? {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val psiFile: YAMLFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return null
        val document = psiDocumentManager.getDocument(psiFile) ?: return null
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
        psiDocumentManager.commitDocument(document)
        return document
    }

    /**
     * Writes the changes made to [configFile] through to its document and to disk.
     *
     * Without this the change lives in the PSI only, and everything reading the file through the virtual file
     * system — the build, an external MkDocs run — would still see the previous content.
     *
     * @param project the project [configFile] belongs to
     * @param configFile the configuration file that was changed
     */
    private fun commit(project: Project, configFile: VirtualFile) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val psiFile: YAMLFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return
        val document = psiDocumentManager.getDocument(psiFile) ?: return
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
        psiDocumentManager.commitDocument(document)
        FileDocumentManager.getInstance().saveDocument(document)
    }

    /**
     * Backs [MkDocsConfigEditScope.addMappingTreeItem] without committing the document.
     *
     * The generalisation of [addMappingItemIn]: a value of an entry may be a [String], written as a scalar,
     * or a [List] of key-value pairs, written as a block mapping one level below the key. That second shape
     * is what `theme.palette` needs — a palette entry carries its `toggle` as a mapping of `icon` and `name`,
     * and a sequence entry holding only scalars cannot express it.
     *
     * @param project the project [configFile] belongs to
     * @param configFile an MkDocs configuration file
     * @param path the dotted path of the sequence
     * @param entries the keys and values of the new entry, in the order they are written
     * @return the zero based position the entry was appended at, or `-1` if it could not be written
     */
    internal fun addMappingTreeItemIn(
        project: Project,
        configFile: VirtualFile,
        path: String,
        entries: List<Pair<String, Any>>,
    ): Int {
        val segments = splitPath(path)
        if (segments.isEmpty() || entries.isEmpty()) return -1
        val document = syncedDocument(project, configFile) ?: return -1
        val yamlFile = MkDocsConfig.yamlFileOf(project, configFile) ?: return -1

        val lines = mutableListOf<String>()
        for ((key, value) in entries) {
            val prefix = if (lines.isEmpty()) "- " else "  "
            when (value) {
                is String -> lines += "$prefix$key: ${MkDocsSiteCreationService.yamlScalar(value)}"

                is List<*> -> {
                    val nested = value.filterIsInstance<Pair<*, *>>()
                    if (nested.isEmpty()) continue
                    lines += "$prefix$key:"
                    nested.forEach { (nestedKey, nestedValue) ->
                        val raw = MkDocsSiteCreationService.yamlScalar(nestedValue.toString())
                        lines += "${" ".repeat(INDENT_SIZE * 2)}$nestedKey: $raw"
                    }
                }
            }
        }
        if (lines.isEmpty()) return -1

        val keyValue = keyValueAt(yamlFile, segments)
        if (keyValue == null) {
            val body = listOf("${segments.last()}:") + lines.map { "${" ".repeat(INDENT_SIZE)}$it" }
            return if (insertInto(project, configFile, segments.dropLast(1), body)) 0 else -1
        }
        val sequence = keyValue.value
        return when {
            sequence is YAMLSequence -> {
                val index = sequence.items.size
                if (appendToSequence(project, document, sequence, lines)) index else -1
            }

            sequence == null -> {
                val indent = columnOf(document, keyValue.textRange.startOffset) + INDENT_SIZE
                insertLines(project, document, keyValue, lines.map { "${" ".repeat(indent)}$it" })
                0
            }

            else -> -1
        }
    }
}

/**
 * The operations of [MkDocsConfigWriter] bound to one project and one configuration file.
 *
 * Handed to the block of [MkDocsConfigWriter.edit]. None of these functions commits — the whole batch is
 * committed once when the block returns.
 *
 * @property project the project [configFile] belongs to
 * @property configFile the configuration file being changed
 */
class MkDocsConfigEditScope internal constructor(
    private val project: Project,
    private val configFile: VirtualFile,
) {

    /**
     * Sets the key addressed by the dotted [path] to the scalar [value].
     *
     * @param path the dotted path of the key
     * @param value the value to write, as the user entered it
     * @return `false` if a segment of the path is blocked by a non-mapping value
     */
    fun setNestedScalar(path: String, value: String): Boolean =
        MkDocsConfigWriter.setNestedScalarIn(project, configFile, path, value)

    /**
     * Sets the key addressed by the dotted [path] to the boolean [value].
     *
     * @param path the dotted path of the key
     * @param value the value to write
     * @return `false` if a segment of the path is blocked by a non-mapping value
     */
    fun setNestedBoolean(path: String, value: Boolean): Boolean =
        MkDocsConfigWriter.setNestedBooleanIn(project, configFile, path, value)

    /**
     * Removes the key addressed by the dotted [path], pruning the mappings it leaves empty.
     *
     * @param path the dotted path of the key to remove
     */
    fun removeNestedKey(path: String) {
        MkDocsConfigWriter.removeNestedKeyIn(project, configFile, path)
    }

    /**
     * Turns the scalar at [path] into a mapping holding that scalar under [key].
     *
     * @param path the dotted path of the key holding the scalar
     * @param key the key the previous scalar is moved under
     * @return `false` if the path does not exist or does not hold a scalar
     */
    fun promoteScalarToMapping(path: String, key: String): Boolean =
        MkDocsConfigWriter.promoteScalarToMappingIn(project, configFile, path, key)

    /**
     * Adds [value] to the scalar sequence at [path], creating the sequence if it is missing.
     *
     * @param path the dotted path of the sequence
     * @param value the entry to add, as the user entered it
     * @return `false` if the entry could not be written
     */
    fun addScalarItem(path: String, value: String): Boolean =
        MkDocsConfigWriter.addScalarItemIn(project, configFile, path, value)

    /**
     * Removes [value] from the scalar sequence at [path].
     *
     * @param path the dotted path of the sequence
     * @param value the entry to remove
     * @return `false` if there is no such sequence or it does not hold the value
     */
    fun removeScalarItem(path: String, value: String): Boolean =
        MkDocsConfigWriter.removeScalarItemIn(project, configFile, path, value)

    /**
     * Sets the entry [index] of the scalar sequence at [path] to [value].
     *
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry
     * @param value the value to write, as the user entered it
     */
    fun setScalarItem(path: String, index: Int, value: String) {
        MkDocsConfigWriter.setScalarItemIn(project, configFile, path, index, value)
    }

    /**
     * Appends a mapping entry built from [entries] to the sequence at [path].
     *
     * @param path the dotted path of the sequence
     * @param entries the keys and values of the new entry, in the order they are written
     * @return the zero based position the entry was appended at, or `-1` if it could not be written
     */
    fun addMappingItem(path: String, entries: List<Pair<String, String>>): Int =
        MkDocsConfigWriter.addMappingItemIn(project, configFile, path, entries)

    /**
     * Appends a mapping entry built from [entries] to the sequence at [path], allowing a nested block.
     *
     * A value of an entry is either a [String], written as a scalar, or a [List] of key-value pairs, written
     * as a block mapping one level below its key. The shape `theme.palette` needs: its entries carry the
     * `toggle` as a mapping of `icon` and `name`.
     *
     * @param path the dotted path of the sequence
     * @param entries the keys and values of the new entry, in the order they are written
     * @return the zero based position the entry was appended at, or `-1` if it could not be written
     */
    fun addMappingTreeItem(path: String, entries: List<Pair<String, Any>>): Int =
        MkDocsConfigWriter.addMappingTreeItemIn(project, configFile, path, entries)

    /**
     * Sets [key] of the mapping entry [index] of the sequence at [path] to [value].
     *
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry
     * @param key the key inside the entry
     * @param value the value to write, as the user entered it
     * @return `false` if there is no such entry or the entry is not a mapping
     */
    fun setItemScalar(path: String, index: Int, key: String, value: String): Boolean =
        MkDocsConfigWriter.setItemScalarIn(project, configFile, path, index, key, value)

    /**
     * Removes the entry [index] from the sequence at [path].
     *
     * @param path the dotted path of the sequence
     * @param index the zero based position of the entry to remove
     */
    fun removeItem(path: String, index: Int) {
        MkDocsConfigWriter.removeItemIn(project, configFile, path, index)
    }

    /**
     * Returns how many entries the sequence at [path] holds.
     *
     * @param path the dotted path of the sequence
     * @return the number of entries, or `0` if there is no sequence at the path
     */
    fun itemCount(path: String): Int = MkDocsConfigWriter.itemCount(project, configFile, path)
}
