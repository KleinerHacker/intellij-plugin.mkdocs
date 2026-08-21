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

package org.pcsoft.ij.plugin.mkdocs.material

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * What was read out of an installation directory of *Material for MkDocs*, remembered per directory.
 *
 * The one cache of the feature, and the reason it exists is the size of what is read: the `RECORD` of this
 * distribution carries a line per shipped file, several thousand of them because of the icon sets. Everything
 * asking about the installation asks on the hot path — the completion popup on every keystroke, the inlay
 * hints and the annotator on every highlighting pass, the settings page while the user types a path — and
 * reading that file again for each of them is what made all of them slow.
 *
 * Everything that follows from an installation is kept here, and only what follows from one:
 *
 * * [dataOf] — what its `RECORD` and `METADATA` say: whether it is one at all, and the names it ships;
 * * [fileOf] — the SVG file behind an icon name;
 * * [iconOf] — that file as something the IDE can paint.
 *
 * One cache with one lifetime rather than one per question: the three answers become stale at exactly the
 * same moment, and a caller having to remember which of them to throw away is how a stale one survives. What
 * does *not* belong here is anything of the UI — a lookup element carries a renderer, and with it the project
 * it was built for, which an application service must not outlive.
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

    /** The SVG file per icon, keyed by the URL of the sets it lies in and the name addressing it. */
    private val fileList = ConcurrentHashMap<String, VirtualFile>()

    /** The painted icons, keyed by the URL of the file they read and the size they carry. */
    private val iconList = ConcurrentHashMap<String, Icon>()

    /**
     * Returns what the installation directory [location] holds, reading it on first use.
     *
     * May be called from anywhere, a read action included: what it touches are files outside the project,
     * through [java.io.File] rather than through the VFS. It must not be called on the EDT while the entry of
     * that directory is still cold, because the first call reads the whole file listing.
     *
     * @param location the directory pip reports as its `Location`
     * @param reader what reads a directory that is not remembered yet
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
     * Returns the SVG file of the icon [name] below the sets at [root], resolving it on first use.
     *
     * Only a hit is remembered: a name the installation does not carry is the rare case, while every name
     * that is painted is asked for again on the next repaint.
     *
     * @param root the directory holding the icon sets
     * @param name the name of the icon, as the theme addresses it
     * @param resolve what looks the name up below [root] when it is not remembered yet
     */
    fun fileOf(root: VirtualFile, name: String, resolve: () -> VirtualFile?): VirtualFile? {
        val key = "${root.url}/$name"
        fileList[key]?.takeIf { it.isValid }?.let { return it }
        val file = resolve()
        if (file != null) fileList[key] = file
        return file
    }

    /**
     * Returns [file] as an icon of [size] pixels, building it on first use.
     *
     * @param file the SVG file of the icon
     * @param size the edge length in pixels the icon is painted at
     * @param render what turns the file into an icon when it is not remembered yet
     */
    fun iconOf(file: VirtualFile, size: Int, render: () -> Icon): Icon {
        val key = "${file.url}@$size"
        iconList[key]?.let { return it }
        val icon = render()
        return iconList.putIfAbsent(key, icon) ?: icon
    }

    /**
     * Throws away everything that was read, so the next question reads the directories again.
     *
     * Called wherever an installation can have become another one.
     */
    fun invalidate() {
        dataSetList.clear()
        fileList.clear()
        iconList.clear()
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
