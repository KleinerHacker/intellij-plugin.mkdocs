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

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.pcsoft.ij.plugin.mkdocs.material.config.MkDocsMaterialIconSettings
import org.pcsoft.ij.plugin.mkdocs.utils.MkDocsPipService
import java.io.File

/**
 * An installed *Material for MkDocs*, for the tests of this feature.
 *
 * Where the theme is installed is asked of pip, so a test cannot write an installation into the project of
 * its fixture any more — and it must not be at the mercy of the machine it runs on either, where a real
 * `mkdocs-material` would answer for the icons a test wrote itself. So both are pinned here: pip is told the
 * distribution is not installed, and the icons of the test become a real directory the settings point at.
 *
 * What is written is a whole installation, not just the icon files: a `*.dist-info` beside the package, with
 * the `METADATA` naming the distribution and the `RECORD` listing every file. That is what the plugin checks
 * a directory against and what it reads the icon names out of, so a fixture writing only the SVG files would
 * be a directory the plugin rightly refuses.
 */
internal object MkDocsMaterialInstallationFixture {

    /** The drawing every installed icon is written with. */
    private const val SVG =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg>"""

    /** The version the fixture installs; nothing reads it, it only has to look like one. */
    private const val VERSION = "9.9.9"

    /** The path of the icon sets inside the installation. */
    private const val ICONS = "material/templates/.icons"

    /**
     * Installs [icons] for [project] and returns the directory holding the icon sets.
     *
     * @param project the project whose settings are pointed at the installation
     * @param icons the icon files, as the theme addresses them plus the extension, e.g. `material/check.svg`
     */
    fun install(project: Project, icons: List<String>): VirtualFile {
        val location = write(icons)
        point(project, location.path)
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(location, ICONS))
            ?: error("cannot reach the icon sets below ${location.path}")
    }

    /**
     * Writes an installation holding [icons] and returns the directory it was installed into.
     *
     * The directory of a real installation: the package, and the `*.dist-info` pip writes beside it. Plain
     * file system work, without the VFS — a developer test running without a platform application uses this
     * to build an installation the checks can be run against.
     *
     * @param icons the icon files, as the theme addresses them plus the extension
     */
    fun write(icons: List<String>): File {
        val location = FileUtil.createTempDirectory("mkdocs-site-packages", null, true)
        icons.forEach { icon ->
            val file = File(location, "$ICONS/$icon")
            file.parentFile.mkdirs()
            file.writeText(SVG)
        }
        val distInfo = File(location, "mkdocs_material-$VERSION.dist-info")
        distInfo.mkdirs()
        File(distInfo, "METADATA").writeText(
            "Metadata-Version: 2.4\nName: mkdocs-material\nVersion: $VERSION\n",
        )
        File(distInfo, "RECORD").writeText(record(icons))
        return location
    }

    /**
     * Takes the installation away from [project], which is the state of a fresh checkout.
     *
     * @param project the project without an installation
     */
    fun uninstall(project: Project) = point(project, "")

    /**
     * Points [project] at [path] and drops what was read out of the installation before.
     *
     * @param project the project being pointed
     * @param path the installation directory, empty for no installation at all
     */
    fun point(project: Project, path: String) {
        service<MkDocsPipService>().overrideLocation(MkDocsMaterialIconLocator.DISTRIBUTION, "")
        MkDocsMaterialIconSettings.getInstance(project).iconPath = path
        MkDocsMaterialIconIndex.getInstance(project).invalidate()
    }

    /**
     * Returns the `RECORD` of an installation holding [icons], in the shape pip writes it.
     *
     * @param icons the icon files, as the theme addresses them plus the extension
     */
    private fun record(icons: List<String>): String =
        (listOf("mkdocs_material-$VERSION.dist-info/METADATA") + icons.map { "$ICONS/$it" })
            .joinToString("\n") { "$it,sha256=0000000000000000000000000000000000000000000,1" }
}
