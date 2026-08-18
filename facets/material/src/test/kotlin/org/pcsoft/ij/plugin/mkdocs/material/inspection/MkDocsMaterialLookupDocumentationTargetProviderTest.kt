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

package org.pcsoft.ij.plugin.mkdocs.material.inspection

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialDataService

/**
 * Developer test (class name does NOT end in `IT`) — runs under `test -PtestSuite=developer`.
 *
 * Covers the target *Ctrl+Q* is answered with while the completion popup is open. That path is separate from
 * the one taken on a name already written into the file: the popup asks the providers of
 * `platform.backend.documentation.lookupElementTargetProvider`, and an entry nobody answers for shows the bare
 * name of the element behind it.
 */
class MkDocsMaterialLookupDocumentationTargetProviderTest : BasePlatformTestCase() {

    private val provider = MkDocsMaterialLookupDocumentationTargetProvider()

    /**
     * Use case: the popup entry of an extension. The title of the documentation is the identifier, and the
     * text is the one the very same extension is explained with inside the file.
     */
    fun `test answers on an entry offering an extension`() {
        val extension = service<MkDocsMaterialDataService>().extensions.byId("admonition")!!
        val element = LookupElementBuilder.create(ExtensionDocElement(context(), extension), extension.id)

        val target = provider.documentationTarget(myFixture.file, element, 0)

        assertNotNull(target)
        assertEquals(extension.id, target!!.computePresentation().presentableText)
        assertNotNull(target.computeDocumentation())
    }

    /**
     * Use case: an entry of the same popup that comes from somewhere else — a value of the JSON schema, of
     * another contributor, of another plugin. Answering it would put a text of this plugin on a stranger.
     */
    fun `test says nothing about an entry that is not its own`() {
        context()
        val element = LookupElementBuilder.create("something.else")

        assertNull(provider.documentationTarget(myFixture.file, element, 0))
    }

    /**
     * Returns an element of a configured file, which the documentation elements hang on for their project.
     */
    private fun context() = myFixture.configureByText("mkdocs.yml", "site_name: Handbook\n").firstChild!!
}
