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

package org.pcsoft.ij.plugin.mkdocs

/**
 * Marks the window in which the MkDocs detection is writing facets itself.
 *
 * A facet the detection adds or removes says nothing new: it was derived from the configuration file in the
 * first place. Writing it back would be worse than pointless, because the two decisions are taken at
 * different moments — the detection may read a file the IDE has not parsed yet, decide a feature is not in
 * use and drop its facet, and a listener acting on that removal would then take the very key out of the file
 * that declares it. Only a change made in the Project Structure dialog carries new information, and only that
 * one may reach the file.
 *
 * Every facet listener that writes back to the configuration file therefore has to ask [isRunning] first.
 * That includes the listeners of the site features, which is why the flag lives here rather than on the
 * detection service: a feature must be able to ask without seeing the plugin it extends.
 *
 * The flag is per thread. It is read from inside a facet event, which the platform fires while the model is
 * being committed — on the same thread that made the change.
 */
object MkDocsFacetSync {

    /** Nesting depth of the facet changes the detection is applying right now, see [isRunning]. */
    private val depth = ThreadLocal.withInitial { 0 }

    /**
     * Returns `true` if the facet change currently being announced comes from the MkDocs detection.
     *
     * Must be called on the thread the facet event is fired on.
     */
    @JvmStatic
    fun isRunning(): Boolean = depth.get() > 0

    /**
     * Runs [change] with [isRunning] set, so the facet events it causes are recognised as the detection's own
     * work.
     *
     * @param change the facet model change to run
     * @return whatever [change] returns
     */
    fun <T> running(change: () -> T): T {
        depth.set(depth.get() + 1)
        try {
            return change()
        } finally {
            depth.set(depth.get() - 1)
        }
    }
}
