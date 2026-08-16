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

plugins {
    `kotlin-dsl`
}

// A precompiled script plugin may only apply plugins that are on the class path of this build. The Kotlin and
// IntelliJ Platform versions are taken from the shared catalog so they cannot differ from what the plugin
// project itself is built with; the two quality gates carry their version here, next to the conventions that
// apply them.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation(
        "org.jetbrains.intellij.platform:intellij-platform-gradle-plugin:${libs.versions.intellij.platform.get()}"
    )
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.9")
    implementation("app.cash.licensee:licensee-gradle-plugin:1.14.1")
}
