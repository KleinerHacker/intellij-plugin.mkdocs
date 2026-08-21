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

// The *Material for MkDocs* facet. It depends on the contract and on the shared helpers, NEVER on the plugin:
// every hand-off runs through the `siteFeature` extension point of `:facets:api`.

plugins {
    id("mkdocs.module-conventions")
}

dependencies {
    implementation(project(":facets:api"))
    implementation(project(":utils"))

    intellijPlatform {
        // The configuration file the facet reads and writes is YAML …
        bundledPlugin("org.jetbrains.plugins.yaml")
        // … and the refined theme schema it contributes is JSON.
        bundledPlugin("com.intellij.modules.json")
        // The style sheets behind `extra_css` are read through the CSS PSI of the platform rather than with a
        // regular expression. Everything using it is registered in the optional content module of the facet,
        // so an IDE without the CSS plugin keeps working — the dependency here is a compile time one.
        bundledPlugin("com.intellij.css")
        // The CSS PSI lies in a content module of that plugin rather than in its jar, and a content module is
        // a class loader of its own. This mirrors the `<module name="intellij.css"/>` of the descriptor.
        bundledModule("intellij.css")
    }
}
