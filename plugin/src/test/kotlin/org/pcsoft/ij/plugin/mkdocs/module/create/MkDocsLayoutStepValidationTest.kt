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

package org.pcsoft.ij.plugin.mkdocs.module.create

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteTemplateError
import java.nio.file.Files
import java.nio.file.Path

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the validation of the first wizard step against the real Swing fields, which is where the step and
 * the template rules meet.
 */
class MkDocsLayoutStepValidationTest : BasePlatformTestCase() {

    private lateinit var baseDirectory: Path

    override fun setUp() {
        super.setUp()
        baseDirectory = Files.createTempDirectory("mkdocs-step-test")
    }

    override fun tearDown() {
        try {
            baseDirectory.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: the ordinary input — a location that exists and a name, which the step appends to it. The
     * resulting directory does not exist yet, and that is exactly the state the wizard produces, so the step
     * must report no error at all.
     *
     * This is the regression guard for a validation that reported every *valid* input as an invalid
     * directory: folding "no error" together with "no template" into one elvis chain made the success case
     * indistinguishable from a missing template.
     */
    fun `test reports no error for a valid location and name`() {
        val step = MkDocsLayoutStep(project, baseDirectory.toString())
        step.setNameForTest("demo")

        val template = step.buildTemplate()
        assertNotNull("expected a template for valid input", template)
        assertEquals(baseDirectory.resolve("demo"), template!!.rootPath)
        assertNull("valid input must not be reported as an error", template.validate())
        assertNull("valid input must not be reported as an error", step.validate())
    }

    /**
     * Use case: the user has not typed a name yet. The step must say what is actually missing instead of
     * blaming the location, which is perfectly fine at that point. The name asked for here is a directory
     * name, so it must not be reported as a missing *site* name either.
     */
    fun `test reports the missing name rather than the location`() {
        val step = MkDocsLayoutStep(project, baseDirectory.toString())

        assertEquals(MkDocsSiteTemplateError.BLANK_NAME, step.validate())
    }

    /**
     * Use case: the target directory already holds an MkDocs configuration file. That is the one case the
     * step has to refuse outright, and it must be reported as such — not as an unusable path.
     */
    fun `test reports an existing site`() {
        Files.createDirectory(baseDirectory.resolve("demo"))
        Files.createFile(baseDirectory.resolve("demo").resolve("mkdocs.yml"))

        val step = MkDocsLayoutStep(project, baseDirectory.toString())
        step.setNameForTest("demo")

        assertEquals(MkDocsSiteTemplateError.SITE_EXISTS, step.validate())
    }

    /**
     * Use case: the wizard has to grey out *Next* while the input cannot produce a site. It learns about
     * every keystroke through this callback, so the callback has to fire for each field.
     */
    fun `test notifies about every input change`() {
        val step = MkDocsLayoutStep(project, baseDirectory.toString())
        var notifications = 0
        step.onInputChanged = { notifications++ }

        step.setNameForTest("demo")
        assertTrue("the name must notify", notifications > 0)

        val afterName = notifications
        step.setDocsDirNameForTest("sources")
        assertTrue("the documentation directory must notify", notifications > afterName)

        val afterDocs = notifications
        step.setAssetsDirNameForTest("media")
        assertTrue("the assets directory must notify", notifications > afterDocs)

        val afterAssets = notifications
        step.setStylesheetsDirNameForTest("css")
        assertTrue("the stylesheets directory must notify", notifications > afterAssets)

        assertNull("the changed input is still valid", step.validate())
        assertEquals("sources", step.buildTemplate()!!.docsDirName)
        assertEquals("media", step.buildTemplate()!!.assetsDirName)
        assertEquals("css", step.buildTemplate()!!.stylesheetsDirName)
    }

    /**
     * Use case: an emptied directory field. *Next* has to go grey, so the step must report the error rather
     * than quietly falling back to the default.
     */
    fun `test reports an emptied directory field`() {
        val step = MkDocsLayoutStep(project, baseDirectory.toString())
        step.setNameForTest("demo")

        step.setDocsDirNameForTest("")
        assertEquals(MkDocsSiteTemplateError.INVALID_DOCS_DIR, step.validate())

        step.setDocsDirNameForTest("docs")
        step.setAssetsDirNameForTest("a/b")
        assertEquals(MkDocsSiteTemplateError.INVALID_ASSETS_DIR, step.validate())

        step.setAssetsDirNameForTest("assets")
        step.setStylesheetsDirNameForTest("")
        assertEquals(MkDocsSiteTemplateError.INVALID_STYLESHEETS_DIR, step.validate())
    }

    /**
     * Use case: several levels of the location are missing at once, because the user typed a deeper path.
     * Everything missing is created with the site, so this is no error either.
     */
    fun `test accepts a location several levels below what exists`() {
        val step = MkDocsLayoutStep(project, baseDirectory.resolve("a").resolve("b").toString())
        step.setNameForTest("demo")

        assertNull(step.validate())
    }

    /**
     * Use case: the user edits the location after having typed a name. The site root is the combination of
     * both, so the template has to follow the changed location.
     */
    fun `test builds the root from location and name`() {
        val step = MkDocsLayoutStep(project, baseDirectory.toString())
        step.setNameForTest("demo")
        step.setLocationForTest(baseDirectory.resolve("nested").toString())

        assertEquals(baseDirectory.resolve("nested").resolve("demo"), step.buildTemplate()!!.rootPath)
    }
}
