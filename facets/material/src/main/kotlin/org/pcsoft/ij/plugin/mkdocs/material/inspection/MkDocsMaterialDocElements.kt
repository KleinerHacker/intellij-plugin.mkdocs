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

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import org.jetbrains.yaml.YAMLLanguage
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtension
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMarkdownExtensionOption

/**
 * The element the quick documentation of a completion entry offering an extension is generated for.
 *
 * An entry of that popup is a plain string, and the platform asks for the PSI element *behind* it before it
 * asks anyone for documentation — an entry without one leaves *Ctrl+Q* empty inside the popup, while the very
 * same name answers once it is written into the file. The entry therefore carries this element, and
 * [MkDocsMaterialExtensionDocumentationProvider] answers on it.
 *
 * @property context an element of the file the popup was opened in, which the platform reads the project from
 * @property extension the extension the entry offers
 */
internal class ExtensionDocElement(
    private val context: PsiElement,
    val extension: MkDocsMarkdownExtension,
) : FakePsiElement() {

    override fun getParent(): PsiElement = context

    override fun getName(): String = extension.id

    override fun getLanguage(): Language = YAMLLanguage.INSTANCE
}

/**
 * The element the quick documentation of a completion entry offering an option of an extension is generated
 * for.
 *
 * The counterpart of [ExtensionDocElement] one level deeper, and there for the same reason: an entry of the
 * popup is a plain string, and without an element behind it *Ctrl+Q* stays empty on exactly the level no
 * schema describes.
 *
 * The extension is carried along with the option: the same option name belongs to more than one extension,
 * and the documentation says which one it is being read for.
 *
 * @property context an element of the file the popup was opened in, which the platform reads the project from
 * @property extension the extension the offered option belongs to
 * @property option the option the entry offers
 */
internal class OptionDocElement(
    private val context: PsiElement,
    val extension: MkDocsMarkdownExtension,
    val option: MkDocsMarkdownExtensionOption,
) : FakePsiElement() {

    override fun getParent(): PsiElement = context

    override fun getName(): String = "${extension.id}.${option.key}"

    override fun getLanguage(): Language = YAMLLanguage.INSTANCE
}
