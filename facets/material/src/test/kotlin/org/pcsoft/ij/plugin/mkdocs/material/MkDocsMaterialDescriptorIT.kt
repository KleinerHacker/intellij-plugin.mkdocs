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

package org.pcsoft.ij.plugin.mkdocs.material

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Checks the shipped module descriptors rather than a single unit. Everything the Angular Material feature
 * contributes is registered in them, and a registration that is missing or names a renamed class fails at run
 * time only — the facet would simply never appear, without an error anywhere.
 *
 * The descriptors have to sit in the resource root and carry the name of their module: under `META-INF` the
 * platform does not find them at all.
 */
class MkDocsMaterialDescriptorIT {

    /**
     * Use case: the facet type, the listener carrying a hand-added facet back into `mkdocs.yml` and the
     * `siteFeature` registration are what make the feature exist at all. All three live in the base module,
     * which is loaded wherever the plugin is.
     */
    @Test
    fun `the module descriptor registers the facet, the listener and the site feature`() {
        val content = descriptorText(BASE_DESCRIPTOR)

        assertTrue(
            "the module descriptor does not register the Angular Material facet type",
            content.contains("org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacetType"),
        )
        assertTrue(
            "the module descriptor does not subscribe the Angular Material facet listener",
            content.contains("org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacetListener"),
        )
        assertTrue(
            "the module descriptor does not name the facet topic",
            content.contains("""topic="com.intellij.facet.FacetManagerListener""""),
        )
        assertTrue(
            "the module descriptor does not contribute the Angular Material site feature",
            content.contains("org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialSiteFeature"),
        )
        assertTrue(
            "the module descriptor does not declare the extension namespace of the plugin",
            content.contains("""<extensions defaultExtensionNs="org.pcsoft.ij.plugin.mkdocs">"""),
        )
    }

    /**
     * Use case: every text of the feature comes from its own bundle. A module descriptor without the
     * declaration leaves the platform looking in the bundle of the plugin, where none of the keys exist.
     */
    @Test
    fun `every module descriptor names the bundle of the feature`() {
        listOf(BASE_DESCRIPTOR, CSS_DESCRIPTOR, MARKDOWN_DESCRIPTOR).forEach { descriptor ->
            assertTrue(
                "$descriptor does not name the bundle of the feature",
                descriptorText(descriptor).contains("<resource-bundle>messages.MkDocsMaterialBundle</resource-bundle>"),
            )
        }
    }

    /**
     * Use case: the completion of the theme's custom properties registers against the CSS language, which
     * only exists where the CSS plugin is installed. The module therefore has to declare that dependency —
     * without it the whole module fails to load in an IDE shipping no CSS support.
     */
    @Test
    fun `the css module depends on the css plugin and on the base module`() {
        val content = descriptorText(CSS_DESCRIPTOR)

        assertTrue(
            "the CSS module does not depend on the CSS plugin",
            content.contains("""<plugin id="com.intellij.css"/>"""),
        )
        assertTrue(
            "the CSS module does not depend on the base module of the feature",
            content.contains("""<module name="org.pcsoft.ij.plugin.mkdocs.material"/>"""),
        )
        assertTrue(
            "the CSS module does not register the custom property completion",
            content.contains("org.pcsoft.ij.plugin.mkdocs.material.css.MkDocsMaterialCssVariableCompletionContributor"),
        )
    }

    /**
     * Use case: the icon shorthands register against the Markdown language, which not every IDE ships. The
     * same reasoning as for the CSS module, and the same failure if the dependency is missing.
     */
    @Test
    fun `the markdown module depends on the markdown plugin and on the base module`() {
        val content = descriptorText(MARKDOWN_DESCRIPTOR)

        assertTrue(
            "the Markdown module does not depend on the Markdown plugin",
            content.contains("""<plugin id="org.intellij.plugins.markdown"/>"""),
        )
        assertTrue(
            "the Markdown module does not depend on the base module of the feature",
            content.contains("""<module name="org.pcsoft.ij.plugin.mkdocs.material"/>"""),
        )
        assertTrue(
            "the Markdown module does not register the shorthand completion",
            content.contains("org.pcsoft.ij.plugin.mkdocs.material.markdown.MkDocsMaterialShorthandCompletionContributor"),
        )
    }

    /**
     * Reads a module descriptor off the runtime classpath.
     *
     * @param name the file name of the descriptor, which is the name of the module
     */
    private fun descriptorText(name: String): String {
        val descriptor = javaClass.getResource("/$name")
        assertNotNull("$name is missing from the runtime classpath", descriptor)
        return descriptor!!.readText()
    }

    private companion object {

        /** The descriptor of the module that is loaded wherever the plugin is. */
        const val BASE_DESCRIPTOR = "org.pcsoft.ij.plugin.mkdocs.material.xml"

        /** The descriptor of the module needing the CSS plugin. */
        const val CSS_DESCRIPTOR = "org.pcsoft.ij.plugin.mkdocs.material.css.xml"

        /** The descriptor of the module needing the Markdown plugin. */
        const val MARKDOWN_DESCRIPTOR = "org.pcsoft.ij.plugin.mkdocs.material.markdown.xml"
    }
}
