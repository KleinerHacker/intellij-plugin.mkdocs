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

package org.pcsoft.ij.plugin.mkdocs.schema

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Contributes the schema mappings of the plugin to a project.
 *
 * Two of them, and they answer two different questions. [MkDocsMaterialSchemaFileProvider] knows about the
 * project: it hands the refined schema to the configuration files of sites rendered with the Material theme,
 * whichever of the two spellings they use. [MkDocsSchemaFileProvider] knows nothing about the project and
 * closes a gap in the bundled catalogue for every other site. The Material provider comes first, so a file it
 * claims is not answered by the plain mapping as well.
 *
 * [DumbAware], and that is not a detail: a factory the platform cannot use during indexing is not asked right
 * away but on a pooled thread once the project is smart again, and until that pass has run the file is mapped
 * by whatever the bundled catalogue says. A site would then be edited against the plain MkDocs schema for the
 * first seconds after every start. Nothing here needs an index — the mapping is decided by the module of the
 * file and by its facets.
 */
class MkDocsSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> =
        listOf(MkDocsMaterialSchemaFileProvider(project), MkDocsSchemaFileProvider())
}

/**
 * The mapping itself: the MkDocs schema for every file named `mkdocs.yaml`.
 *
 * No schema file is shipped with the plugin. The schema is named as a remote source instead, which is what
 * the platform uses for every catalogue entry as well — it is downloaded once and cached, and it stays in
 * step with the schema `mkdocs.yml` is validated against instead of ageing inside the plugin.
 */
class MkDocsSchemaFileProvider : JsonSchemaFileProvider {

    companion object {

        /** The file name the catalogue does not cover. */
        const val FILE_NAME: String = "mkdocs.yaml"

        /** The same schema the SchemaStore catalogue maps `mkdocs.yml` to. */
        const val SCHEMA_URL: String = "https://www.schemastore.org/mkdocs-1.6.json"
    }

    override fun isAvailable(file: VirtualFile): Boolean = file.name.equals(FILE_NAME, ignoreCase = true)

    override fun getName(): String = MkDocsBundle.message("schema.name")

    override fun getSchemaFile(): VirtualFile? = null

    override fun getSchemaType(): SchemaType = SchemaType.remoteSchema

    override fun getRemoteSource(): String = SCHEMA_URL
}
