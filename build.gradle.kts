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

import com.github.jk1.license.render.ReportRenderer
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask

// ─────────────────────────────────────────────────────────────────────────────────────────────
// The root project IS the publishable IntelliJ plugin: the plugin implementation, `META-INF/plugin.xml`,
// signing, publishing and the `pluginVerification` matrix, plus the quality gates (coverage, licences, SBOM)
// and the MkDocs documentation site. The code shared with the facets lives in the module projects, which are
// merged into the plugin jar as plugin modules below.
// ─────────────────────────────────────────────────────────────────────────────────────────────

plugins {
    // The Kotlin toolchain, the bytecode level and the developer/integration test split are shared with every
    // module project and therefore come from the convention plugin in `build-logic`.
    id("mkdocs.kotlin-conventions")
    alias(libs.plugins.changelog)
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.github.jk1.dependency-license-report") version "3.1.4"
    id("org.cyclonedx.bom") version "3.3.0"
}

intellijPlatform {
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }   // unbounded: covers 2026.2 (262) and future IDEs
        }
        // Release-time changelog injection is wired separately; keep patchPluginXml off the changelog
        // provider so it stays configuration-cache friendly.
        changeNotes = provider { "" }
    }

    // Authoritative IDE matrix for `verifyPlugin` (see .claude/rules/plugin.md). The version is
    // always read from the catalog so the verified IDEs can never drift away from the platform the plugin is
    // compiled against — bumping `idea` in libs.versions.toml bumps all of them at once.
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, libs.versions.idea.get())
            // Rider is not distributed in a form the installer path can consume, so it is taken as an archive.
            create(IntelliJPlatformType.Rider, libs.versions.idea.get()) { useInstaller = false }
            create(IntelliJPlatformType.CLion, libs.versions.idea.get())
            create(IntelliJPlatformType.GoLand, libs.versions.idea.get())
        }
    }

    signing {
        System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { keyStore = file(it) }
        keyStoreType = "PKCS12"
        System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }?.let { keyStoreKeyAlias = it }

        val pwFile = System.getenv("KEYSTORE_PASSWORD_FILE")?.takeIf { it.isNotBlank() }
        keyStorePassword = if (pwFile != null) {
            providers.fileContents(layout.file(provider { File(pwFile) })).asText.map { it.trim() }
        } else {
            providers.environmentVariable("KEYSTORE_PASSWORD")
        }
    }

    publishing {
        val tokenFile = System.getenv("PUBLISH_TOKEN_FILE")?.takeIf { it.isNotBlank() }
        token = if (tokenFile != null) {
            providers.fileContents(layout.file(provider { File(tokenFile) })).asText.map { it.trim() }
        } else {
            providers.environmentVariable("PUBLISH_TOKEN")
        }
        channels = listOf(providers.gradleProperty("publishChannel").getOrElse("default"))
    }
}

dependencies {
    // Jackson is NOT taken from Maven (libs.jackson.yaml / libs.jackson.kotlin stay unused in the catalog):
    // shipping those jars makes the Plugin Verifier report dozens of unresolved references inside their
    // multi-release internals. It needs no declaration at all — the platform dependency below already brings
    // it along, the same way it brings gson.

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // The projects carrying the code. `pluginComposedModule` puts each of them on the compile classpath
        // *and* merges its jar into the single plugin jar, classes and resources alike.
        //
        // Deliberately not `pluginModule`, which ships every project as a jar of its own under `lib/modules/`
        // named after the *Gradle* project (`mkdocs.facets.material.jar`). The V2 loader looks a content
        // module up by its own name — it reads `org.pcsoft.ij.plugin.mkdocs.material.xml` off the plugin
        // classpath — and fails with "Cannot resolve org.pcsoft.ij.plugin.mkdocs.material.xml" when the
        // descriptor sits in a jar it does not read. Merged into the plugin jar, all three module descriptors
        // lie in its root, which is exactly where the loader looks.
        pluginComposedModule(implementation(project(":utils")))
        pluginComposedModule(implementation(project(":facets:api")))
        pluginComposedModule(implementation(project(":facets:material")))

        // Single source of truth for the target IDE: a local IDE when configured (Gradle property
        // `localIdePath` or env `LOCAL_IDE_PATH`), otherwise the downloaded SDK. Pointing this at an IDE
        // whose build differs from the target version makes the platform tests hang during app boot.
        val localIdePath = (providers.gradleProperty("localIdePath").orNull
            ?: providers.environmentVariable("LOCAL_IDE_PATH").orNull)?.takeIf { it.isNotBlank() }
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            intellijIdea(libs.versions.idea.get())
        }
        testFramework(TestFrameworkType.Platform)

        // Plugin dependencies for compilation — these mirror the <depends> entries in plugin.xml.
        // Deliberately NOT org.jetbrains.kotlin: see the comment in plugin.xml.
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")

        // Optional at runtime (see the <depends optional="true"> entries in plugin.xml), but needed at compile
        // time: the SCM and copyright prefilling of the creation wizard talks to these plugins directly.
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.copyright")

        // Optional at runtime as well: the content module of the Material facet reading the style sheets
        // behind `extra_css` is loaded only where the CSS plugin is. Needed here so its tests — the ones
        // driving completion, references and the annotator, which have to run against a registered plugin —
        // have a CSS language to parse with.
        bundledPlugin("com.intellij.css")
        // The CSS PSI lies in a content module of that plugin rather than in its jar. Mirrors the
        // `<module name="intellij.css"/>` the CSS content module of the facet declares.
        bundledModule("intellij.css")

        // Git4Idea's GitRepository/GitRepositoryManager extend the DVCS base types, which live in a platform
        // module of their own. Without it Kotlin cannot even read the supertypes of what git4idea exposes.
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")

        // Since the platform bump to 2026.2 the core `intellij.spellchecker` module (pulled in transitively
        // via com.intellij.modules.lang) depends on `intellij.libraries.lucene.common`, which was moved out
        // of core lib/ into the bundled `intellij.libraries.misc.plugin`. Without it on the test classpath
        // the lucene module is unresolved, spellchecker(.xml) is excluded, and the whole test plugin gets
        // excluded ("dependency on 'IDEA CORE' which cannot be loaded") — every platform feature test then
        // fails with no language support.
        bundledPlugin("intellij.libraries.misc.plugin")

        // Nothing is declared here for Jackson on purpose. It arrives transitively with the platform
        // dependency above, exactly as gson does — both are IDE libraries the platform itself loads. Naming
        // them with `bundledLibrary(…)` compiles too, but the Gradle plugin warns against it: reaching into
        // the platform's own jars is not a supported dependency path.
    }
}

// The licence-report plugin holds on to the Project instance at execution time, which the configuration
// cache (enabled in gradle.properties) rejects outright. Opting the task out makes Gradle skip caching for
// builds that include it instead of failing them — `buildDocs` depends on it via `copyLicenceReport`.
tasks.named("generateLicenseReport") {
    notCompatibleWithConfigurationCache("com.github.jk1.dependency-license-report accesses Project at execution time")
}

licenseReport {
    outputDir = layout.buildDirectory.dir("licences").get().asFile.absolutePath
    configurations = arrayOf("runtimeClasspath")
    renderers = arrayOf<ReportRenderer>(
        com.github.jk1.license.render.JsonReportRenderer(),
        com.github.jk1.license.render.SimpleHtmlReportRenderer()
    )
}

// Coverage of the whole plugin, not of the plugin project alone: a class of `:utils` exercised by a test of
// `:facets:material` is covered, and the report has to say so. `koverVerify` therefore checks the sum of every
// project the plugin is built from — the same set that is merged into the published artifact.
dependencies {
    kover(project(":utils"))
    kover(project(":facets:api"))
    kover(project(":facets:material"))
}

tasks {
    register<SignPluginTask>("selfSignPlugin") {
        val signPluginTask = named<SignPluginTask>("signPlugin")
        group = "intellij platform"
        description = "Sign the plugin locally with the project's own PKCS#12 keystore (.signing/)."

        archiveFile.set(signPluginTask.flatMap { it.archiveFile })
        signedArchiveFile.set(signPluginTask.flatMap { it.signedArchiveFile })
        zipSignerExecutable.set(signPluginTask.flatMap { it.zipSignerExecutable })

        keyStore.set(layout.projectDirectory.file(".signing/keystore.p12"))
        keyStoreType.set("PKCS12")
        keyStoreKeyAlias.set(
            providers.gradleProperty("keyAlias")
                .orElse(providers.environmentVariable("KEY_ALIAS"))
                .orElse("mkdocs")
        )
        keyStorePassword.set(
            providers.gradleProperty("keyPassword")
                .orElse(providers.environmentVariable("KEYSTORE_PASSWORD"))
        )
    }

    //region Dokka
    register<Copy>("copyDokka") {
        group = "dokka"
        description = "Copy Dokka to MkDocs"
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
        from(File("build/licences"))
        into(File("docs/docs/licences"))
        dependsOn("generateLicenseReport")
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
