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

package org.pcsoft.ij.plugin.mkdocs.material.icon

import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * What was read out of an installation directory of *Material for MkDocs*, remembered per directory.
 *
 * The one cache of the feature, and the reason it exists is the size of what is read: the `RECORD` of this
 * distribution carries a line per shipped file, several thousand of them because of the icon sets. Everything
 * asking about the installation asks on the hot path — the completion popup on every keystroke, the inlay
 * hints and the annotator on every highlighting pass, the settings page while the user types a path — and
 * reading that file again for each of them is what made all of them slow.
 *
 * An installation does not change while the IDE runs, so nothing is re-checked: an entry stays until it is
 * thrown away on purpose. That happens where an installation can actually have become another one — the
 * settings page choosing a different directory, the action re-reading it by hand, and a write below the
 * package that the VFS happens to report.
 *
 * The one exception is [MkDocsMaterialInstallation.Problem.NO_DIRECTORY]: a path that is not a directory at
 * all is the answer given while a user is still typing one, and remembering it would outlive the typing.
 */
@Service(Service.Level.APP)
class MkDocsMaterialInstallationCache {

    /** What was read, per installation directory. */
    private val dataSetList = ConcurrentHashMap<String, MkDocsMaterialInstallation.DataSet>()

    /**
     * Returns what the installation directory [location] holds, reading it on first use.
     *
     * May be called from anywhere, a read action included: what it touches are files outside the project,
     * through [java.io.File] rather than through the VFS. It must not be called on the EDT while the entry of
     * that directory is still cold, because the first call reads the whole file listing.
     *
     * @param location the directory pip reports as its `Location`
     * @return what was found, never `null`
     */
    fun dataOf(location: String, reader: (String) -> MkDocsMaterialInstallation.DataSet): MkDocsMaterialInstallation.DataSet {
        val key = location.trim()
        dataSetList[key]?.let { return it }
        val reading = reader(key)
        if (reading.problem != MkDocsMaterialInstallation.Problem.NO_DIRECTORY) dataSetList[key] = reading
        return reading
    }

    /**
     * Throws away everything that was read, so the next question reads the directories again.
     *
     * Called wherever an installation can have become another one.
     */
    fun invalidate() {
        dataSetList.clear()
    }

    /**
     * Remembers [dataSet] as the content of [location], without reading anything.
     *
     * The seam every test uses: what lies in the `site-packages` of the machine running a build is not
     * something a test may depend on.
     *
     * @param location the directory the reading stands for
     * @param dataSet what that directory is to be taken for
     */
    fun remember(location: String, dataSet: MkDocsMaterialInstallation.DataSet) {
        dataSetList[location.trim()] = dataSet
    }
}
