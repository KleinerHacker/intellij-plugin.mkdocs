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

import com.intellij.util.messages.Topic

/**
 * Reports that something about the MkDocs sites of a project changed.
 *
 * The platform's own facet and module events do not cover what the tool window needs: adding a page to `nav`
 * changes neither a facet nor a module, and renaming the heading of a page changes nothing in the model at
 * all. [MkDocsModuleService] is the one place that sees all of it, so it is the one place that reports it.
 *
 * Listeners are called on whichever thread caused the change, which is usually a pooled thread. Anything
 * touching Swing has to hand the work to the event dispatch thread itself.
 */
interface MkDocsSitesListener {

    /**
     * The sites of the project were re-detected.
     *
     * Sent after every scan, whether or not the set of sites changed — a scan also runs when the content of a
     * configuration file changed, and that content is what the navigation is read from.
     */
    fun sitesChanged() {}

    /**
     * A page of a site was created, deleted, renamed or written.
     *
     * The set of sites is unaffected; only what the pages are called can have changed.
     */
    fun pagesChanged() {}

    companion object {

        /** The topic [MkDocsModuleService] and the VFS listener report on. */
        val TOPIC: Topic<MkDocsSitesListener> = Topic.create("MkDocs sites", MkDocsSitesListener::class.java)
    }
}
