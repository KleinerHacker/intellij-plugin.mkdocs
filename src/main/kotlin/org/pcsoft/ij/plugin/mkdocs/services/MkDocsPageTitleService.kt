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

package org.pcsoft.ij.plugin.mkdocs.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.pcsoft.ij.plugin.mkdocs.types.MkDocsPageTitle

/**
 * Answers what a Markdown page is called, and remembers the answer.
 *
 * The navigation tree asks this for every visible node, and it asks again after every change to the site, so
 * reading the file each time would be wasteful. The answers are therefore cached against the modification
 * stamp of what they were read from, which makes a stale title impossible without a change being noticed.
 *
 * Nothing here touches PSI: the file is read as text, either out of the document an editor already holds or
 * straight out of the virtual file system. That keeps the whole lookup free of a read action, so the tree can
 * build its nodes on a background thread without taking a lock. Reading from disk is blocking, so this must
 * not be called on the event dispatch thread.
 */
@Service(Service.Level.PROJECT)
class MkDocsPageTitleService {

    /**
     * What a cached title was read from.
     *
     * Documents and virtual files count their modifications separately, so the source belongs into the key
     * as well — otherwise the two counters could agree by accident and hand out a title of the wrong text.
     *
     * @property stamp the modification stamp the title was read at
     * @property fromDocument whether [stamp] counts document or virtual file modifications
     * @property title the title read at that point
     */
    private data class Entry(val stamp: Long, val fromDocument: Boolean, val title: String)

    /**
     * The cached titles.
     *
     * Weak keys, so a file deleted or forgotten by the virtual file system does not stay alive because its
     * title was once read.
     */
    private val cache = ContainerUtil.createConcurrentWeakKeySoftValueMap<VirtualFile, Entry>()

    /**
     * Returns the title [file] presents itself with.
     *
     * The first heading of the page, or the name of the file if it has none. An editor holding unsaved
     * changes wins over what is on disk, so renaming a heading is reflected in the tree without saving first.
     *
     * @param file the Markdown page to read
     * @return the title, never blank unless the file name is
     */
    fun titleOf(file: VirtualFile): String {
        if (!file.isValid || file.isDirectory) return MkDocsPageTitle.fallback(file.name)

        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        val fromDocument = document != null
        val stamp = document?.modificationStamp ?: file.modificationStamp

        val cached = cache[file]
        if (cached != null && cached.stamp == stamp && cached.fromDocument == fromDocument) {
            return cached.title
        }

        val text = when {
            document != null -> document.immutableCharSequence
            file.length > MAX_FILE_LENGTH -> null
            else -> runCatching { VfsUtilCore.loadText(file) }.getOrNull()
        }
        val title = text?.let { MkDocsPageTitle.extract(it) } ?: MkDocsPageTitle.fallback(file.name)

        cache[file] = Entry(stamp, fromDocument, title)
        return title
    }

    /**
     * Forgets every cached title.
     *
     * Not needed in normal operation — a changed file changes its modification stamp and is re-read by
     * itself. It exists for tests, which have to be able to tell a cache hit from a fresh read.
     */
    fun clear() {
        cache.clear()
    }

    companion object {

        /**
         * How large a file may be before it is not read at all.
         *
         * A Markdown page of a megabyte is generated output, not something a person navigates by heading.
         * Reading it would cost more than the title is worth.
         */
        const val MAX_FILE_LENGTH: Long = 1_000_000
    }
}
