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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.pcsoft.ij.plugin.mkdocs.MkDocsBundle

/**
 * Gives `mkdocs.yaml` the same JSON schema `mkdocs.yml` already gets.
 *
 * MkDocs accepts both spellings of its configuration file, but the SchemaStore catalogue the IDE reads its
 * schema mappings from lists only `mkdocs.yml` — so the `.yaml` spelling ends up without completion and
 * without validation, while the two files are the same thing to MkDocs. This provider closes that gap and
 * deliberately covers nothing else: `mkdocs.yml` is left to the catalogue, because two mappings for the same
 * file would only compete with each other.
 */
class MkDocsSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(MkDocsSchemaFileProvider())
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
