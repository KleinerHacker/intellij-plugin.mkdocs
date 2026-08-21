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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssFile
import com.intellij.psi.css.CssRuleset
import com.intellij.psi.css.CssSelector
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.pcsoft.ij.plugin.mkdocs.material.data.MkDocsMaterialScheme
import org.pcsoft.ij.plugin.mkdocs.material.icon.MkDocsMaterialIconLocator
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsConfig

/**
 * The custom property that carries one colour of a palette.
 *
 * @property variable the name of the custom property, `--md-primary-fg-color` and its like
 * @property scope where the definition applies
 * @property file the style sheet the definition was read from
 * @property selector the selector the definition sits below, the element a reference resolves to
 */
data class MkDocsMaterialCssDefinition(
    val variable: String,
    val scope: MkDocsMaterialCssScope,
    val file: VirtualFile,
    val selector: PsiElement,
)

/**
 * One colour scheme a style sheet the site loads paints.
 *
 * A site loads two kinds of them, and both answer the same question: the style sheet the installed theme
 * brings along — which is where `default` and `slate` come from — and the ones behind `extra_css`. Neither is
 * more built in than the other; `[data-md-color-scheme="slate"]` is a rule of a CSS file exactly as a ground
 * of the author's own is.
 *
 * @property name the identifier behind `data-md-color-scheme`, the value `theme.palette.scheme` is written with
 * @property file the style sheet the scheme was read from, `null` if no installation could be read
 * @property target what a reference resolves to — the selector for a style sheet of the site, the file itself
 *           for the one of the theme, `null` if no installation could be read
 * @property builtIn whether the installed theme paints it rather than the site
 */
data class MkDocsMaterialCssScheme(
    val name: String,
    val file: VirtualFile?,
    val target: PsiElement?,
    val builtIn: Boolean,
)

/**
 * Where a definition of a style sheet applies.
 *
 * The two are not the same thing at all, and telling them apart is the whole point of reading the style sheets:
 * `:root` paints every palette of the site, while a rule below `[data-md-color-scheme="slate"]` paints exactly
 * the palette whose `scheme` names that identifier — a colour defined there says nothing about a palette
 * standing on another ground.
 */
sealed interface MkDocsMaterialCssScope {

    /** Applies to every palette of the site, whatever ground it is painted on. */
    data object Global : MkDocsMaterialCssScope

    /**
     * Applies to the palettes whose `scheme` is [name].
     *
     * @property name the identifier behind `data-md-color-scheme`
     */
    data class Scheme(val name: String) : MkDocsMaterialCssScope
}

/**
 * What the style sheets a site loads say about its palette.
 *
 * *Material for MkDocs* is restyled by redefining its custom properties, and `theme.palette` and those style
 * sheets therefore describe the same colours twice. Whether the two agree cannot be seen in either file alone,
 * so the definitions are collected here, once per site, and every feature judging a palette value asks this
 * service rather than reading a style sheet of its own.
 *
 * Read through the CSS PSI of the platform: a custom property can sit below any selector, behind an `@media`
 * and inside a comment that looks like one, and a regular expression over the text answers all three the same
 * way. Which is why this lives in the optional content module of the facet — without the CSS plugin there is
 * no such PSI, and the rest of the plugin has to keep working.
 *
 * The answer is cached against the modification count of the PSI, because an annotator asks it for every value
 * of every palette on every keystroke.
 *
 * @property project the project the sites belong to
 */
@Service(Service.Level.PROJECT)
class MkDocsMaterialCssPaletteService(private val project: Project) {

    /** What was last read out of an installation, together with the directory it was read from. */
    @Volatile
    private var builtIns: BuiltIns? = null

    /**
     * Returns the custom properties the style sheets of the site behind [configFile] define.
     *
     * The caller must hold a read action.
     *
     * @param configFile the configuration file of the site
     */
    fun definitions(configFile: VirtualFile): List<MkDocsMaterialCssDefinition> = read(configFile).definitions

    /**
     * Returns the colour schemes the site behind [configFile] can stand on.
     *
     * Both style sheets a site loads are asked, because both answer the same question: the one the installed
     * theme brings along, which is where `default` and `slate` come from, and the ones behind `extra_css`.
     * A ground painted by both is reported once, as the site's own — that is the file worth navigating to.
     *
     * The caller must hold a read action.
     *
     * @param configFile the configuration file of the site
     */
    fun schemes(configFile: VirtualFile): List<MkDocsMaterialCssScheme> =
        (read(configFile).schemes + builtInSchemes()).distinctBy { it.name }

    /**
     * Returns the definitions of [variable] a palette standing on [scheme] is painted by.
     *
     * A definition below `:root` counts for every palette; one below `[data-md-color-scheme="…"]` only for the
     * palette naming that ground. Which ground a palette stands on is answered by
     * `MkDocsMaterialPaletteKeys.schemeNameOf`, the fallback of the theme included.
     *
     * The caller must hold a read action.
     *
     * @param configFile the configuration file of the site
     * @param variable the name of the custom property
     * @param scheme the ground the palette is painted on
     */
    fun definitionsFor(
        configFile: VirtualFile,
        variable: String,
        scheme: String,
    ): List<MkDocsMaterialCssDefinition> =
        definitions(configFile).filter { it.variable == variable && applies(it.scope, scheme) }

    /**
     * Returns the colour schemes the style sheet of the installed theme paints.
     *
     * That file is what makes `default` and `slate` grounds a site can stand on, and reading it is what keeps
     * the two out of a written list here: a version of the theme adding a third one adds it to the popup by
     * itself. It is read as text rather than through the CSS PSI — the shipped style sheet is minified, and
     * all that is wanted of it are the identifiers of one attribute selector.
     *
     * While no installation can be read the theme's own two are named after all, out of
     * [MkDocsMaterialScheme]. They stay grounds of the theme whether the IDE has found the package or not,
     * and an empty popup would be a worse answer than a slightly stale one. Such an entry resolves to
     * nothing, which is what makes the reference on it soft.
     */
    private fun builtInSchemes(): List<MkDocsMaterialCssScheme> {
        val psiManager = PsiManager.getInstance(project)
        val read = readBuiltIns()
        if (read.isEmpty()) {
            return MkDocsMaterialScheme.entries.map {
                MkDocsMaterialCssScheme(it.id, file = null, target = null, builtIn = true)
            }
        }
        return read.map { (name, file) ->
            MkDocsMaterialCssScheme(name, file, psiManager.findFile(file), builtIn = true)
        }
    }

    /**
     * Returns the grounds of the installed theme, from the memo or by reading the installation.
     *
     * Memoised against the directory pip reported, because that is the only thing that can change the answer
     * — a style sheet inside an installed package is not edited. Nothing else would drop the answer: the
     * cache of the site's own style sheets hangs on the PSI, which an installation appearing next to a
     * running IDE does not touch.
     */
    private fun readBuiltIns(): List<Pair<String, VirtualFile>> {
        val location = MkDocsMaterialIconLocator.locateInstallation(project)
        builtIns?.let { if (it.location == location) return it.schemes }
        val schemes = location?.let { scanInstallation(it) }.orEmpty()
        builtIns = BuiltIns(location, schemes)
        return schemes
    }

    /**
     * Reads the grounds out of the style sheets the package under [location] ships.
     *
     * @param location the directory pip reports as the `Location` of the theme
     */
    private fun scanInstallation(location: String): List<Pair<String, VirtualFile>> {
        val fileSystem = LocalFileSystem.getInstance()
        val path = "$location/$STYLE_SHEETS_INSIDE_PACKAGE"
        // An installation lies outside the project, so the VFS may not know it yet. A synchronous refresh is
        // what the platform forbids under the read lock, so inside one the cached state has to do.
        val directory = if (ApplicationManager.getApplication().isReadAccessAllowed) {
            fileSystem.findFileByPath(path)
        } else {
            fileSystem.refreshAndFindFileByPath(path)
        }
        if (directory == null || !directory.isValid || !directory.isDirectory) return emptyList()
        return directory.children
            .filter { !it.isDirectory && it.extension.equals("css", ignoreCase = true) }
            .flatMap { file ->
                val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return@flatMap emptyList()
                SCHEME_ATTRIBUTE.findAll(text).map { it.groupValues[1] to file }.toList()
            }
            .distinctBy { it.first }
    }

    /**
     * Returns `true` if a definition of [scope] paints a palette standing on [scheme].
     *
     * @param scope where the definition applies
     * @param scheme the ground the palette is painted on
     */
    private fun applies(scope: MkDocsMaterialCssScope, scheme: String): Boolean = when (scope) {
        is MkDocsMaterialCssScope.Global -> true
        is MkDocsMaterialCssScope.Scheme -> scope.name == scheme
    }

    /**
     * Returns what the style sheets of the site behind [configFile] hold, from the cache or by reading them.
     *
     * The cache sits on the configuration file, which is where the list of style sheets comes from: a file
     * added to or removed from `extra_css` is a change of that file as much as an edit of a style sheet is a
     * change of the PSI.
     *
     * @param configFile the configuration file of the site
     */
    private fun read(configFile: VirtualFile): Palette {
        val psiConfig = PsiManager.getInstance(project).findFile(configFile) ?: return Palette.EMPTY
        return CachedValuesManager.getCachedValue(psiConfig) {
            CachedValueProvider.Result.create(scan(configFile), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    /**
     * Reads every style sheet `extra_css` of [configFile] names.
     *
     * @param configFile the configuration file of the site
     */
    private fun scan(configFile: VirtualFile): Palette {
        val definitions = mutableListOf<MkDocsMaterialCssDefinition>()
        val schemes = mutableListOf<MkDocsMaterialCssScheme>()
        val psiManager = PsiManager.getInstance(project)
        MkDocsConfig.resolveExtraCss(project, configFile).forEach { file ->
            val cssFile = psiManager.findFile(file) as? CssFile ?: return@forEach
            scan(cssFile, file, definitions, schemes)
        }
        return Palette(definitions, schemes)
    }

    /**
     * Reads one style sheet into [definitions] and [schemes].
     *
     * A rule whose selector says nothing about the theme is skipped whole: an author styles the pages of the
     * site in the same file, and a custom property of their own is none of this service's business.
     *
     * @param cssFile the parsed style sheet
     * @param file the file it was parsed from
     * @param definitions the custom properties collected so far
     * @param schemes the colour schemes collected so far
     */
    private fun scan(
        cssFile: CssFile,
        file: VirtualFile,
        definitions: MutableList<MkDocsMaterialCssDefinition>,
        schemes: MutableList<MkDocsMaterialCssScheme>,
    ) {
        PsiTreeUtil.findChildrenOfType(cssFile, CssRuleset::class.java).forEach { ruleset ->
            val scoped = PsiTreeUtil.findChildrenOfType(ruleset, CssSelector::class.java)
                .mapNotNull { selector -> scopeOf(selector)?.let { selector to it } }
            if (scoped.isEmpty()) return@forEach
            scoped.forEach { (selector, scope) ->
                if (scope is MkDocsMaterialCssScope.Scheme) {
                    schemes += MkDocsMaterialCssScheme(scope.name, file, selector, builtIn = false)
                }
            }
            PsiTreeUtil.findChildrenOfType(ruleset, CssDeclaration::class.java).forEach { declaration ->
                val name = declaration.propertyName.trim()
                if (!name.startsWith(VARIABLE_PREFIX)) return@forEach
                scoped.forEach { (selector, scope) ->
                    definitions += MkDocsMaterialCssDefinition(name, scope, file, selector)
                }
            }
        }
    }

    /**
     * Returns where a rule below [selector] applies, or `null` if it says nothing about the palette.
     *
     * The scheme is asked first: `:root[data-md-color-scheme="slate"]` names both, and it paints that one
     * scheme rather than the whole site.
     *
     * @param selector one selector of a rule
     */
    private fun scopeOf(selector: CssSelector): MkDocsMaterialCssScope? {
        val text = selector.text ?: return null
        SCHEME_ATTRIBUTE.find(text)?.let { return MkDocsMaterialCssScope.Scheme(it.groupValues[1]) }
        if (GLOBAL_SELECTOR.containsMatchIn(text)) return MkDocsMaterialCssScope.Global
        return null
    }

    /**
     * The grounds of an installation, remembered against the directory they were read from.
     *
     * @property location the directory pip reported, or `null` while there is none
     * @property schemes the identifier of every ground, with the style sheet naming it
     */
    private data class BuiltIns(val location: String?, val schemes: List<Pair<String, VirtualFile>>)

    /**
     * What one site's style sheets hold.
     *
     * @property definitions the custom properties they define
     * @property schemes the colour schemes they paint
     */
    private data class Palette(
        val definitions: List<MkDocsMaterialCssDefinition>,
        val schemes: List<MkDocsMaterialCssScheme>,
    ) {
        companion object {

            /** What a site without a readable style sheet holds. */
            val EMPTY: Palette = Palette(emptyList(), emptyList())
        }
    }

}

/** The attribute a colour scheme of the theme is selected by. */
internal const val MATERIAL_SCHEME_ATTRIBUTE: String = "data-md-color-scheme"

/** The directory inside the installed package holding the style sheets the theme ships. */
private const val STYLE_SHEETS_INSIDE_PACKAGE: String = "material/templates/assets/stylesheets"

/** The prefix every custom property of the theme carries. */
private const val VARIABLE_PREFIX: String = "--md-"

/** The attribute selector naming a colour scheme, with the identifier as its only group. */
private val SCHEME_ATTRIBUTE: Regex =
    Regex("""\[\s*$MATERIAL_SCHEME_ATTRIBUTE\s*=\s*["']?([A-Za-z0-9_-]+)["']?\s*]""")

/** The selectors standing for the whole document, whatever ground a palette is painted on. */
private val GLOBAL_SELECTOR: Regex = Regex("""(^|[\s,>+~])(:root|html|body)([^A-Za-z0-9_-]|$)""")
