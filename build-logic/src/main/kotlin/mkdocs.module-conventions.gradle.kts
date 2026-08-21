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
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// ─────────────────────────────────────────────────────────────────────────────────────────────
// A module project: it compiles against the IntelliJ Platform and produces a plain jar, which the plugin
// project merges into the plugin it publishes. Everything specific to a single module — the bundled plugins
// it needs and the projects it depends on — stays in that module's own build file.
// ─────────────────────────────────────────────────────────────────────────────────────────────

plugins {
    id("mkdocs.kotlin-conventions")
    id("org.jetbrains.intellij.platform.module")
}

private val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    intellijPlatform {
        // Single source of truth for the target IDE, the same one the plugin project reads: a local IDE when
        // configured (Gradle property `localIdePath` or env `LOCAL_IDE_PATH`), otherwise the downloaded SDK.
        val localIdePath = (providers.gradleProperty("localIdePath").orNull
            ?: providers.environmentVariable("LOCAL_IDE_PATH").orNull)?.takeIf { it.isNotBlank() }
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            intellijIdea(catalog.findVersion("idea").get().requiredVersion)
        }
        testFramework(TestFrameworkType.Platform)

        // Since the platform bump to 2026.2 the core `intellij.spellchecker` module (pulled in transitively
        // via com.intellij.modules.lang) depends on `intellij.libraries.lucene.common`, which was moved out
        // of core lib/ into the bundled `intellij.libraries.misc.plugin`. Without it on the test classpath
        // the whole test plugin gets excluded and every platform feature test fails with no language support.
        bundledPlugin("intellij.libraries.misc.plugin")
    }
}
