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

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

// ─────────────────────────────────────────────────────────────────────────────────────────────
// What every project of this build compiles and tests the same way: the Kotlin toolchain, the emitted
// bytecode level and the split between developer and integration tests. Applied by the plugin project itself
// and, through `mkdocs.module-conventions`, by every module project.
// ─────────────────────────────────────────────────────────────────────────────────────────────

plugins {
    id("org.jetbrains.kotlin.jvm")
    // The two quality gates every project of this build is measured by. Coverage is aggregated by the plugin
    // project, which needs the plugin applied in every project it aggregates; the licence check reports per
    // project, because a dependency pulled in by a module is shipped just as much as one of the plugin.
    id("org.jetbrains.kotlinx.kover")
    id("app.cash.licensee")
}

private val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Coordinates of a catalog library, as `group:name:version`. */
fun catalogLibrary(alias: String): String = catalog.findLibrary(alias).get().get().toString()

kotlin {
    // Compile with JDK 25: since IntelliJ 2026.2 the platform jars are Java 25 (class file 69), so an
    // older javac/kotlinc cannot even read them. The emitted bytecode is pinned to Java 21 below so the
    // plugin still loads on the whole supported IDE range (sinceBuild 262).
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Inherit Java default methods instead of generating an override for each of them. Without this a
        // class implementing a platform interface silently overrides *every* default method it declares,
        // including the deprecated ones — which the plugin verifier reports as a deprecation of ours.
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// Everything shipped with the plugin has to be Apache-2.0, whichever project pulls it in.
licensee {
    allow("Apache-2.0")
}

dependencies {
    // The IntelliJ Platform ships its own kotlin-stdlib as an IDE jar (not a resolved Gradle module), so it
    // does NOT participate in dependency conflict resolution. Transitive dependencies therefore decide the
    // version on the test/runtime classpath; an older stdlib than the compiler crashes the platform's
    // coroutine debug probes with "Debug metadata version mismatch" and hangs every platform test.
    constraints {
        add("implementation", catalogLibrary("kotlin-stdlib"))
        add("implementation", catalogLibrary("kotlin-reflect"))
    }

    add("testImplementation", catalogLibrary("junit"))
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Developer tests vs. integration tests
//
// Project-wide convention: a test class whose name ends in `IT` is an *integration test* — it exercises a
// shipped artifact or the interplay of several layers, and may measure time. Everything else is a
// *developer test* and must stay fast, because it is the inner feedback loop.
//
//     ./gradlew test                         → everything (what `check` runs)
//     ./gradlew test -PtestSuite=developer   → every class NOT named *IT
//     ./gradlew test -PtestSuite=integration → only classes named *IT
//
// Deliberately a filter on the existing `test` task rather than a second Test task: the IntelliJ Platform
// Gradle plugin configures `test` extensively (sandbox, IDE system properties, platform classpath) and a
// separately registered task inherits none of it — it starts a bare JVM and finds no platform tests at all.
// ─────────────────────────────────────────────────────────────────────────────────────────────
val testSuite: String? = providers.gradleProperty("testSuite").orNull

tasks.withType<Test>().configureEach {
    // Without this a second invocation on the same machine would be UP-TO-DATE and skip its suite.
    inputs.property("testSuite", testSuite ?: "all")

    filter {
        when (testSuite) {
            "developer" -> excludeTestsMatching("*IT")
            "integration" -> includeTestsMatching("*IT")
            null -> Unit
            else -> throw GradleException(
                "Unknown -PtestSuite=$testSuite (expected 'developer' or 'integration')"
            )
        }
        // A project may legitimately contain no test of the selected kind.
        isFailOnNoMatchingTests = false
    }

    // Platform tests log through java.util.logging (JUL) via TestLoggerFactory — NOT log4j. Two independent
    // knobs matter: the console threshold of the JUL handler, and the JUL .properties file itself.
    systemProperty("intellij.console.log.level", "off")
    systemProperty("idea.log.config.file", "${rootDir}/gradle/test-logging.properties")
    // On a FAILED test the framework dumps the full buffered debug log; with this flag it goes to a per-test
    // file under the sandbox log dir instead of flooding stderr.
    systemProperty("idea.split.test.logs", "true")
    // Hard backstop so a hung test cannot stall the build indefinitely.
    timeout.set(Duration.ofMinutes(15))
}
