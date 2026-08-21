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

package org.pcsoft.ij.plugin.mkdocs.material.css

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialInstalledTheme

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the reading of `theme.palette` against the style sheets behind `extra_css`. Both halves describe the
 * same colours, and neither file shows the other: a `custom` with nothing behind it leaves the theme painting
 * its own ground colour, and a named colour a style sheet redefines leaves the site painted by whichever of
 * the two the browser reaches last.
 *
 * The scope of a definition is what most of this is about — `:root` counts for every palette, a rule below
 * `[data-md-color-scheme="…"]` for exactly one — so every case is driven with the ground of the palette
 * spelled out.
 */
class MkDocsMaterialPaletteCssAnnotatorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The light fixture hands every test of a class the same project, and pip must not answer for the
        // machine the build runs on: a developer with mkdocs-material installed would otherwise get the
        // grounds out of a real package and a different answer than the build server.
        MkDocsMaterialInstalledTheme.uninstall(project)
    }

    override fun tearDown() {
        try {
            MkDocsMaterialInstalledTheme.uninstall(project)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Use case: a mistyped ground. The theme writes the name into `data-md-color-scheme`, no rule of any
     * style sheet matches it, and the site is painted as if nothing had been asked for — which is invisible
     * in the file and was invisible in the editor.
     */
    fun `test marks a scheme no style sheet paints`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)

        val problems = schemeProblemsOf("slaet", css = "")

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("slaet"))
    }

    /**
     * Use case: a ground the installed theme paints itself. `slate` is a rule of the style sheet the package
     * ships, so nothing is wrong with a site standing on it.
     */
    fun `test stays quiet on a ground the theme paints`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)

        assertEmpty(schemeProblemsOf(MkDocsMaterialInstalledTheme.SCHEMES.last(), css = ""))
    }

    /**
     * Use case: a ground of the author's own, painted in a style sheet behind `extra_css`. It exists exactly
     * as the theme's own do, and must not be told apart from them here.
     */
    fun `test stays quiet on a ground the site paints itself`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)

        assertEmpty(
            schemeProblemsOf("sepia", css = """[data-md-color-scheme="sepia"] { color: #333333; }""")
        )
    }

    /**
     * Use case: the same mistyped ground while no installation can be read. The grounds of the theme are
     * known without one — they are named out of the model — so the judgement has to work all the same. A
     * check that went silent whenever the IDE had not found the package would be off in every project whose
     * environment is not set up, which is where a mistyped ground is written in the first place.
     */
    fun `test marks an unknown ground while the installation is unknown`() {
        val problems = schemeProblemsOf("slaet", css = "")

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("slaet"))
    }

    /**
     * Use case: a ground of the theme while no installation can be read. `slate` without a style sheet of
     * one's own is the documented way to a dark site, and marking it would report the most ordinary Material
     * configuration there is.
     */
    fun `test stays quiet on a ground of the theme without an installation`() {
        assertEmpty(schemeProblemsOf(MkDocsMaterialScheme.SLATE.id, css = ""))
    }

    /**
     * Use case: the severity of that finding. The name points at a rule that is nowhere, which is not a
     * matter of taste — and it is drawn the way an unresolved reference is, because that is what it is.
     */
    fun `test reports an unknown ground as an unresolved name`() {
        MkDocsMaterialInstalledTheme.installStyleSheets(project)

        val problem = schemeProblemsOf("slaet", css = "").first()

        assertEquals(HighlightSeverity.ERROR, problem.severity)
        // What paints the name itself rather than putting a wave under it, which is the whole point of
        // reporting it as an unknown symbol.
        assertEquals(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES, problem.type.attributesKey)
    }

    /**
     * Returns what the annotator reports about a site standing on [scheme] whose style sheet is [css].
     *
     * @param scheme the value written behind `theme.palette.scheme`
     * @param css the content of the style sheet behind `extra_css`
     */
    private fun schemeProblemsOf(scheme: String, css: String): List<HighlightInfo> {
        myFixture.addFileToProject("docs/stylesheets/extra.css", css)
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: material
              palette:
                scheme: $scheme
            """.trimIndent() + "\n",
        )
        return myFixture.doHighlighting().filter { it.description?.contains("colour scheme") == true }
    }

    /**
     * Use case: `primary: custom` and a style sheet that defines the custom property globally. The site is
     * painted the way it reads, and nothing may be reported.
     */
    fun `test stays quiet on custom with a global definition`() {
        assertEmpty(
            problemsOf(
                palette = "primary: custom",
                css = ":root { --md-primary-fg-color: #EE0F0F; }",
            )
        )
    }

    /**
     * Use case: `primary: custom` while no style sheet defines the property at all. Nothing sets the colour
     * then and the theme falls back to its own, which is invisible in either file.
     */
    fun `test reports custom without any definition`() {
        val problems = problemsOf(
            palette = "primary: custom",
            css = ":root { --md-accent-fg-color: #EE0F0F; }",
        )

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("--md-primary-fg-color"))
    }

    /**
     * Use case: `primary: custom` on a palette standing on `slate`, while the property is defined below
     * `[data-md-color-scheme="default"]`. The definition exists, but not for this ground — the palette is as
     * unpainted as if there were none.
     */
    fun `test reports custom whose definition belongs to another scheme`() {
        val problems = problemsOf(
            palette = "scheme: slate\nprimary: custom",
            css = """[data-md-color-scheme="default"] { --md-primary-fg-color: #EE0F0F; }""",
        )

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("slate"))
    }

    /**
     * Use case: the same palette with the definition below its own ground. Nothing is wrong there.
     */
    fun `test stays quiet on custom defined for its own scheme`() {
        assertEmpty(
            problemsOf(
                palette = "scheme: slate\nprimary: custom",
                css = """[data-md-color-scheme="slate"] { --md-primary-fg-color: #EE0F0F; }""",
            )
        )
    }

    /**
     * Use case: a named colour and a style sheet redefining the very property the theme paints it through.
     * Both halves are legal, and which of them the site ends up in cannot be read off either.
     */
    fun `test reports a named colour a style sheet redefines`() {
        val problems = problemsOf(
            palette = "primary: indigo",
            css = ":root { --md-primary-fg-color: #EE0F0F; }",
        )

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("indigo"))
        assertTrue(problems.first().description!!.contains("extra.css"))
    }

    /**
     * Use case: a named colour and a style sheet that touches the other one of the two properties. The two
     * keys are painted through names of their own, and one says nothing about the other.
     */
    fun `test stays quiet on a named colour whose property is untouched`() {
        assertEmpty(
            problemsOf(
                palette = "primary: indigo",
                css = ":root { --md-accent-fg-color: #EE0F0F; }",
            )
        )
    }

    /**
     * Use case: a named colour of a palette on `default`, redefined below `[data-md-color-scheme="slate"]`.
     * That rule paints the other palette of the toggle, so this one is untouched.
     */
    fun `test stays quiet on a named colour redefined for another scheme`() {
        assertEmpty(
            problemsOf(
                palette = "primary: indigo",
                css = """[data-md-color-scheme="slate"] { --md-primary-fg-color: #EE0F0F; }""",
            )
        )
    }

    /**
     * Use case: the `accent` of the same palette. It is painted through a name of its own, and the key has to
     * be judged as much as `primary` is.
     */
    fun `test reads the accent colour as well`() {
        val problems = problemsOf(
            palette = "accent: cyan",
            css = ":root { --md-accent-fg-color: #EE0F0F; }",
        )

        assertSize(1, problems)
        assertTrue(problems.first().description!!.contains("--md-accent-fg-color"))
    }

    /**
     * Use case: the severity of the finding. Both cases are legal CSS and legal MkDocs and there are sites
     * whose author means them, so neither may be reported as an error.
     */
    fun `test reports the finding as a warning`() {
        val problems = problemsOf(
            palette = "primary: indigo",
            css = ":root { --md-primary-fg-color: #EE0F0F; }",
        )

        assertTrue(problems.all { it.severity == HighlightSeverity.WARNING })
    }

    /**
     * Use case: a site whose `extra_css` names no style sheet at all. Nothing has been said about its colours,
     * so a `custom` there is a site that is merely not finished — not one that is wrong.
     */
    fun `test stays quiet while the site styles nothing`() {
        myFixture.configureByText(
            "mkdocs.yml",
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: custom
            """.trimIndent() + "\n",
        )

        assertEmpty(reported())
    }

    /**
     * Use case: a site that is not rendered with the Material theme. `theme.palette` is the theme's own key,
     * and a site on another theme has no business being judged by it.
     */
    fun `test stays away from a site that is not on the Material theme`() {
        assertEmpty(problemsOf(palette = "primary: custom", css = "", theme = "readthedocs"))
    }

    /**
     * Use case: a YAML file that is no configuration file of MkDocs, holding the very content that gets
     * marked under the name of one. Its name decides, exactly as everywhere else in the plugin.
     */
    fun `test stays quiet in a YAML file that is not a configuration file`() {
        assertEmpty(problemsOf(palette = "primary: custom", css = "", name = "other.yml"))
    }

    /**
     * Returns what the annotator reports for a site whose palette is [palette] and whose style sheet is [css].
     *
     * @param palette the lines below `theme.palette`, indented as they are written there
     * @param css the content of the style sheet behind `extra_css`
     * @param theme the theme of the site
     * @param name the file name to write the configuration under
     */
    private fun problemsOf(
        palette: String,
        css: String,
        theme: String = "material",
        name: String = "mkdocs.yml",
    ): List<HighlightInfo> {
        myFixture.addFileToProject("docs/stylesheets/extra.css", css)
        val header = """
            site_name: Handbook
            extra_css:
              - stylesheets/extra.css
            theme:
              name: $theme
              palette:
        """.trimIndent()
        myFixture.configureByText(name, header + "\n" + palette.prependIndent("    ") + "\n")
        return reported()
    }

    /**
     * Returns the findings of this annotator among everything the file was highlighted with.
     */
    private fun reported(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.description?.contains("--md-") == true }
}
