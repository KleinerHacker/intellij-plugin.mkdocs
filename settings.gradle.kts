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

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

// Must stay the first block of this file — Gradle rejects a `pluginManagement` that anything precedes.
pluginManagement {
    // The convention plugins every project of this build is configured with.
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

rootProject.name = "mkdocs"

// Multi-project build: the root project IS the publishable plugin, the projects below carry the code.
//   :utils            shared model and helpers, no project dependency
//   :facets:api       the contract between the plugin and a facet
//   :facets:material  the Angular Material facet
include(":utils")
include(":facets:api")
include(":facets:material")

// Relocate the local build cache when GRADLE_BUILD_CACHE_DIR is set. On CI the `build` job populates that
// directory and the two test jobs restore it, so the test jobs reuse the compilation output of the build
// instead of recompiling everything themselves (see .github/actions/setup-gradle). A directory inside the
// workspace is used there because it must be handed to actions/cache, which cannot reach into the Gradle
// user home managed by actions/setup-java. Without the variable Gradle keeps its default location.
buildCache {
    local {
        System.getenv("GRADLE_BUILD_CACHE_DIR")?.takeIf { it.isNotBlank() }?.let { directory = file(it) }
        // Every job pushes: the build job fills the cache, the test jobs add whatever they compile on top.
        isPush = true
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        gradlePluginPortal()

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
