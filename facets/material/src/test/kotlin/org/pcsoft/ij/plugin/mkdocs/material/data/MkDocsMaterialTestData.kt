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

package org.pcsoft.ij.plugin.mkdocs.material.data

/**
 * The theme description every test shares.
 *
 * Constructed directly rather than looked up with `service<…>()`: the service only reads bundled classpath
 * resources, so the plain unit tests can use it as well as the ones running on the platform. Reading the
 * resources once for the whole test run also keeps the parsing out of every single test.
 */
private val testData = MkDocsMaterialDataService()

/**
 * The palette colour written as [id], for a test naming a colour it expects.
 *
 * Fails loudly instead of returning `null`: a test asserting against a colour the bundled resource does not
 * describe is a broken test, not a failed assertion.
 *
 * @param id the identifier as it appears in the configuration file, for example `deep-purple`
 */
fun color(id: String): MkDocsMaterialColor =
    requireNotNull(testData.colors.byId(id)) { "the bundled resource describes no colour '$id'" }

/**
 * The feature flag written as [id], for a test naming a flag it expects.
 *
 * @param id the identifier as it appears in `theme.features`, for example `navigation.tabs`
 */
fun flag(id: String): MkDocsMaterialFeatureFlag =
    requireNotNull(testData.featureFlags.byId(id)) { "the bundled resource describes no feature flag '$id'" }

/**
 * The Markdown extension written as [id], for a test naming an extension it expects.
 *
 * @param id the identifier as it appears under `markdown_extensions`, for example `pymdownx.superfences`
 */
fun extension(id: String): MkDocsMarkdownExtension =
    requireNotNull(testData.extensions.byId(id)) { "the bundled resource describes no extension '$id'" }
