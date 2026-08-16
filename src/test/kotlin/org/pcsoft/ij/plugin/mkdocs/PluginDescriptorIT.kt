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

package org.pcsoft.ij.plugin.mkdocs

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration test (class name ends in `IT`) — runs under `test -PtestSuite=integration`.
 *
 * Checks the shipped artifact rather than a single unit: the plugin descriptor must be on the runtime
 * classpath and declare the plugin ID the Marketplace release is published under.
 */
class PluginDescriptorIT {

    @Test
    fun `plugin descriptor is on the classpath and declares the expected id`() {
        val descriptor = javaClass.getResource("/META-INF/plugin.xml")
        assertNotNull("META-INF/plugin.xml is missing from the runtime classpath", descriptor)

        val content = descriptor!!.readText()
        assertTrue(
            "plugin.xml does not declare the expected plugin id",
            content.contains("<id>org.pcsoft.ij.plugin.mkdocs</id>")
        )
    }

    /**
     * Use case: the *Site Page* tool window only exists if the descriptor registers it. A tool window whose
     * factory class was renamed without the descriptor following fails at run time, not at compile time, so
     * the registration is checked against the shipped artifact.
     */
    @Test
    fun `plugin descriptor registers the site page tool window`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml does not register the Site Page tool window",
            content.contains("""<toolWindow id="MkDocs Site Page""""),
        )
        assertTrue(
            "plugin.xml does not name the tool window factory",
            content.contains("org.pcsoft.ij.plugin.mkdocs.ui.toolwindow.MkDocsSitePageToolWindowFactory"),
        )
        assertTrue(
            "plugin.xml does not name the tool window icon",
            content.contains("org.pcsoft.ij.plugin.mkdocs.MkDocsIcons.SitePageToolWindow"),
        )
    }

    /**
     * Use case: the tool window appears and disappears with the sites of the project, which only works while
     * the availability listener is subscribed to the site topic through the descriptor.
     */
    @Test
    fun `plugin descriptor subscribes the availability listener`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml declares no project listeners",
            content.contains("<projectListeners>"),
        )
        assertTrue(
            "plugin.xml does not subscribe the availability listener",
            content.contains("org.pcsoft.ij.plugin.mkdocs.ui.toolwindow.MkDocsSitePageAvailabilityListener"),
        )
        assertTrue(
            "plugin.xml does not name the site topic",
            content.contains("""topic="org.pcsoft.ij.plugin.mkdocs.services.MkDocsSitesListener""""),
        )
    }

    /**
     * Use case: Ctrl+Click, completion and renaming on the path values of `mkdocs.yml` only exist while the
     * reference contributor is registered for YAML. A contributor that is not declared fails silently — the
     * paths simply stay plain text — so the registration is checked against the shipped artifact.
     */
    @Test
    fun `plugin descriptor registers the path reference contributor`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml does not register a reference contributor for yaml",
            content.contains("""<psi.referenceContributor language="yaml""""),
        )
        assertTrue(
            "plugin.xml does not name the path reference contributor",
            content.contains("org.pcsoft.ij.plugin.mkdocs.reference.MkDocsPathReferenceContributor"),
        )
    }

    /**
     * Use case: the gutter icons next to the path values are drawn by a line marker provider, which the
     * platform only asks once the descriptor declares it for YAML.
     */
    @Test
    fun `plugin descriptor registers the path line marker provider`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml does not register a line marker provider for yaml",
            content.contains("""<codeInsight.lineMarkerProvider language="yaml""""),
        )
        assertTrue(
            "plugin.xml does not name the path line marker provider",
            content.contains("org.pcsoft.ij.plugin.mkdocs.reference.MkDocsPathLineMarkerProvider"),
        )
    }

    /**
     * Use case: a path no file system can read is reported by an annotator of its own, separate from the one
     * warning about missing metadata. Both are declared for YAML, so the check is on the class name.
     */
    @Test
    fun `plugin descriptor registers the path annotator`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml does not name the path annotator",
            content.contains("org.pcsoft.ij.plugin.mkdocs.reference.MkDocsPathAnnotator"),
        )
        assertTrue(
            "plugin.xml declares no annotator for yaml",
            content.contains("""<annotator language="yaml""""),
        )
    }

    /**
     * Use case: every optional feature is shipped as a content module of the V2 model. A module the plugin
     * descriptor does not list is not loaded at all — the facet, its schema and its wizard step would be
     * absent without anything failing — so the declaration is checked against the shipped artifact.
     *
     * What each module registers is checked by the feature itself; the plugin only has to name them, and the
     * two needing another IDE plugin have to stay optional so the plugin keeps loading without it.
     */
    @Test
    fun `plugin descriptor declares the feature content modules`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml declares no content modules",
            content.contains("<content>"),
        )
        assertTrue(
            "plugin.xml does not declare the Angular Material module",
            declaresModule(content, "org.pcsoft.ij.plugin.mkdocs.material"),
        )
        assertTrue(
            "the CSS part of the Angular Material feature must be an optional module",
            declaresModule(content, "org.pcsoft.ij.plugin.mkdocs.material.css", optional = true),
        )
        assertTrue(
            "the Markdown part of the Angular Material feature must be an optional module",
            declaresModule(content, "org.pcsoft.ij.plugin.mkdocs.material.markdown", optional = true),
        )
    }

    /**
     * Returns `true` if [content] declares [name] as a content module.
     *
     * Matched with a pattern rather than compared as text: what is checked is the descriptor of the *built*
     * plugin, which the build re-serialises on its way into the artifact — the attributes keep their values,
     * the white space around them does not.
     *
     * @param content the descriptor to search
     * @param name the module name as declared in the `<content>` block
     * @param optional whether the module has to be marked `loading="optional"`
     */
    private fun declaresModule(content: String, name: String, optional: Boolean = false): Boolean {
        val loading = if (optional) """\s+loading="optional"""" else """\s*"""
        return Regex("""<module\s+name="${Regex.escape(name)}"$loading\s*/>""").containsMatchIn(content)
    }

    /**
     * Use case: a feature reaches the plugin through the `siteFeature` extension point and through nothing
     * else. A point that is not declared makes every registration of a feature module fail on load.
     */
    @Test
    fun `plugin descriptor declares the site feature extension point`() {
        val content = descriptorText()

        assertTrue(
            "plugin.xml does not declare the siteFeature extension point",
            content.contains("""<extensionPoint name="siteFeature"""),
        )
        assertTrue(
            "the siteFeature extension point must name the interface of the facet API",
            content.contains("org.pcsoft.ij.plugin.mkdocs.api.MkDocsSiteFeature"),
        )
    }

    /**
     * Reads the plugin descriptor off the runtime classpath.
     */
    private fun descriptorText(): String {
        val descriptor = javaClass.getResource("/META-INF/plugin.xml")
        assertNotNull("META-INF/plugin.xml is missing from the runtime classpath", descriptor)
        return descriptor!!.readText()
    }
}
