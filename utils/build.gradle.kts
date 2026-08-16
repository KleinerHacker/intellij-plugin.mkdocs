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

// The shared model and helpers of the plugin: what more than one facet needs and what carries no facet
// knowledge. A leaf of the dependency graph — it MUST NOT depend on any other project of this build.

plugins {
    id("mkdocs.module-conventions")
}

dependencies {
    intellijPlatform {
        // The configuration file of a site is YAML, and reading it is what most of this project does.
        bundledPlugin("org.jetbrains.plugins.yaml")
    }
}

//region Schema
// The MkDocs base schema is vendored into this project (src/main/resources/schema/mkdocs-1.6.json) because a
// bundled schema cannot reference a remote one: the platform resolves a $ref against the parent of the
// schema's own VirtualFile, and remote fetching is driven by SchemaType.remoteSchema and the catalogue, not
// by ref resolution. A remote $ref would therefore silently drop the whole base branch. It lives here rather
// than with the plugin because a facet refines it — the Material theme schema $refs this very file — and a
// facet must not reach into the plugin. This task refreshes the snapshot with a single command.
tasks.register("refreshMkDocsSchema") {
    group = "schema"
    description = "Re-download the SchemaStore MkDocs schema into utils/src/main/resources/schema/mkdocs-1.6.json"

    val source = "https://www.schemastore.org/mkdocs-1.6.json"
    val target = layout.projectDirectory.file("src/main/resources/schema/mkdocs-1.6.json").asFile
    outputs.upToDateWhen { false }

    doLast {
        val text = uri(source).toURL().openStream().use { it.readBytes().toString(Charsets.UTF_8) }
        check(text.isNotBlank()) { "Downloaded an empty schema from $source" }
        target.parentFile.mkdirs()
        target.writeText(text, Charsets.UTF_8)
        logger.lifecycle("Schema von $source aktualisiert: ${target.absolutePath}")
    }
}
//endregion
