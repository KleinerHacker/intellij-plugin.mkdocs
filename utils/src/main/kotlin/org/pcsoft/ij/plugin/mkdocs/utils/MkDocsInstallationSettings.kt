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

package org.pcsoft.ij.plugin.mkdocs.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * The installation directory a user configured by hand, per feature of an MkDocs site.
 *
 * [MkDocsInstallationLocator] asks pip, and pip answers for the interpreter it finds on the `PATH`. That is
 * the right answer for most setups and for none of the others: an interpreter somewhere else, a system wide
 * installation, a container mount. This is where the answer of the user is kept for those.
 *
 * One state for every feature rather than one per facet, keyed by a name the facet picks for itself — the
 * next facet then needs no persistence of its own, and a user reads one settings file instead of three.
 *
 * Kept per project rather than per IDE: the path points into the environment of *this* project, and a second
 * project usually has one of its own.
 */
@Service(Service.Level.PROJECT)
@State(name = "MkDocsInstallations", storages = [Storage("mkdocs.xml")])
class MkDocsInstallationSettings : PersistentStateComponent<MkDocsInstallationSettings> {

    /**
     * The configured directory per feature key; a key that is absent means "ask pip".
     *
     * Public and mutable because the serialisation of the platform writes it back this way.
     */
    var paths: MutableMap<String, String> = mutableMapOf()

    /**
     * Returns the directory configured for [key], or an empty string if there is none.
     *
     * @param key the name the facet keeps its path under, for example `material`
     */
    fun pathOf(key: String): String = paths[key]?.trim().orEmpty()

    /**
     * Stores [path] as the directory of [key], or forgets it if [path] is blank.
     *
     * A blank path is not an instruction to find nothing — it is the way back to the automatic answer, which
     * is why it drops the entry rather than writing an empty one.
     *
     * @param key the name the facet keeps its path under
     * @param path the directory the user chose, or a blank string for the automatic answer
     */
    fun setPath(key: String, path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) paths.remove(key) else paths[key] = trimmed
    }

    override fun getState(): MkDocsInstallationSettings = this

    override fun loadState(state: MkDocsInstallationSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
