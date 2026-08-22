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

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Root project: pure aggregator. It contains NO production code and NO plugin descriptor.
//   • The publishable plugin lives in   :plugin
//   • The shared model and helpers in   :utils
//   • The facet contract in             :facets:api
//   • One project per facet under       :facets:<facet-name>
//
// The root only aggregates cross-project concerns: Dokka (over every project), the coverage merge, the
// licence report hand-off and the MkDocs documentation site.
//
// The plugin deliberately is NOT the root project any more. The licence-report plugin reports over
// `project + subprojects`; with the plugin at the root that dragged :utils and the facets in, and resolving
// a foreign project's `runtimeClasspath` at execution time fails under Gradle 9 with "Resolution of the
// configuration ':utils:runtimeClasspath' was attempted without an exclusive lock".
// ─────────────────────────────────────────────────────────────────────────────────────────────

// The build script class loaders are hierarchical: the one of this root project is the PARENT of the one
// every project below builds its script with, and class loading is parent first. `org.cyclonedx.bom` in
// `:plugin` brings jackson-dataformat-xml 2.15.3 along, while the IntelliJ Platform Gradle plugin — resolved
// in the settings scope, above this one — puts jackson-databind 2.22.1 on the classpath. Mixed that way
// `cyclonedxBom` dies with "Class …XmlBeanSerializerBase does not have member field …_anyGetterWriter": in
// 2.22.x `BeanSerializerBase` moved to `ser.std` and lost that field, which only the 2.22.x XML module knows.
// Naming the matching XML module here puts it into the parent loader, where it shadows the outdated one.
// The version MUST stay the one jackson-databind resolves to on the build classpath.
buildscript {
    dependencies {
        classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.22.2")
    }
}

plugins {
    // Applied to NOTHING here — the root compiles no Kotlin. It is named only to put the Kotlin plugin on
    // the classpath of the root, which every project below inherits. Without it the plugin is loaded once
    // per project ("The Kotlin Gradle plugin was loaded multiple times in different subprojects") and Dokka
    // cannot see KotlinBasePlugin in the projects it aggregates, so it reports no Kotlin source set at all.
    alias(libs.plugins.kotlin.jvm) apply false
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

dependencies {
    // Dokka MUST aggregate from the root — otherwise the generated API docs would only cover whichever
    // single project applied Dokka. Every project applies it through `mkdocs.kotlin-conventions`; the root
    // pulls them together into one publication.
    dokka(project(":plugin"))
    dokka(project(":utils"))
    dokka(project(":facets:api"))
    dokka(project(":facets:material"))

    // Coverage of the whole plugin, not of a single project: a class of `:utils` exercised by a test of
    // `:facets:material` is covered, and the report has to say so. `koverVerify` therefore checks the sum of
    // every project the plugin is built from — the same set that is merged into the published artefact.
    kover(project(":plugin"))
    kover(project(":utils"))
    kover(project(":facets:api"))
    kover(project(":facets:material"))
}

tasks {
    //region Dokka
    register<Copy>("copyDokka") {
        group = "dokka"
        description = "Copy aggregated Dokka to MkDocs"
        from(File("build/dokka"))
        into(File("docs/docs/dokka"))
        dependsOn("dokkaGeneratePublicationHtml")
    }

    register<Delete>("deleteDokka") {
        group = "dokka"
        description = "Delete Dokka"
        delete(File("docs/docs/dokka"))
    }
    //endregion

    //region Licencing
    register<Copy>("copyLicenceReport") {
        group = "licencing"
        description = "Copy licence report to MkDocs"
        from(File("plugin/build/licences"))
        into(File("docs/docs/licences"))
        dependsOn(":plugin:generateLicenseReport")
    }

    register<Delete>("deleteLicenceReport") {
        group = "licencing"
        description = "Delete licence report"
        delete(File("docs/docs/licences"))
    }
    //endregion

    //region MkDocs
    // mike spawns `mkdocs` as a subprocess; on Windows the Python Scripts dir
    // (where mkdocs.exe lives) is often not on PATH. Resolve it once and prepend
    // it to PATH for the mike tasks. In CI (setup-python) it is already on PATH.
    val pythonScriptsDir: String? by lazy {
        runCatching {
            providers.exec {
                commandLine("python", "-c", "import sysconfig; print(sysconfig.get_path('scripts'))")
            }.standardOutput.asText.get().trim().ifEmpty { null }
        }.getOrNull()
    }

    fun Exec.withMikePath() {
        pythonScriptsDir?.let { dir ->
            environment("PATH", dir + File.pathSeparator + System.getenv("PATH"))
        }
    }

    register<Exec>("installMkDocs") {
        group = null
        description = "Install mkdocs"
        workingDir = file("docs")
        commandLine("python", "-m", "pip", "install", "--upgrade", "mkdocs")
    }

    register<Exec>("installMkDocsMaterial") {
        group = null
        description = "Install mkdocs-material"
        workingDir = file("docs")
        commandLine("python", "-m", "pip", "install", "--upgrade", "mkdocs-material")
    }

    register<Exec>("installGitHubPages") {
        group = null
        description = "Install ghp-import"
        workingDir = file("docs")
        commandLine("python", "-m", "pip", "install", "--upgrade", "ghp-import")
    }

    register<Exec>("installMike") {
        group = null
        description = "Install mike for versioned docs deployment"
        workingDir = file("docs")
        commandLine("python", "-m", "pip", "install", "--upgrade", "mike")
    }

    register<Exec>("installI18N") {
        group = null
        description = "Install i18n"
        workingDir = file("docs")
        commandLine("python", "-m", "pip", "install", "--upgrade", "mkdocs-static-i18n")
    }

    register("installDocs") {
        group = "MKDocs"
        description = "Install mkdocs and dependencies"
        dependsOn("installMkDocs")
        dependsOn("installMkDocsMaterial")
        dependsOn("installGitHubPages")
        dependsOn("installI18N")
        dependsOn("installMike")
    }

    register<Exec>("runDocs") {
        group = "MKDocs"
        description = "Run mkdocs serve and open browser (no version selector — that only appears on the deployed site)"
        workingDir = file("docs")
        commandLine("python", "-m", "mkdocs", "serve", "-o", "-w", ".", "-w", "./docs")
        dependsOn("installDocs", "copyDokka", "copyLicenceReport")
        finalizedBy("deleteDokka", "deleteLicenceReport")
    }

    register<Exec>("buildDocs") {
        group = "MKDocs"
        description =
            "Build the mkdocs site into build/docs (per mkdocs.yml site_dir; no serve, no deploy) — usable as a generation test"
        workingDir = file("docs")
        // --strict fails the build on warnings (broken links, missing pages …) so it acts as a test;
        // --clean wipes the previous output first.
        commandLine("python", "-m", "mkdocs", "build", "--clean", "--strict")
        dependsOn("installDocs", "copyDokka", "copyLicenceReport")
        finalizedBy("deleteDokka", "deleteLicenceReport")
    }

    register<Exec>("deployDocs") {
        group = "MKDocs"
        description =
            "Deploy a versioned docs snapshot via mike. Requires -Pversion=<tag> and a pre-configured git push target."
        workingDir = file("docs")
        val ver = (project.findProperty("version") as String?)
            ?: error("Pass -Pversion=<tag> to deployDocs")
        val setLatest = (project.findProperty("setLatest") as String?) != "false"
        val args = buildList {
            add("python"); add("-c"); add("from mike.driver import main; main()"); add("deploy"); add("--push")
            // Materialise the 'latest' alias as a full copy, not mike's default symlink:
            // GitHub Pages does not resolve git symlinks reliably, and the gh-pages root
            // redirect points at latest/, so it must be a real directory.
            if (setLatest) {
                add("--alias-type"); add("copy"); add("--update-aliases"); add(ver); add("latest")
            } else add(ver)
        }
        commandLine(args)
        withMikePath()
        dependsOn("installDocs", "copyDokka", "copyLicenceReport")
        finalizedBy("deleteDokka", "deleteLicenceReport")
    }

    register<Exec>("setDefaultDocs") {
        group = "MKDocs"
        description =
            "Set the default docs version shown at the root URL via mike (run once after the first release deploy)."
        workingDir = file("docs")
        commandLine("python", "-c", "from mike.driver import main; main()", "set-default", "--push", "latest")
        withMikePath()
        dependsOn("installDocs")
    }
    //endregion
}
