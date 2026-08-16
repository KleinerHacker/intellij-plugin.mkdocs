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

package org.pcsoft.ij.plugin.mkdocs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * The guard behind the module split: the plugin must not reference an optional feature from Kotlin code.
 * Every hand-off runs through the `siteFeature` extension point, which is what lets a feature move into a
 * Gradle project of its own without the plugin following it.
 *
 * Checked on the sources rather than on the byte code, because a mention in a KDoc link is a dependency the
 * moment the class moves away — the link stops resolving and the next reader adds an import to fix it.
 */
class MkDocsCoreIndependenceTest {

    /**
     * Use case: someone adds a call, an import or a documentation link from the plugin into a feature. The
     * build has to say so before the module split turns it into an unresolvable reference.
     */
    @Test
    fun `no core source mentions a feature package`() {
        val offenders = coreSources()
            .filter { file -> FORBIDDEN.any { file.readText().contains(it) } }
            .map { it.path }

        assertTrue(
            "these plugin sources reference an optional feature, which the siteFeature extension point " +
                    "exists to avoid:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    /**
     * Returns every production Kotlin source of the plugin itself, leaving the features out.
     */
    private fun coreSources(): List<File> {
        val root = File(projectRoot(), SOURCE_ROOT)
        assertTrue("cannot find the sources at ${root.path}", root.isDirectory)
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains(FEATURE_DIRECTORY) }
            .toList()
    }

    /**
     * Returns the directory of the Gradle project.
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

    private companion object {

        /** Where the production sources of the plugin live, relative to the project. */
        const val SOURCE_ROOT = "src/main/kotlin/org/pcsoft/ij/plugin/mkdocs"

        /** The path fragment marking a source as belonging to a feature rather than to the plugin. */
        const val FEATURE_DIRECTORY = "/material/"

        /** What a plugin source must not name. */
        val FORBIDDEN = listOf("MkDocsMaterial", "mkdocs.material")
    }
}
