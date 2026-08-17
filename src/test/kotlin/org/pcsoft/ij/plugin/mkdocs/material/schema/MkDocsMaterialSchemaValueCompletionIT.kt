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

package org.pcsoft.ij.plugin.mkdocs.material.schema

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.services.MkDocsModuleService

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Covers every suggestion list of an `mkdocs.yml` that comes out of the refined JSON schema: the keys the
 * Material theme adds below `theme` and `extra`, and the values it accepts for them. `MkDocsMaterialSchemaGenerator`
 * is tested on the generated document itself, and `MkDocsMaterialSchemaFileProviderIT` on the file being mapped
 * to it — neither of them says that the platform walks a value out of that document and into the popup, which is
 * the only thing an author ever sees. Only a run of the real completion answers that.
 *
 * The lists of values are compared against `MkDocsMaterialDataService` rather than against a literal, because the
 * schema is built from that very service: a colour added to `material/spec/colors.yaml` then has to arrive in the
 * popup without this test being touched, and if it does not arrive the test fails.
 */
class MkDocsMaterialSchemaValueCompletionIT : BasePlatformTestCase() {

    /** The theme description the schema is built from. */
    private val data get() = service<MkDocsMaterialDataService>()

    /**
     * Use case: the author opens the `theme` block of a Material site. The base MkDocs schema knows four keys
     * there, and the ones carrying the whole appearance of the theme are not among them — they come out of the
     * refinement, so their absence would leave the block uncompletable.
     */
    fun `test offers the keys the theme adds below theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              <caret>
            """
        )

        assertContainsElements(offered, listOf("features", "palette", "font", "icon", "language", "direction"))
    }

    /**
     * Use case: `theme.direction`, the writing direction of the site. A closed set of two values written by hand
     * into the refinement, so this is the shortest path from the resource to the popup and the one that says the
     * enums of the schema arrive at all.
     */
    fun `test offers the writing directions`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              direction: <caret>
            """
        )

        assertContainsElements(offered, listOf("ltr", "rtl"))
    }

    /**
     * Use case: `theme.features`, the behaviours of the theme to switch on. The values are spliced into the
     * schema out of `material/spec/feature-flags.yaml`, so every flag the theme offers has to be offered here.
     */
    fun `test offers every feature flag of the theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - <caret>
            """
        )

        assertContainsElements(offered, data.featureFlags.all.map { it.id })
    }

    /**
     * Use case: the second entry of `theme.features`. A completion reached only for the first flag of the list
     * would be worse than none, and the sequence is the only shape the key has.
     */
    fun `test offers the feature flags below a flag that is already written`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              features:
                - navigation.tabs
                - <caret>
            """
        )

        assertContainsElements(offered, data.featureFlags.all.map { it.id })
    }

    /**
     * Use case: the palette written as a single mapping, which is what a site without a colour scheme toggle
     * does. `scheme` decides the ground the palette is painted on, and the theme knows exactly two.
     */
    fun `test offers the schemes of the single palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                scheme: <caret>
            """
        )

        assertContainsElements(offered, MkDocsMaterialScheme.entries.map { it.id })
    }

    /**
     * Use case: `theme.palette.primary` of that same mapping. The colours are spliced in out of
     * `material/spec/colors.yaml`, and only the ones the theme accepts as a primary colour belong there.
     */
    fun `test offers the primary colours of the single palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                primary: <caret>
            """
        )

        assertContainsElements(offered, data.colors.primaries().map { it.id })
    }

    /**
     * Use case: `theme.palette.accent` of that same mapping. The accepted colours are a different set than the
     * primary ones — the theme renders no accent for every colour it paints a header with.
     */
    fun `test offers the accent colours of the single palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                accent: <caret>
            """
        )

        assertContainsElements(offered, data.colors.accents().map { it.id })
    }

    /**
     * Use case: the palette written as a sequence, which is what a site with a light and a dark palette does.
     * The schema describes that form through a definition of its own, so a value offered in one form and not in
     * the other would be an arbitrary difference — and both definitions are filled from the same lists.
     */
    fun `test offers the same values in the sequence form of the palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - scheme: default
                  primary: <caret>
            """
        )

        assertContainsElements(offered, data.colors.primaries().map { it.id })
    }

    /**
     * Use case: the scheme of an entry of that sequence, the value a colour scheme toggle switches between.
     */
    fun `test offers the schemes in the sequence form of the palette`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - scheme: <caret>
            """
        )

        assertContainsElements(offered, MkDocsMaterialScheme.entries.map { it.id })
    }

    /**
     * Use case: the toggle of a palette entry — the button switching between the palettes. Its two keys are the
     * icon on the button and the tooltip of it, and a palette missing either renders a button an author cannot
     * read.
     */
    fun `test offers the keys of a palette toggle`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
              palette:
                - scheme: default
                  toggle:
                    <caret>
            """
        )

        assertContainsElements(offered, listOf("icon", "name"))
    }

    /**
     * Use case: the `extra` block, which the base MkDocs schema describes as a mapping with no keys at all.
     * Everything below it is read by the theme, so without the refinement the block stays empty in the popup.
     */
    fun `test offers the keys the theme adds below extra`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              generator: false
              <caret>
            """
        )

        assertContainsElements(offered, listOf("social", "analytics", "consent", "status"))
    }

    /**
     * Use case: an entry of `extra.social`, the row of icons in the footer. The keys sit two levels below a
     * sequence, which is the deepest place of the refinement — a chain of definitions the platform has to walk
     * before it offers anything.
     */
    fun `test offers the keys of a social link`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              social:
                - link: https://example.com
                  <caret>
            """
        )

        assertContainsElements(offered, listOf("icon", "name"))
    }

    /**
     * Use case: `extra.consent.actions`, the buttons of the cookie consent dialogue. A closed set written by
     * hand into the refinement, below a sequence rather than a mapping.
     */
    fun `test offers the actions of the consent dialogue`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: material
            extra:
              consent:
                actions:
                  - <caret>
            """
        )

        assertContainsElements(offered, listOf("accept", "reject", "manage"))
    }

    /**
     * Use case: a site rendered with another theme. The refined schema is not handed to it, so none of the keys
     * above may be offered — a site on `readthedocs` told about `theme.palette` would be told to write a key the
     * theme rendering it never reads.
     */
    fun `test offers no material key on a site that is not on the material theme`() {
        val offered = complete(
            """
            site_name: Handbook
            theme:
              name: readthedocs
              <caret>
            """
        )

        assertDoesntContain(offered, "palette")
        assertDoesntContain(offered, "features")
    }

    /**
     * Runs completion in a configuration file holding [text] and returns what it offers.
     *
     * The site is detected before the caret is asked, because the refined schema is bound to the facet the
     * detection attaches. The cached answer of the schema provider is dropped afterwards — the light fixture
     * hands every test of a class the same project, and with it the answer given for the file of the test
     * before.
     *
     * @param text the content of the configuration file, indented as source and with the caret marked
     * @return the entries the completion popup offers, empty when it offers nothing
     */
    private fun complete(text: String): List<String> {
        myFixture.configureByText("mkdocs.yml", text.trimIndent() + "\n")
        MkDocsModuleService.getInstance(project).sync()
        MkDocsMaterialSchemaCache.invalidate(project)

        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }
}
