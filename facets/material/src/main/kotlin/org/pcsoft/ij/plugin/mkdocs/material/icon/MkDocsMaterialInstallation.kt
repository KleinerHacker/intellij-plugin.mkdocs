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

import java.io.File

/**
 * What an installed *Material for MkDocs* looks like on disk, and what can be read out of it.
 *
 * A directory is not an installation because it is named like one. pip writes a `*.dist-info` directory next
 * to every package it installs, and that directory is the proof: `METADATA` names the distribution, `RECORD`
 * lists every file the distribution brought along. Both are checked before a directory a user chose by hand
 * is accepted, because the alternative — accepting anything and offering no icons afterwards — leaves the
 * user with an empty completion popup and nothing to read it against.
 *
 * `RECORD` is what the icons themselves are read from as well. Walking `material/templates/.icons` would
 * find the same files most of the time, but the list of an installation is the one the installation itself
 * wrote: a file left behind by an uninstalled version is not part of it, and a directory that only looks
 * like the icon sets holds no `RECORD` at all.
 *
 * Read through [java.io.File] rather than through the VFS: the directory lies outside the project, a
 * settings page asks about it while the user is typing, and a synchronous VFS refresh is not something
 * either place can afford.
 */
object MkDocsMaterialInstallation {

    /** What the `*.dist-info` directory of this distribution is named after. */
    private const val DIST_INFO_PREFIX = "mkdocs_material-"

    /** The suffix of every directory pip writes its metadata into. */
    private const val DIST_INFO_SUFFIX = ".dist-info"

    /** The file naming the distribution. */
    private const val METADATA = "METADATA"

    /** The file listing what the distribution installed. */
    private const val RECORD = "RECORD"

    /** The line of [METADATA] carrying the name of the distribution. */
    private const val NAME_LINE = "Name: mkdocs-material"

    /** The path of the icon sets inside the installation directory, as [RECORD] writes it. */
    private const val ICONS_PREFIX = "material/templates/.icons/"

    /** The extension of the icon files. */
    private const val EXTENSION = ".svg"

    /**
     * Returns what is wrong with the installation directory [location], or `null` if nothing is.
     *
     * @param location the directory pip reports as its `Location`, holding both the package and its metadata
     */
    fun problemOf(location: String): Problem? {
        val directory = File(location.trim())
        if (location.isBlank() || !directory.isDirectory) return Problem.NO_DIRECTORY
        val distInfo = distInfoOf(directory) ?: return Problem.NO_DIST_INFO
        val metadata = File(distInfo, METADATA)
        if (!metadata.isFile || !namesTheDistribution(metadata)) return Problem.WRONG_NAME
        val record = File(distInfo, RECORD)
        if (!record.isFile || !isReadableRecord(record)) return Problem.NO_RECORD
        return null
    }

    /**
     * Returns the names of the icons the installation at [location] brought along, sorted.
     *
     * The names are the ones the theme addresses an icon by — `material/check`,
     * `fontawesome/brands/github` — which is the path below the icon sets without its extension.
     *
     * @param location the directory pip reports as its `Location`
     */
    fun iconNames(location: String): List<String> {
        val record = recordOf(location) ?: return emptyList()
        return record.readLines()
            .map { it.substringBefore(',').trim().replace('\\', '/') }
            .filter { it.startsWith(ICONS_PREFIX) && it.endsWith(EXTENSION, ignoreCase = true) }
            .map { it.removePrefix(ICONS_PREFIX).dropLast(EXTENSION.length) }
            .sorted()
    }

    /**
     * Returns the `RECORD` of the installation at [location], or `null` if there is none to read.
     *
     * @param location the directory pip reports as its `Location`
     */
    private fun recordOf(location: String): File? {
        if (problemOf(location) != null) return null
        return distInfoOf(File(location.trim()))?.let { File(it, RECORD) }
    }

    /**
     * Returns the `*.dist-info` directory of this distribution below [directory], or `null` if there is none.
     *
     * @param directory the installation directory being looked into
     */
    private fun distInfoOf(directory: File): File? = directory.listFiles()
        ?.firstOrNull { it.isDirectory && it.name.startsWith(DIST_INFO_PREFIX) && it.name.endsWith(DIST_INFO_SUFFIX) }

    /**
     * Returns whether [metadata] names this distribution.
     *
     * @param metadata the `METADATA` file of the `*.dist-info` directory
     */
    private fun namesTheDistribution(metadata: File): Boolean =
        readText(metadata)?.lineSequence()?.any { it.trim().equals(NAME_LINE, ignoreCase = true) } == true

    /**
     * Returns whether [record] reads as the text pip writes: lines of comma separated fields.
     *
     * A file that cannot be decoded, or one holding no such line, is not the listing of an installation — and
     * accepting it would mean offering no icons without ever saying why.
     *
     * @param record the `RECORD` file of the `*.dist-info` directory
     */
    private fun isReadableRecord(record: File): Boolean {
        val text = readText(record) ?: return false
        return text.lineSequence().any { it.contains(',') && it.substringBefore(',').isNotBlank() }
    }

    /**
     * Returns the text of [file], or `null` if it cannot be read as text at all.
     *
     * @param file the file being read
     */
    private fun readText(file: File): String? = try {
        file.readText().takeIf { REPLACEMENT !in it }
    } catch (_: Exception) {
        // Unreadable, gone between the two calls, or not text: all of them mean the same to the caller.
        null
    }

    /** The character a decoder writes for bytes that are not text. */
    private const val REPLACEMENT = '�'

    /**
     * What can be wrong with a directory a user chose as the installation.
     *
     * The message belongs to the resource bundle of the feature, not here — this is the finding, and the
     * settings page words it.
     */
    enum class Problem {

        /** There is no directory at the chosen path. */
        NO_DIRECTORY,

        /** The directory holds no `*.dist-info` of this distribution. */
        NO_DIST_INFO,

        /** The metadata is missing or names another distribution. */
        WRONG_NAME,

        /** The listing of the installed files is missing or unreadable. */
        NO_RECORD,
    }
}
