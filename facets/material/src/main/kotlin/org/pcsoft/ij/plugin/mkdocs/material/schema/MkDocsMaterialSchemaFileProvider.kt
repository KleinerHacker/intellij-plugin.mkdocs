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

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsProject
import org.pcsoft.ij.plugin.mkdocs.material.MkDocsMaterialBundle
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialConfig
import org.pcsoft.ij.plugin.mkdocs.material.facet.MkDocsMaterialFacet
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which configuration files belong to a *Material for MkDocs* site.
 *
 * [MkDocsMaterialSchemaFileProvider.isAvailable] is asked on the EDT for every schema lookup, and answering it
 * means resolving a module and, before the first detection run, reading a YAML file. That is far too much work
 * to repeat per keystroke, so the answer is kept here until something happens that could change it: a facet
 * appearing or disappearing, or a fresh scan of the project.
 *
 * @param project the project the cached answers belong to
 */
@Service(Service.Level.PROJECT)
class MkDocsMaterialSchemaCache(private val project: Project) {

    companion object {

        /**
         * Drops the cached answers of [project] and makes the IDE ask for the schema again.
         *
         * The convenient entry point for the callers that change the answer — a project on its way out is
         * ignored rather than reported, because a disposed project has nothing left to invalidate.
         *
         * @param project the project whose configuration files may have changed their theme
         */
        @JvmStatic
        fun invalidate(project: Project) {
            if (project.isDisposed) return
            project.service<MkDocsMaterialSchemaCache>().invalidate()
        }
    }

    /** The answers given so far, keyed by the configuration file they were given for. */
    private val answers = ConcurrentHashMap<VirtualFile, Boolean>()

    /**
     * Returns the cached answer for [file], computing it with [compute] on first ask.
     *
     * @param file the configuration file the schema is looked up for
     * @param compute the answer, evaluated only when nothing is cached yet
     */
    fun get(file: VirtualFile, compute: (VirtualFile) -> Boolean): Boolean =
        answers.computeIfAbsent(file) { compute(it) }

    /**
     * Drops every cached answer and makes the platform rebuild its schema assignments.
     *
     * Clearing the map alone would change nothing the user can see: the schema a file is validated against is
     * resolved once and then held by the platform, so it has to be told that the assignment is stale.
     */
    fun invalidate() {
        answers.clear()
        if (project.isDisposed) return
        JsonSchemaService.Impl.get(project).reset()
    }
}

/**
 * Maps the MkDocs configuration file of a *Material for MkDocs* site to the refined schema.
 *
 * The base MkDocs schema describes `theme` as a mapping with four keys and `extra` as a mapping with none —
 * which is correct for MkDocs and useless for a site rendered with the Material theme, where those two blocks
 * carry most of the configuration. This provider hands out the refined schema instead, but only where it
 * applies: a site on another theme has to keep the plain MkDocs schema, otherwise the IDE would offer keys the
 * theme rendering the site does not read.
 *
 * Both spellings MkDocs accepts are covered. `mkdocs.yml` is mapped by the bundled catalogue as well, and this
 * is deliberate. The platform keeps every mapping that claims a file rather than picking one, so the file ends
 * up with both — with this provider first, because providers contributed by a factory are consulted before the
 * catalogue. That order is what the refinement needs, and the catalogue entry alongside it is harmless: it
 * declares no `additionalProperties: false`, so it cannot flag the keys the Material theme adds.
 *
 * @param project the project the configuration files belong to
 */
class MkDocsMaterialSchemaFileProvider(private val project: Project) : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean {
        if (project.isDisposed) return false
        if (!MkDocsProject.isConfigFile(file.name)) return false
        return project.service<MkDocsMaterialSchemaCache>().get(file) { candidate -> isMaterialSite(candidate) }
    }

    override fun getName(): String = MkDocsMaterialBundle.message("schema.material.name")

    override fun getSchemaFile(): VirtualFile = service<MkDocsMaterialSchemaGenerator>().schemaFile

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    /**
     * Decides whether [file] configures a site rendered with the Material theme.
     *
     * The module is the cheap answer and the authoritative one once the detection has run: the facet is what
     * the rest of the plugin treats as the truth about a site. Before the first run there is no module yet,
     * and a file opened right after the IDE started would silently get the plain schema — so in that one case
     * the theme is read from the file itself.
     *
     * @param file the configuration file the schema is looked up for
     */
    private fun isMaterialSite(file: VirtualFile): Boolean =
        runReadActionBlocking {
            if (project.isDisposed || !file.isValid) return@runReadActionBlocking false
            val module = ProjectFileIndex.getInstance(project).getModuleForFile(file)
            when {
                module == null -> MkDocsMaterialConfig.isMaterialTheme(project, file)
                module.isDisposed -> false
                else -> MkDocsMaterialFacet.getInstance(module) != null
            }
        }
}
