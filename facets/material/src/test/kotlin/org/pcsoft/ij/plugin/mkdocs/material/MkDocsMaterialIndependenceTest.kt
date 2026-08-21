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

package org.pcsoft.ij.plugin.mkdocs.material

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The counterpart of the guard the plugin carries: the plugin must not name this facet, and this facet must
 * not name the plugin. It reaches the plugin through the `siteFeature` extension point of `:facets:api` and
 * through nothing else, which is what lets the facet be built, tested and left out on its own.
 *
 * `:facets:api` and `:utils` are allowed and not listed below — they are what the facet is meant to build on.
 *
 * Checked on the sources rather than on the byte code, because a mention in a KDoc link is a dependency the
 * moment the named class moves: the link stops resolving and the next reader adds an import to fix it.
 */
class MkDocsMaterialIndependenceTest {

    /**
     * Use case: someone adds a call, an import or a documentation link from the facet into the plugin — the
     * detection service, the MkDocs facet, the icons or the bundle of the plugin. The build has to say so
     * before the facet stops being separable.
     */
    @Test
    fun `no source of the facet names the plugin`() {
        val root = File(projectRoot(), SOURCE_ROOT)
        assertTrue("cannot find the sources at ${root.path}", root.isDirectory)

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> FORBIDDEN.any { line.contains(it) } }
                    .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }
            .toList()

        assertTrue(
            "these sources of :facets:material name the plugin, which the siteFeature extension point " +
                    "exists to avoid:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private companion object {

        /** Where the production sources of this project live, relative to it. */
        const val SOURCE_ROOT = "src/main/kotlin/org/pcsoft/ij/plugin/mkdocs/material"

        /** The packages of the plugin, as they are spelled in an import or a KDoc link. */
        val FORBIDDEN = listOf(
            "org.pcsoft.ij.plugin.mkdocs.module",
            "org.pcsoft.ij.plugin.mkdocs.services",
            "org.pcsoft.ij.plugin.mkdocs.types",
            "org.pcsoft.ij.plugin.mkdocs.reference",
            "org.pcsoft.ij.plugin.mkdocs.inspection",
            "org.pcsoft.ij.plugin.mkdocs.ui",
            "org.pcsoft.ij.plugin.mkdocs.MkDocsIcons",
            "org.pcsoft.ij.plugin.mkdocs.MkDocsBundle",
        )
    }

    /**
     * Returns the directory of this Gradle project.
     *
     * The working directory of the test JVM is where Gradle starts it, which is the project itself; the walk
     * upwards keeps the test working if that ever changes.
     */
    private fun projectRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            if (File(candidate, SOURCE_ROOT).isDirectory) return candidate
            candidate = candidate.parentFile
        }
        throw AssertionError("cannot find a directory holding $SOURCE_ROOT")
    }
}
