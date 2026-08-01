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
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Single-project build: the root project IS the publishable IntelliJ plugin. Everything lives here —
// the plugin itself, the quality gates (coverage, licences, SBOM), and the MkDocs documentation site.
// ─────────────────────────────────────────────────────────────────────────────────────────────

plugins {
    // kotlin.jvm and changelog are versioned via the catalog; the IntelliJ Platform project plugin gets
    // its version from the `intellij.platform.settings` plugin applied in settings.gradle.kts.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.changelog)
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("com.github.jk1.dependency-license-report") version "3.1.4"
    id("org.cyclonedx.bom") version "3.3.0"
    id("app.cash.licensee") version "1.14.1"
}

kotlin {
    // Compile with JDK 25: since IntelliJ 2026.2 the platform jars are Java 25 (class file 69), so an
    // older javac/kotlinc cannot even read them. The emitted bytecode is pinned to Java 21 below so the
    // plugin still loads on the whole supported IDE range (sinceBuild 262).
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
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
    // The IntelliJ Platform ships its own kotlin-stdlib as an IDE jar (not a resolved Gradle module), so it
    // does NOT participate in dependency conflict resolution. Transitive dependencies (jackson-module-kotlin
    // pulls kotlin-reflect → kotlin-stdlib) therefore decide the version on the test/runtime classpath. If
    // that resolves to a stdlib older than the compiler, the platform's coroutine debug probes crash with
    // "Debug metadata version mismatch", which kills the plugin-descriptor-loading workers and hangs every
    // platform test. Pin the Kotlin artifacts to the catalog version.
    constraints {
        implementation(libs.kotlin.stdlib)
        implementation(libs.kotlin.reflect)
    }

    testImplementation(libs.junit)

    // Jackson (libs.jackson.yaml / libs.jackson.kotlin) is deliberately NOT bundled yet: the plugin has no
    // code that needs it, and shipping it makes the Plugin Verifier report dozens of unresolved references
    // inside the jars' multi-release internals. Add it back here once something actually parses YAML —
    // the catalog entries and the Kotlin constraints above are already in place for that.

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
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
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")

        // Since the platform bump to 2026.2 the core `intellij.spellchecker` module (pulled in transitively
        // via com.intellij.modules.lang) depends on `intellij.libraries.lucene.common`, which was moved out
        // of core lib/ into the bundled `intellij.libraries.misc.plugin`. Without it on the test classpath
        // the lucene module is unresolved, spellchecker(.xml) is excluded, and the whole test plugin gets
        // excluded ("dependency on 'IDEA CORE' which cannot be loaded") — every platform feature test then
        // fails with no language support.
        bundledPlugin("intellij.libraries.misc.plugin")
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

plugins.withId("org.jetbrains.kotlin.jvm") {
    plugins.withId("app.cash.licensee") {
        extensions.configure<app.cash.licensee.LicenseeExtension> {
            listOf("Apache-2.0").forEach(::allow)
        }
    }
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
        // The project may legitimately contain no test of the selected kind.
        isFailOnNoMatchingTests = false
    }

    // Platform tests log through java.util.logging (JUL) via TestLoggerFactory — NOT log4j. The old
    // idea/log4j.xml + idea.log.level were silently ignored. Two independent knobs actually matter:
    //   1. intellij.console.log.level — the IntelliJ Platform Gradle plugin sets this to "warning" by
    //      default; it is the threshold of the JUL console handler that echoes WARN+ records to stdout.
    //      Override it to "off" to silence the console entirely (records still go to the per-test idea.log).
    //   2. idea.log.config.file — read as a JUL .properties file and loaded into the LogManager.
    systemProperty("intellij.console.log.level", "off")
    systemProperty("idea.log.config.file", "${rootDir}/gradle/test-logging.properties")
    // On a FAILED test the framework dumps the full buffered debug log (down to FINE/TRACE) somewhere.
    // Default target is stderr (floods the console); with this flag it goes to a per-test file under the
    // sandbox log dir and only a short "Log saved to: …" line is printed.
    systemProperty("idea.split.test.logs", "true")
    // Hard backstop so a hung test cannot stall the build indefinitely.
    timeout.set(Duration.ofMinutes(15))
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
