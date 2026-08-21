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

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLScalar
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialPaletteKeys
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject

/**
 * Links `theme.palette.scheme` to the selector of the style sheet that paints that ground.
 *
 * The value is a name of the CSS and nothing else: what a scheme looks like is written below
 * `[data-md-color-scheme="…"]`, either in a style sheet behind `extra_css` or in the one the installed theme
 * ships. Reading the configuration file and the style sheets as unconnected files is what made a mistyped
 * ground invisible — and it is why finding out what a scheme actually paints meant searching by hand.
 *
 * A ground of the site leads to the selector painting it; one of the theme leads to the style sheet shipping
 * it, because that file is minified and there is nothing inside it worth landing on.
 *
 * Navigation only. A name none of the style sheets carries is marked by [MkDocsMaterialPaletteCssAnnotator]
 * instead of by this reference: whether an unresolved reference is drawn at all is decided by the language
 * owning the file, and YAML draws nothing for one.
 */
class MkDocsMaterialSchemeReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLScalar::class.java), PROVIDER)
    }

    private companion object {

        /** Hands out the reference wherever a scalar stands for the ground of a palette. */
        val PROVIDER: PsiReferenceProvider = object : PsiReferenceProvider() {

            override fun getReferencesByElement(
                element: PsiElement,
                context: ProcessingContext,
            ): Array<PsiReference> {
                val scalar = element as? YAMLScalar ?: return PsiReference.EMPTY_ARRAY
                val file = scalar.containingFile?.originalFile ?: return PsiReference.EMPTY_ARRAY
                if (!MkDocsProject.isConfigFile(file.name)) return PsiReference.EMPTY_ARRAY
                if (MkDocsMaterialPaletteKeys.roleOf(scalar) != MkDocsMaterialPaletteKeys.Role.SCHEME) {
                    return PsiReference.EMPTY_ARRAY
                }
                val configFile = file.virtualFile ?: return PsiReference.EMPTY_ARRAY
                if (!MkDocsMaterialConfig.isMaterialTheme(scalar.project, configFile)) {
                    return PsiReference.EMPTY_ARRAY
                }
                return arrayOf(MkDocsMaterialSchemeReference(scalar))
            }
        }
    }
}

/**
 * The reference from the ground of a palette to the selector painting it.
 *
 * @param scalar the value of `theme.palette.scheme`
 */
class MkDocsMaterialSchemeReference(scalar: YAMLScalar) :
    PsiReferenceBase<YAMLScalar>(scalar, ElementManipulators.getValueTextRange(scalar), true) {

    override fun resolve(): PsiElement? = schemes().firstOrNull { it.name == value.trim() }?.target

    /**
     * Returns that an unresolved ground is not marked through this reference.
     *
     * Always soft, and deliberately so. Whether a hard reference is drawn as unresolved is decided by the
     * language owning the file, and YAML draws nothing at all for one — measured in a running IDE, a ground
     * no style sheet paints stayed unmarked whatever this answered. So the navigation lives here and the mark
     * is put by [MkDocsMaterialPaletteCssAnnotator], which also decides when the set of grounds is complete
     * enough to judge a name by.
     */
    override fun isSoft(): Boolean = true

    // Without an icon, for the same reason the completion of the same values carries none: the origin
    // contributor of the facet puts the mark of the theme on every entry of a Material key.
    override fun getVariants(): Array<Any> = schemes()
        .map { LookupElementBuilder.create(it.name).withTypeText(it.file?.name.orEmpty(), true) }
        .toTypedArray()

    /**
     * Returns the colour schemes the style sheets of this site paint.
     */
    private fun schemes(): List<MkDocsMaterialCssScheme> {
        val configFile = element.containingFile?.originalFile?.virtualFile ?: return emptyList()
        return service().schemes(configFile)
    }

    /**
     * Returns the service answering what the style sheets of this site paint.
     */
    private fun service(): MkDocsMaterialCssPaletteService =
        element.project.service<MkDocsMaterialCssPaletteService>()
}
