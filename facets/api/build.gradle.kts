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

// The contract between the plugin and a facet: the extension point, the wizard step and the DTOs of their
// signatures. Nothing else belongs here — no implementation, no service, no UI.

plugins {
    id("mkdocs.module-conventions")
}

dependencies {
    // Compile time only, and deliberately so: `MkDocsSiteTemplate.validate` checks the directory names with
    // `MkDocsProject`, which every real user of this API — the plugin and every facet — already carries. The
    // API therefore compiles against `:utils` without dragging it into anyone's runtime classpath.
    compileOnly(project(":utils"))
    testImplementation(project(":utils"))

    intellijPlatform {
        // `MkDocsSiteFeature.schemaProvider` hands out a JsonSchemaFileProvider.
        bundledPlugin("com.intellij.modules.json")
    }
}
