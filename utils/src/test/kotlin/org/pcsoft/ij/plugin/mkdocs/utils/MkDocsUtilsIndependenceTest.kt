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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The guard on the one leaf of the dependency graph: `:utils` is what the plugin and every facet build on, so
 * it MUST NOT build on any of them. A single reference the other way round would make the shared helpers
 * unusable for the next facet, and the build file alone does not say so — a project dependency added by hand
 * compiles happily.
 *
 * Checked on the sources rather than on the byte code, because a mention in a KDoc link is a dependency the
 * moment the named class moves: the link stops resolving and the next reader adds an import to fix it.
 */
class MkDocsUtilsIndependenceTest {

    /**
     * Use case: someone adds a call, an import or a documentation link from `:utils` into the plugin, into the
     * facet API or into a facet. The build has to say so before the shared helpers stop being shareable.
     */
    @Test
    fun `no source of utils names another project`() {
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
            "these sources of :utils name another project, which would turn the leaf of the dependency " +
                    "graph into a branch:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private companion object {

        /** Where the production sources of this project live, relative to it. */
        const val SOURCE_ROOT = "src/main/kotlin/org/pcsoft/ij/plugin/mkdocs/utils"

        /**
         * The packages of the other projects, as they are spelled in an import or a KDoc link.
         *
         * Everything below the root package that is not `…mkdocs.utils` belongs to somebody else: `…mkdocs.api`
         * to the facet contract, `…mkdocs.material` and its like to a facet, the rest to the plugin.
         */
        val FORBIDDEN = listOf(
            "org.pcsoft.ij.plugin.mkdocs.api",
            "org.pcsoft.ij.plugin.mkdocs.material",
            "org.pcsoft.ij.plugin.mkdocs.module",
            "org.pcsoft.ij.plugin.mkdocs.services",
            "org.pcsoft.ij.plugin.mkdocs.types",
            "org.pcsoft.ij.plugin.mkdocs.reference",
            "org.pcsoft.ij.plugin.mkdocs.inspection",
            "org.pcsoft.ij.plugin.mkdocs.ui",
            "org.pcsoft.ij.plugin.mkdocs.MkDocs",
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
